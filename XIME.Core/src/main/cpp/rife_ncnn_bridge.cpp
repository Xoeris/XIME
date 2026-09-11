// rife_ncnn_bridge.cpp
//
// JNI bridge for xime.media.lucine.RifeFrameInterpolator. Loads a RIFE-style
// ncnn model (<name>.param / <name>.bin, as shipped by
// github.com/nihui/rife-ncnn-vulkan) straight out of the APK's assets and
// runs the two-frame + timestep forward pass on the Vulkan compute backend,
// falling back to ncnn's CPU backend if no Vulkan device is usable.
//
// Build against: ncnn (with NCNN_VULKAN=ON), vulkan headers/loader, and the
// Android NDK's jnigraphics + android (AAssetManager) libraries. Link:
//   ncnn, vulkan, android, jnigraphics, log
//
// This file intentionally does NOT try to reproduce RIFE's exact network
// topology (IFNet's flow/context encoders, warping, fusion, etc.) -- that
// graph lives entirely in the .param file and is instantiated generically
// via ncnn::Net::load_param/load_model. This bridge only handles: model
// loading off Vulkan/CPU, pixel <-> ncnn::Mat conversion via the Android
// Bitmap NDK, padding to a multiple of 32 for RIFE's flow pyramid, and
// running the extracted output blob back into the caller's output Bitmap.

#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <memory>
#include <mutex>

#include "net.h"
#include "mat.h"
#include "gpu.h"

#define LOG_TAG "RifeNcnnBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// RIFE's flow pyramid downsamples several times internally; inputs must be
// a multiple of this or the network's internal concat/add ops mismatch shape.
static const int PAD_ALIGN = 32;

namespace {

// Tracks how many RifeHandle instances currently have a Vulkan backend
// selected, so we only call ncnn::create_gpu_instance()/destroy_gpu_instance()
// once each, globally, no matter how many models are loaded/unloaded.
    std::mutex g_vulkanInitMutex;
    int g_vulkanRefCount = 0;

    bool acquireVulkanInstance() {
        std::lock_guard<std::mutex> lock(g_vulkanInitMutex);
        if (g_vulkanRefCount == 0) {
            int ret = ncnn::create_gpu_instance();
            if (ret != 0) {
                LOGE("ncnn::create_gpu_instance() failed (ret=%d) -- no usable Vulkan device", ret);
                return false;
            }
        }
        g_vulkanRefCount++;
        return true;
    }

    void releaseVulkanInstance() {
        std::lock_guard<std::mutex> lock(g_vulkanInitMutex);
        if (g_vulkanRefCount > 0) {
            g_vulkanRefCount--;
            if (g_vulkanRefCount == 0) {
                ncnn::destroy_gpu_instance();
            }
        }
    }

    struct RifeHandle {
        ncnn::Net net;
        bool usingVulkan = false;
        int activeGpuId = -1;

        // RIFE ncnn exports typically name the two frame inputs "input0"/"input1"
        // and the fused timestep either as a scalar broadcast input "input2" (the
        // common practical-rife-style export) or bake t=0.5 into the graph and
        // expose no third input at all. We check input_names() at load time so
        // this bridge works with either export.
        bool hasTimestepInput = false;
        std::string input0Name;
        std::string input1Name;
        std::string timestepInputName;
        std::string outputName;

        ~RifeHandle() {
            net.clear();
            if (usingVulkan) {
                releaseVulkanInstance();
            }
        }
    };

// Reads an asset fully into a heap buffer. ncnn's Net::load_param/load_model
// also accept an AAssetManager* + path directly, which is what we use below
// instead of this -- kept only as a fallback note for non-Android callers of
// the same graph-loading logic, harmless to leave unused-but-available.
    bool resolveInOutNames(RifeHandle *handle) {
        const std::vector<int> &inputIndexes = handle->net.input_indexes();
        const std::vector<int> &outputIndexes = handle->net.output_indexes();
        const std::vector<const char *> &inputNames = handle->net.input_names();
        const std::vector<const char *> &outputNames = handle->net.output_names();

        if (inputIndexes.size() < 2 || outputIndexes.empty()) {
            LOGE("Unexpected ncnn graph: %zu inputs, %zu outputs (need >=2 inputs, 1 output)",
                 inputIndexes.size(), outputIndexes.size());
            return false;
        }

        handle->input0Name = inputNames[0];
        handle->input1Name = inputNames[1];
        handle->outputName = outputNames[0];

        if (inputIndexes.size() >= 3) {
            handle->hasTimestepInput = true;
            handle->timestepInputName = inputNames[2];
        } else {
            handle->hasTimestepInput = false;
        }

        LOGD("ncnn RIFE graph: in0=%s in1=%s timestepInput=%s out=%s",
             handle->input0Name.c_str(), handle->input1Name.c_str(),
             handle->hasTimestepInput ? handle->timestepInputName.c_str() : "(none, t baked in)",
             handle->outputName.c_str());
        return true;
    }

// Locks an ARGB_8888 bitmap and wraps its pixels as an ncnn::Mat (RGB, no
// alpha channel -- RIFE models are trained on RGB), padded on the right/
// bottom to a multiple of PAD_ALIGN so the flow pyramid divides evenly.
// outPadW/outPadH receive the padded size actually returned.
    bool bitmapToPaddedMat(JNIEnv *env, jobject bitmap, ncnn::Mat &outMat,
                           int &outOrigW, int &outOrigH, int &outPadW, int &outPadH) {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("AndroidBitmap_getInfo failed");
            return false;
        }
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
            LOGE("Expected ARGB_8888 bitmap, got format=%d", info.format);
            return false;
        }

        void *pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("AndroidBitmap_lockPixels failed");
            return false;
        }

        outOrigW = (int) info.width;
        outOrigH = (int) info.height;
        outPadW = ((outOrigW + PAD_ALIGN - 1) / PAD_ALIGN) * PAD_ALIGN;
        outPadH = ((outOrigH + PAD_ALIGN - 1) / PAD_ALIGN) * PAD_ALIGN;

        // from_pixels_resize handles the RGBA->RGB channel drop and stride; we
        // then pad into the aligned canvas by replicating the edge, which RIFE's
        // training augmentation is tolerant of (far better than zero-padding,
        // which would smear a black edge into the flow estimate).
        ncnn::Mat unpadded = ncnn::Mat::from_pixels((const unsigned char *) pixels,
                                                    ncnn::Mat::PIXEL_RGBA2RGB,
                                                    outOrigW, outOrigH, (int) info.stride);

        AndroidBitmap_unlockPixels(env, bitmap);

        if (unpadded.empty()) {
            LOGE("ncnn::Mat::from_pixels failed");
            return false;
        }

        if (outPadW == outOrigW && outPadH == outOrigH) {
            outMat = unpadded;
            return true;
        }

        ncnn::copy_make_border(unpadded, outMat, 0, outPadH - outOrigH, 0, outPadW - outOrigW,
                               ncnn::BORDER_REPLICATE, 0.f);
        return !outMat.empty();
    }

// Writes an ncnn::Mat (RGB, 0..255 float or already uint8 depending on export)
// cropped to origW x origH, resized to outW x outH, into an already-allocated
// ARGB_8888 output Bitmap.
    bool matToBitmap(JNIEnv *env, const ncnn::Mat &mat, int origW, int origH, jobject outBitmap) {
        ncnn::Mat cropped;
        if (mat.w != origW || mat.h != origH) {
            ncnn::copy_cut_border(mat, cropped, 0, mat.h - origH, 0, mat.w - origW);
        } else {
            cropped = mat;
        }
        if (cropped.empty()) {
            LOGE("crop to original size failed (mat %dx%d -> %dx%d)", mat.w, mat.h, origW, origH);
            return false;
        }

        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, outBitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("AndroidBitmap_getInfo (output) failed");
            return false;
        }

        void *pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, outBitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("AndroidBitmap_lockPixels (output) failed");
            return false;
        }

        // to_pixels writes RGB straight into a tightly-packed buffer at the
        // Mat's own resolution; if the caller wants a different output size than
        // the source frames we resize first via ncnn's bilinear resize, then
        // convert, so we only touch the Bitmap's real pixel buffer once.
        bool ok = true;
        if ((int) info.width != cropped.w || (int) info.height != cropped.h) {
            ncnn::Mat resized;
            ncnn::resize_bilinear(cropped, resized, (int) info.width, (int) info.height);
            if (resized.empty()) {
                LOGE("resize to output size failed");
                ok = false;
            } else {
                resized.to_pixels((unsigned char *) pixels, ncnn::Mat::PIXEL_RGB2RGBA);
            }
        } else {
            cropped.to_pixels((unsigned char *) pixels, ncnn::Mat::PIXEL_RGB2RGBA);
        }

        // to_pixels above wrote RGB into an RGBA layout with alpha left as
        // whatever garbage followed each pixel triple; stamp full alpha in a
        // second pass so the returned Bitmap composites correctly.
        if (ok) {
            auto *argb = (unsigned char *) pixels;
            size_t count = (size_t) info.width * info.height;
            for (size_t i = 0; i < count; i++) {
                argb[i * 4 + 3] = 0xFF;
            }
        }

        AndroidBitmap_unlockPixels(env, outBitmap);
        return ok;
    }

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_xime_media_lucine_RifeFrameInterpolator_nativeCreate(
        JNIEnv *env, jclass /*clazz*/,
        jobject assetManager, jstring paramAssetPath, jstring binAssetPath,
        jint requestedGpuId, jint numThreads) {

    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("AAssetManager_fromJava returned null");
        return 0;
    }

    const char *paramPath = env->GetStringUTFChars(paramAssetPath, nullptr);
    const char *binPath = env->GetStringUTFChars(binAssetPath, nullptr);

    auto handle = std::make_unique<RifeHandle>();

    bool wantVulkan = requestedGpuId != -2;
    bool vulkanReady = false;
    if (wantVulkan) {
        vulkanReady = acquireVulkanInstance();
        if (!vulkanReady) {
            LOGD("No Vulkan device available, falling back to CPU");
        }
    }

    if (vulkanReady) {
        int gpuCount = ncnn::get_gpu_count();
        int useGpuId = requestedGpuId;
        if (useGpuId < 0 || useGpuId >= gpuCount) {
            useGpuId = ncnn::get_default_gpu_index();
        }
        handle->net.opt.use_vulkan_compute = true;
        handle->net.set_vulkan_device(useGpuId);
        handle->usingVulkan = true;
        handle->activeGpuId = useGpuId;
    } else {
        handle->net.opt.use_vulkan_compute = false;
        handle->usingVulkan = false;
        handle->activeGpuId = -1;
    }

    handle->net.opt.num_threads = std::max(1, (int) numThreads);
    handle->net.opt.use_fp16_packed = handle->usingVulkan;
    handle->net.opt.use_fp16_storage = handle->usingVulkan;
    handle->net.opt.use_fp16_arithmetic = handle->usingVulkan;

    int rp = handle->net.load_param(mgr, paramPath);
    int rb = rp == 0 ? handle->net.load_model(mgr, binPath) : -1;

    env->ReleaseStringUTFChars(paramAssetPath, paramPath);
    env->ReleaseStringUTFChars(binAssetPath, binPath);

    if (rp != 0 || rb != 0) {
        LOGE("Failed to load ncnn model (load_param=%d load_model=%d)", rp, rb);
        if (handle->usingVulkan) releaseVulkanInstance();
        return 0;
    }

    if (!resolveInOutNames(handle.get())) {
        if (handle->usingVulkan) releaseVulkanInstance();
        return 0;
    }

    return reinterpret_cast<jlong>(handle.release());
}

JNIEXPORT void JNICALL
Java_xime_media_lucine_RifeFrameInterpolator_nativeDestroy(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong handlePtr) {
if (handlePtr == 0) return;
delete reinterpret_cast<RifeHandle *>(handlePtr);
}

JNIEXPORT jboolean JNICALL
        Java_xime_media_lucine_RifeFrameInterpolator_nativeIsUsingVulkan(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong handlePtr) {
if (handlePtr == 0) return JNI_FALSE;
return reinterpret_cast<RifeHandle *>(handlePtr)->usingVulkan ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
        Java_xime_media_lucine_RifeFrameInterpolator_nativeGetActiveGpuId(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong handlePtr) {
if (handlePtr == 0) return -1;
return reinterpret_cast<RifeHandle *>(handlePtr)->activeGpuId;
}

JNIEXPORT jboolean JNICALL
        Java_xime_media_lucine_RifeFrameInterpolator_nativeInterpolate(
        JNIEnv *env, jclass /*clazz*/, jlong handlePtr,
jobject frame0, jobject frame1, jfloat timestep, jobject output) {

if (handlePtr == 0) return JNI_FALSE;
auto *handle = reinterpret_cast<RifeHandle *>(handlePtr);

int origW = 0, origH = 0, padW = 0, padH = 0;
int origW1 = 0, origH1 = 0, padW1 = 0, padH1 = 0;
ncnn::Mat in0, in1;

if (!bitmapToPaddedMat(env, frame0, in0, origW, origH, padW, padH)) return JNI_FALSE;
if (!bitmapToPaddedMat(env, frame1, in1, origW1, origH1, padW1, padH1)) return JNI_FALSE;

if (origW != origW1 || origH != origH1) {
LOGE("frame0/frame1 size mismatch: %dx%d vs %dx%d", origW, origH, origW1, origH1);
return JNI_FALSE;
}

// RIFE models are trained on [0,1]-normalized RGB, not raw 0..255.
const float normVals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
in0.substract_mean_normalize(nullptr, normVals);
in1.substract_mean_normalize(nullptr, normVals);

ncnn::Extractor ex = handle->net.create_extractor();
ex.input(handle->input0Name.c_str(), in0);
ex.input(handle->input1Name.c_str(), in1);

if (handle->hasTimestepInput) {
ncnn::Mat timestepMat(padW, padH, 1);
timestepMat.fill(timestep);
ex.input(handle->timestepInputName.c_str(), timestepMat);
} else if (timestep != 0.5f) {
LOGD("Model has no timestep input (t=0.5 baked in); ignoring requested t=%.3f", timestep);
}

ncnn::Mat out;
int ret = ex.extract(handle->outputName.c_str(), out);
if (ret != 0 || out.empty()) {
LOGE("ncnn extract() failed, ret=%d", ret);
return JNI_FALSE;
}

// Model output is [0,1]-normalized RGB; scale back to 0..255 before
// converting to pixels.
const float denormVals[3] = {255.f, 255.f, 255.f};
out.substract_mean_normalize(nullptr, denormVals);

return matToBitmap(env, out, origW, origH, output) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"