package xime.ui.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;

/**
 * Wraps an ncnn-vulkan RIFE-style (two-frame + timestep) video frame
 * interpolation model. This is genuine learned frame interpolation, not a
 * geometric warp: the model is given two real frames and a timestep in
 * [0,1] and predicts the intermediate frame directly, including plausible
 * handling of occlusion/disocclusion that block matching cannot do.
 *
 * This class no longer runs the model through TFLite's Interpreter /
 * GpuDelegate. Instead it drives an ncnn::Net over the Vulkan compute
 * backend via a thin JNI bridge (native library "rife_ncnn_bridge",
 * implemented separately in C++ against the ncnn + ncnn-vulkan sources).
 *
 * You must supply the ncnn model yourself as a `<name>.param` /
 * `<name>.bin` pair -- e.g. the weights shipped with
 * github.com/nihui/rife-ncnn-vulkan (themselves converted from
 * github.com/hzwer/ECCV2022-RIFE). Place both files under assets/ (same
 * directory, same base name) and pass that base asset path to the
 * constructor -- do not add the extension.
 *
 * Unlike the old fixed-shape TFLite tensors, ncnn RIFE models accept
 * arbitrary input resolutions: the native side internally pads each frame
 * up to a multiple of 32px (standard for RIFE's flow pyramid), runs
 * inference, and crops the result back down before returning it. Callers
 * do not need to worry about the model's internal tiling/padding.
 *
 * Inference is NOT free -- do not call interpolate() from the draw thread.
 * Call it from a dedicated background thread (see RifeInterpolationWorker)
 * and hand the result back to the UI/draw path once ready.
 */
public class RifeFrameInterpolator implements Closeable {

    private static final String TAG = "RifeFrameInterpolator";

    static {
        System.loadLibrary("rife_ncnn_bridge");
    }

    /** Opaque pointer (reinterpret_cast to a native wrapper struct) owned by the JNI side. */
    private long nativeHandle;

    private final boolean usingVulkan;
    private final int gpuId;

    /**
     * @param context          Used to reach the APK's AssetManager. The native side reads the
     *                         .param/.bin pair straight out of assets via ncnn's built-in
     *                         AAssetManager support -- no copy to internal storage is made.
     * @param modelAssetBase   Asset path with no extension, e.g. "models/rife-v4.6" resolves to
     *                         "models/rife-v4.6.param" and "models/rife-v4.6.bin".
     * @param gpuId             Vulkan physical device index to run on, or -1 to let ncnn pick the
     *                         default/discrete GPU. Ignored if Vulkan is unavailable.
     * @param preferVulkan      If true, try the Vulkan compute backend first. If no Vulkan device
     *                         is present, or context creation fails, this class transparently
     *                         falls back to ncnn's CPU backend rather than throwing -- check
     *                         isUsingVulkan() afterward if you care which path was taken.
     * @param numThreads        CPU thread count used either for the CPU fallback path or for the
     *                         non-GPU parts of the graph when Vulkan is active. 4 is a reasonable
     *                         default on most phones.
     */
    public RifeFrameInterpolator(Context context, String modelAssetBase, int gpuId,
                                      boolean preferVulkan, int numThreads) throws IOException {
        AssetManager assetManager = context.getAssets();

        int requestedGpuId = preferVulkan ? gpuId : -2; // -2 tells the native side "CPU only"
        nativeHandle = nativeCreate(assetManager, modelAssetBase + ".param",
                modelAssetBase + ".bin", requestedGpuId, numThreads);

        if (nativeHandle == 0) {
            throw new IOException("Failed to load ncnn RIFE model from assets: " + modelAssetBase +
                    " (.param/.bin) -- see logcat tag " + TAG + " for the ncnn-side error");
        }

        this.usingVulkan = preferVulkan && nativeIsUsingVulkan(nativeHandle);
        this.gpuId = usingVulkan ? nativeGetActiveGpuId(nativeHandle) : -1;

        Log.d(TAG, "RIFE ncnn model loaded: " + modelAssetBase +
                " backend=" + (usingVulkan ? ("vulkan gpu#" + this.gpuId) : "cpu") +
                " threads=" + numThreads);
    }

    /** Convenience constructor: Vulkan preferred, default GPU, 4 CPU threads. */
    public RifeFrameInterpolator(Context context, String modelAssetBase) throws IOException {
        this(context, modelAssetBase, -1, true, 4);
    }

    public boolean isUsingVulkan() {
        return usingVulkan;
    }

    public int getActiveGpuId() {
        return gpuId;
    }

    /**
     * Runs the model on two frames and returns the predicted intermediate
     * frame at the given timestep, scaled to outWidth x outHeight.
     * Blocking / GPU-or-CPU heavy -- call from a background thread only.
     *
     * frame0/frame1 are read directly (ARGB_8888 assumed); the native side
     * locks their pixel buffers via the Android Bitmap NDK API, so no extra
     * Java-side copy is made before crossing into native code.
     */
    public synchronized Bitmap interpolate(Bitmap frame0, Bitmap frame1, float timestep,
                                           int outWidth, int outHeight) {
        checkNotClosed();

        Bitmap in0 = ensureArgb8888(frame0);
        Bitmap in1 = ensureArgb8888(frame1);

        Bitmap output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);

        boolean ok = nativeInterpolate(nativeHandle, in0, in1, timestep, output);

        if (in0 != frame0) in0.recycle();
        if (in1 != frame1) in1.recycle();

        if (!ok) {
            output.recycle();
            Log.w(TAG, "nativeInterpolate failed; returning null (caller should hold the last good frame)");
            return null;
        }
        return output;
    }

    private static Bitmap ensureArgb8888(Bitmap bmp) {
        if (bmp.getConfig() == Bitmap.Config.ARGB_8888) return bmp;
        return bmp.copy(Bitmap.Config.ARGB_8888, false);
    }

    private void checkNotClosed() {
        if (nativeHandle == 0) {
            throw new IllegalStateException("RifeFrameInterpolator used after close()");
        }
    }

    @Override
    public synchronized void close() {
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (nativeHandle != 0) {
                Log.w(TAG, "RifeFrameInterpolator leaked without close() -- freeing native ncnn::Net from finalizer");
                close();
            }
        } finally {
            super.finalize();
        }
    }

    // --- Native bridge (implemented in rife_ncnn_bridge.cpp against ncnn + ncnn-vulkan) ---

    /**
     * Loads the .param/.bin pair from the APK's assets and builds an ncnn::Net (plus a
     * VulkanDevice/Options if requestedGpuId != -2 and a Vulkan-capable device exists).
     * Returns a native handle, or 0 on failure (bad model files, JNI/asset errors, etc).
     * requestedGpuId: -1 = ncnn default GPU selection, -2 = force CPU backend, >=0 = explicit
     * Vulkan physical device index.
     */
    private static native long nativeCreate(AssetManager assetManager, String paramAssetPath,
                                            String binAssetPath, int requestedGpuId, int numThreads);

    /** Frees the ncnn::Net and any associated Vulkan device/pipeline cache/command pool. */
    private static native void nativeDestroy(long handle);

    /** True if this handle ended up on the Vulkan compute path rather than the CPU fallback. */
    private static native boolean nativeIsUsingVulkan(long handle);

    /** The Vulkan physical device index actually in use, or -1 if running on CPU. */
    private static native int nativeGetActiveGpuId(long handle);

    /**
     * Runs one interpolation pass. frame0/frame1 must be ARGB_8888 Bitmaps; output must already
     * be an allocated ARGB_8888 Bitmap of the desired output size (this class allocates it above
     * so ncnn can write straight into locked native pixel memory without an extra copy/resize
     * pass on the Java side). The native side internally pads both inputs up to a multiple of 32
     * for RIFE's flow pyramid, runs the two-frame + timestep forward pass, then crops/resizes
     * the result into `output`. Returns false on any native-side failure (e.g. OOM on the GPU
     * heap, a Vulkan device-lost error) -- callers should treat that as "skip this frame" rather
     * than crash.
     */
    private static native boolean nativeInterpolate(long handle, Bitmap frame0, Bitmap frame1,
                                                    float timestep, Bitmap output);
}
