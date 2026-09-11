/**
 * xime_tools_jni.cpp
 * XIME.Tools — JNI bridge layer
 *
 * All entry points are declared extern "C" so the JNI runtime can locate them
 * by their mangled-free symbol names (Java_xime_tools_<Class>_<method>), matching
 * the convention established by pty.c in XIME.Terminal.
 *
 * Error code table (XIME_ERR_* constants):
 *   0              — success
 *   -1 .. -99      — general native errors (I/O, ENOMEM, etc.)
 *   -100 .. -200   — corrupt/truncated image  → FilesystemCorruptException (Java)
 *   -300           — unsupported FS           → UnsupportedFsException (Java)
 *
 * These ranges must stay in sync with the Java constants in XimeTools.java:
 *   ERR_CORRUPT_MIN = -200
 *   ERR_CORRUPT_MAX = -100
 *   ERR_UNSUPPORTED = -300
 */

#include <jni.h>
#include <android/log.h>
#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <cstdint>
#include <string>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <utime.h>
#include <algorithm>
#include <vector>
#include <dirent.h>
#include <sys/vfs.h>

// ---------------------------------------------------------------------------
// erofs-utils public API
// ---------------------------------------------------------------------------
extern "C" {
#ifdef HAVE_CONFIG_H
#  include "config.h"
#endif
#include "erofs/erofs.h"
#include "erofs/config.h"
#include "erofs/dir.h"
#include "erofs/inode.h"
#include "erofs/io.h"
#include "erofs/compress.h"
#include "erofs/importer.h"
#include "liberofs_rebuild.h"
}

// ---------------------------------------------------------------------------
// e2fsprogs / libext2fs public API
// ---------------------------------------------------------------------------
extern "C" {
#include "ext2fs/ext2fs.h"
#include "ext2fs/ext2_fs.h"
}

// ---------------------------------------------------------------------------
// Logging
// ---------------------------------------------------------------------------
#define TAG "XIME-Tools"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Native error codes (must match XimeTools.java constants)
// ---------------------------------------------------------------------------
static constexpr int XIME_ERR_OK           =    0;
static constexpr int XIME_ERR_IO           =   -1;
static constexpr int XIME_ERR_NOMEM        =   -2;
static constexpr int XIME_ERR_CORRUPT_MIN  = -200;
static constexpr int XIME_ERR_CORRUPT      = -150;   // generic corrupt
static constexpr int XIME_ERR_CORRUPT_MAX  = -100;
static constexpr int XIME_ERR_UNSUPPORTED  = -300;

// ---------------------------------------------------------------------------
// FsType ordinals (must match FsType.java enum declaration order)
// ---------------------------------------------------------------------------
static constexpr jint FS_TYPE_EROFS   = 0;
static constexpr jint FS_TYPE_EXT4    = 1;
static constexpr jint FS_TYPE_UNKNOWN = 2;

// Superblock byte offsets (mirrors FsType.java constants)
static constexpr int  FS_SUPERBLOCK_OFFSET     = 1024;
static constexpr long FS_EROFS_MAGIC           = 0xE0F5E1E2L;
static constexpr int  FS_EXT4_MAGIC            = 0xEF53;
static constexpr int  EXT4_MAGIC_SB_OFFSET     = 56;

// ---------------------------------------------------------------------------
// Progress callback helpers
// ---------------------------------------------------------------------------

/**
 * Holds the JNI global references needed to invoke ProgressListener callbacks.
 * Must be allocated on the heap before the long operation, and freed after.
 */
struct ProgressCtx {
    JNIEnv  *env;
    jobject  listenerRef;   // GlobalRef to ProgressListener Java object
    jmethodID onProgress;
    jmethodID onComplete;
    jmethodID onError;

    // Convenience: call ProgressListener.onProgress(bytesProcessed, totalBytes)
    void reportProgress(long long bytesProcessed, long long totalBytes) const {
        if (listenerRef == nullptr) return;
        env->CallVoidMethod(listenerRef, onProgress, (jlong)bytesProcessed, (jlong)totalBytes);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    void reportComplete() const {
        if (listenerRef == nullptr) return;
        env->CallVoidMethod(listenerRef, onComplete);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    void reportError(const char *message) const {
        if (listenerRef == nullptr) return;
        jstring jmsg = env->NewStringUTF(message ? message : "unknown error");
        env->CallVoidMethod(listenerRef, onError, jmsg);
        if (jmsg) env->DeleteLocalRef(jmsg);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
};

/**
 * Creates a ProgressCtx from a Java ProgressListener object.
 * Returns nullptr if @p listener is null (progress callbacks disabled).
 *
 * Caller must call destroyProgressCtx() when done.
 */
static ProgressCtx *createProgressCtx(JNIEnv *env, jobject listener) {
    if (listener == nullptr) return nullptr;

    jclass cls = env->GetObjectClass(listener);
    if (cls == nullptr) return nullptr;

    jmethodID midProgress = env->GetMethodID(cls, "onProgress", "(JJ)V");
    jmethodID midComplete = env->GetMethodID(cls, "onComplete", "()V");
    jmethodID midError    = env->GetMethodID(cls, "onError",    "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);

    if (!midProgress || !midComplete || !midError) {
        LOGE("Could not find ProgressListener methods — check interface signature");
        return nullptr;
    }

    auto *ctx = new ProgressCtx();
    ctx->env          = env;
    ctx->listenerRef  = env->NewGlobalRef(listener);
    ctx->onProgress   = midProgress;
    ctx->onComplete   = midComplete;
    ctx->onError      = midError;
    return ctx;
}

static void destroyProgressCtx(JNIEnv *env, ProgressCtx *ctx) {
    if (ctx == nullptr) return;
    if (ctx->listenerRef) env->DeleteGlobalRef(ctx->listenerRef);
    delete ctx;
}

// ---------------------------------------------------------------------------
// Exception throwing helpers
// ---------------------------------------------------------------------------

/**
 * Throws the Java exception appropriate for @p nativeCode into @p env.
 * Does nothing if nativeCode is XIME_ERR_OK.
 */
static void throwXimeToolsException(JNIEnv *env, int nativeCode, const char *message) {
    if (nativeCode == XIME_ERR_OK) return;

    const char *className;
    if (nativeCode >= XIME_ERR_CORRUPT_MIN && nativeCode <= XIME_ERR_CORRUPT_MAX) {
        className = "xime/tools/exception/FilesystemCorruptException";
    } else if (nativeCode == XIME_ERR_UNSUPPORTED) {
        className = "xime/tools/exception/UnsupportedFsException";
    } else {
        className = "xime/tools/exception/XimeToolsException";
    }

    jclass exClass = env->FindClass(className);
    if (exClass == nullptr) {
        // Fallback: throw RuntimeException so we don't silently swallow the error
        exClass = env->FindClass("java/lang/RuntimeException");
    }
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message);
        env->DeleteLocalRef(exClass);
    }
}

// ---------------------------------------------------------------------------
// Raw byte readers for superblock magic detection
// ---------------------------------------------------------------------------
static uint32_t readUInt32LE(const uint8_t *buf, int offset) {
    return (uint32_t)buf[offset]
         | ((uint32_t)buf[offset + 1] << 8)
         | ((uint32_t)buf[offset + 2] << 16)
         | ((uint32_t)buf[offset + 3] << 24);
}

static uint16_t readUInt16LE(const uint8_t *buf, int offset) {
    return (uint16_t)buf[offset] | ((uint16_t)buf[offset + 1] << 8);
}

static inline int xime_ilog2(uint32_t n) {
    int res = 0;
    while (n >>= 1) res++;
    return res;
}

static uint64_t get_dir_size_recursive(const std::string &path) {
    uint64_t total = 0;
    DIR *d = opendir(path.c_str());
    if (!d) return 0;
    struct dirent *de;
    while ((de = readdir(d))) {
        if (strcmp(de->d_name, ".") == 0 || strcmp(de->d_name, "..") == 0) continue;
        std::string subPath = path + "/" + de->d_name;
        struct stat st;
        if (lstat(subPath.c_str(), &st) == 0) {
            if (S_ISDIR(st.st_mode)) total += get_dir_size_recursive(subPath);
            else if (S_ISREG(st.st_mode)) total += st.st_size;
        }
    }
    closedir(d);
    return total;
}

// ---------------------------------------------------------------------------
// Native engine state
// ---------------------------------------------------------------------------
struct ErofsExtractCtx {
    ProgressCtx *progress;
    std::string baseOutputDir;
    struct erofs_sb_info *sbi;
    uint64_t totalBytes;
    uint64_t processedBytes;
};

static int erofs_extract_inode(ErofsExtractCtx *eCtx, struct erofs_inode *inode, const std::string &destPath);

static int erofs_extract_readdir_cb(struct erofs_dir_context *ctx) {
    if (ctx->dot_dotdot) return 0;

    auto *eCtx = (ErofsExtractCtx *)ctx;
    struct erofs_inode inode = { .sbi = eCtx->sbi, .nid = ctx->de_nid };

    int err = erofs_read_inode_from_disk(&inode);
    if (err) return err;

    std::string destPath = eCtx->baseOutputDir + "/" + std::string(ctx->dname, ctx->de_namelen);
    return erofs_extract_inode(eCtx, &inode, destPath);
}

static int erofs_extract_inode(ErofsExtractCtx *eCtx, struct erofs_inode *inode, const std::string &destPath) {
    if (S_ISDIR(inode->i_mode)) {
        if (mkdir(destPath.c_str(), 0755) && errno != EEXIST) {
            return -errno;
        }

        ErofsExtractCtx subCtx = *eCtx;
        subCtx.baseOutputDir = destPath;

        struct erofs_dir_context dirCtx = {0};
        dirCtx.dir = inode;
        dirCtx.cb = erofs_extract_readdir_cb;
        // Pass our context through the dirCtx by casting (unsafe but common in C-to-C++ callbacks if careful)
        // Actually, we should use a wrapper struct that contains erofs_dir_context as first field.

        struct DirWrapper : erofs_dir_context {
            ErofsExtractCtx *eCtx;
        } wrapper;
        memset(&wrapper, 0, sizeof(wrapper));
        wrapper.dir = inode;
        wrapper.cb = [](struct erofs_dir_context *c) {
            return erofs_extract_readdir_cb((struct erofs_dir_context *)((DirWrapper*)c)->eCtx);
        };
        // Wait, the callback signature in dir.h is `int (*erofs_readdir_cb)(struct erofs_dir_context *)`.
        // I'll just use a static pointer for now if I don't want to overcomplicate, but that's not thread-safe.
        // Better: erofs_dir_context is designed to be wrapped.

        // RE-IMPLEMENTING CLEANLY:
        struct WalkerCtx {
            struct erofs_dir_context dirCtx;
            ErofsExtractCtx *eCtx;
        } walker;
        memset(&walker, 0, sizeof(walker));
        walker.dirCtx.dir = inode;
        walker.dirCtx.cb = [](struct erofs_dir_context *c) {
            auto *w = (WalkerCtx *)c;
            if (c->dot_dotdot) return 0;

            struct erofs_inode subInode = { .sbi = w->eCtx->sbi, .nid = c->de_nid };
            int err = erofs_read_inode_from_disk(&subInode);
            if (err) return err;

            std::string subDest = w->eCtx->baseOutputDir + "/" + std::string(c->dname, c->de_namelen);
            return erofs_extract_inode(w->eCtx, &subInode, subDest);
        };
        walker.eCtx = &subCtx;

        return erofs_iterate_dir(&walker.dirCtx, false);
    } else if (S_ISREG(inode->i_mode)) {
        int outFd = open(destPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (outFd < 0) return -errno;

    struct erofs_vfile vf = {};
        erofs_iopen(&vf, inode);

        char buf[65536];
        erofs_off_t offset = 0;
        while (offset < inode->i_size) {
            size_t toRead = std::min((erofs_off_t)sizeof(buf), inode->i_size - offset);
            ssize_t nread = erofs_io_pread(&vf, buf, toRead, offset);
            if (nread < 0) {
                close(outFd);
                return (int)nread;
            }
            if (write(outFd, buf, nread) != nread) {
                close(outFd);
                return -errno;
            }
            offset += nread;
            eCtx->processedBytes += nread;
            if (eCtx->progress && (offset % (1024 * 1024) == 0 || offset == inode->i_size)) {
                eCtx->progress->reportProgress(static_cast<long long>(eCtx->processedBytes), static_cast<long long>(eCtx->totalBytes));
            }
        }
        close(outFd);
        return 0;
    } else if (S_ISLNK(inode->i_mode)) {
        char linkTarget[4096];
    struct erofs_vfile vf = {};
        erofs_iopen(&vf, inode);
        ssize_t nread = erofs_io_pread(&vf, linkTarget, std::min((erofs_off_t)sizeof(linkTarget) - 1, inode->i_size), 0);
        if (nread >= 0) {
            linkTarget[nread] = '\0';
            if (symlink(linkTarget, destPath.c_str()) && errno != EEXIST) {
                // Ignore symlink errors on non-supporting filesystems (e.g. some SDCARDs)
            }
        }
        return 0;
    }
    return 0;
}

struct Ext4ExtractCtx {
    ProgressCtx *progress;
    std::string baseOutputDir;
    ext2_filsys fs;
    uint64_t totalBytes;
    uint64_t processedBytes;
};

static errcode_t ext4_extract_inode(Ext4ExtractCtx *eCtx, ext2_ino_t ino, const std::string &destPath);

static int ext4_dir_iter_cb(ext2_ino_t dir, int entry, struct ext2_dir_entry *dirent,
                            int offset, int blocksize, char *buf, void *priv) {
    auto *eCtx = (Ext4ExtractCtx *)priv;
    std::string name(dirent->name, dirent->name_len & 0xFF);
    if (name == "." || name == "..") return 0;

    std::string subDest = eCtx->baseOutputDir + "/" + name;
    ext4_extract_inode(eCtx, dirent->inode, subDest);
    return 0;
}

static errcode_t ext4_extract_inode(Ext4ExtractCtx *eCtx, ext2_ino_t ino, const std::string &destPath) {
    struct ext2_inode inode = {};
    memset(&inode, 0, sizeof(inode));
    errcode_t err = ext2fs_read_inode(eCtx->fs, ino, &inode);
    if (err) return err;

    if (LINUX_S_ISDIR(inode.i_mode)) {
        if (mkdir(destPath.c_str(), 0755) && errno != EEXIST) return -errno;

        Ext4ExtractCtx subCtx = *eCtx;
        subCtx.baseOutputDir = destPath;
        return ext2fs_dir_iterate2(eCtx->fs, ino, 0, nullptr, ext4_dir_iter_cb, &subCtx);
    } else if (LINUX_S_ISREG(inode.i_mode)) {
        int outFd = open(destPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (outFd < 0) return -errno;

        ext2_file_t file;
        err = ext2fs_file_open(eCtx->fs, ino, 0, &file);
        if (err) {
            close(outFd);
            return err;
        }

        char buf[65536];
        unsigned int got;
        uint64_t size = EXT2_I_SIZE(&inode);
        while (size > 0) {
            unsigned int wanted = (unsigned int)std::min((uint64_t)sizeof(buf), size);
            err = ext2fs_file_read(file, buf, wanted, &got);
            if (err) break;
            if (write(outFd, buf, got) != (ssize_t)got) {
                err = -errno;
                break;
            }
            size -= got;
            eCtx->processedBytes += got;
            if (eCtx->progress && (got % (1024 * 1024) == 0 || size == 0)) {
                eCtx->progress->reportProgress(static_cast<long long>(eCtx->processedBytes), static_cast<long long>(eCtx->totalBytes));
            }
        }
        ext2fs_file_close(file);
        close(outFd);
        return err;
    } else if (LINUX_S_ISLNK(inode.i_mode)) {
        char linkTarget[4096];
        size_t len = 0;
        if (ext2fs_is_fast_symlink(&inode)) {
            len = (size_t)EXT2_I_SIZE(&inode);
            if (len > sizeof(linkTarget) - 1) len = sizeof(linkTarget) - 1;
            memcpy(linkTarget, inode.i_block, len);
            linkTarget[len] = '\0';
        } else {
            // Read link from blocks
            ext2_file_t file;
            err = ext2fs_file_open(eCtx->fs, ino, 0, &file);
            if (!err) {
                unsigned int got;
                ext2fs_file_read(file, linkTarget, sizeof(linkTarget) - 1, &got);
                linkTarget[got] = '\0';
                ext2fs_file_close(file);
            }
        }
        if (symlink(linkTarget, destPath.c_str()) && errno != EEXIST) {
            // Ignore
        }
    }
    return 0;
}

extern "C" {

// ---------------------------------------------------------------------------
// nativeDetectFs
// Java: static native int nativeDetectFs(String imagePath);
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_xime_tools_XimeToolsNative_nativeDetectFs(JNIEnv *env, jclass /*clazz*/,
                                                jstring imagePath) {
    const char *path = env->GetStringUTFChars(imagePath, nullptr);
    if (!path) return FS_TYPE_UNKNOWN;

    // Read enough bytes to check both EROFS (offset 1024) and ext4 (offset 1080)
    static const int HEADER_SIZE = FS_SUPERBLOCK_OFFSET + EXT4_MAGIC_SB_OFFSET + 2;
    uint8_t header[HEADER_SIZE];
    memset(header, 0, sizeof(header));

    FILE *fp = fopen(path, "rb");
    env->ReleaseStringUTFChars(imagePath, path);

    if (!fp) return FS_TYPE_UNKNOWN;

    size_t nread = fread(header, 1, HEADER_SIZE, fp);
    fclose(fp);

    if (nread < (size_t)(FS_SUPERBLOCK_OFFSET + 4)) return FS_TYPE_UNKNOWN;

    uint32_t magic32 = readUInt32LE(header, FS_SUPERBLOCK_OFFSET);
    if (magic32 == (uint32_t)FS_EROFS_MAGIC) return FS_TYPE_EROFS;

    if (nread >= (size_t)(FS_SUPERBLOCK_OFFSET + EXT4_MAGIC_SB_OFFSET + 2)) {
        uint16_t magic16 = readUInt16LE(header, FS_SUPERBLOCK_OFFSET + EXT4_MAGIC_SB_OFFSET);
        if (magic16 == (uint16_t)FS_EXT4_MAGIC) return FS_TYPE_EXT4;
    }

    return FS_TYPE_UNKNOWN;
}

// ---------------------------------------------------------------------------
// nativeExtractErofs
// Java: static native int nativeExtractErofs(String imagePath, String outputDir,
//                                             ProgressListener listener);
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_xime_tools_XimeToolsNative_nativeExtractErofs(JNIEnv *env, jclass /*clazz*/,
                                                    jstring imagePath,
                                                    jstring outputDir,
                                                    jobject listener) {
    const char *cImagePath = env->GetStringUTFChars(imagePath, nullptr);
    const char *cOutputDir = env->GetStringUTFChars(outputDir, nullptr);

    LOGD("nativeExtractErofs: image=%s outputDir=%s", cImagePath, cOutputDir);

    ProgressCtx *pCtx = createProgressCtx(env, listener);

    int result = XIME_ERR_OK;
    struct erofs_sb_info sbi = {};
    memset(&sbi, 0, sizeof(sbi));

    erofs_init_configure();

    int err = erofs_dev_open(&sbi, cImagePath, O_RDONLY);
    if (err) {
        LOGE("Failed to open EROFS device %s: %s", cImagePath, strerror(-err));
        result = XIME_ERR_IO;
        goto done;
    }

    err = erofs_read_superblock(&sbi);
    if (err) {
        LOGE("Failed to read EROFS superblock: %d", err);
        result = XIME_ERR_CORRUPT;
        goto done;
    }

    {
    struct stat st = {};
        uint64_t totalBytes = 0;
        if (stat(cImagePath, &st) == 0) totalBytes = st.st_size;

        ErofsExtractCtx eCtx = { pCtx, cOutputDir, &sbi, totalBytes, 0 };
        struct erofs_inode rootInode = {};
        memset(&rootInode, 0, sizeof(rootInode));
        rootInode.sbi = &sbi;
        rootInode.nid = sbi.root_nid;

        err = erofs_read_inode_from_disk(&rootInode);
        if (err) {
             LOGE("Failed to read EROFS root inode: %d", err);
             result = XIME_ERR_CORRUPT;
             goto done;
        }

        err = erofs_extract_inode(&eCtx, &rootInode, cOutputDir);
        if (err) {
            LOGE("EROFS extraction loop failed: %d", err);
            result = XIME_ERR_IO;
        }
    }

done:
    erofs_dev_close(&sbi);
    if (pCtx) {
        if (result == XIME_ERR_OK)  pCtx->reportComplete();
        else                         pCtx->reportError("EROFS extraction failed");
        destroyProgressCtx(env, pCtx);
    }

    env->ReleaseStringUTFChars(imagePath, cImagePath);
    env->ReleaseStringUTFChars(outputDir, cOutputDir);
    return (jint)result;
}

// ---------------------------------------------------------------------------
// nativeCreateErofs
// Java: static native int nativeCreateErofs(String sourceDir, String imagePath,
//                                            ErofsOptions opts, ProgressListener listener);
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_xime_tools_XimeToolsNative_nativeCreateErofs(JNIEnv *env, jclass /*clazz*/,
                                                   jstring sourceDir,
                                                   jstring imagePath,
                                                   jobject opts,
                                                   jobject listener) {
    const char *cSourceDir  = env->GetStringUTFChars(sourceDir, nullptr);
    const char *cImagePath  = env->GetStringUTFChars(imagePath, nullptr);

    LOGD("nativeCreateErofs: source=%s image=%s", cSourceDir, cImagePath);

    ProgressCtx *pCtx = createProgressCtx(env, listener);
    int result = XIME_ERR_OK;

    erofs_init_configure();
    cfg.c_showprogress = false;

    // Map Options
    jclass optsCls = env->GetObjectClass(opts);
    jint blockSize = env->CallIntMethod(opts, env->GetMethodID(optsCls, "getBlockSize", "()I"));
    cfg.c_chunkbits = xime_ilog2(blockSize);

    // Default to a sane build time if PRESERVE is not used (handled by erofs-utils internally if PRESERVE)
    // But we can force it here if needed.

    struct erofs_sb_info sbi = {};
    memset(&sbi, 0, sizeof(sbi));

    int err = erofs_dev_open(&sbi, cImagePath, O_WRONLY | O_CREAT | O_TRUNC);
    if (err) {
        LOGE("Failed to open output image %s: %s", cImagePath, strerror(-err));
        result = XIME_ERR_IO;
        goto done;
    }

    {
        struct erofs_importer_params params;
        erofs_importer_preset(&params);
        params.source = (char *)cSourceDir;

        struct erofs_importer im = { &params, &sbi, nullptr };

        err = erofs_importer_init(&im);
        if (err) {
            LOGE("erofs_importer_init failed: %d", err);
            result = XIME_ERR_IO;
            goto close_dev;
        }

        im.root = erofs_make_empty_root_inode(&im, &sbi);
        if (IS_ERR(im.root)) {
            result = XIME_ERR_IO;
            goto close_dev;
        }

        if (pCtx) pCtx->reportProgress(0, 100); // Start

        err = erofs_importer_load_tree(&im, false, false);
        if (err) {
            LOGE("erofs_importer_load_tree failed: %d", err);
            result = XIME_ERR_IO;
            goto exit_importer;
        }

        err = erofs_importer_flush_all(&im);
        if (err) {
            LOGE("erofs_importer_flush_all failed: %d", err);
            result = XIME_ERR_IO;
        }

    exit_importer:
        erofs_importer_exit(&im);
    }

close_dev:
    erofs_dev_close(&sbi);

done:
    if (pCtx) {
        if (result == XIME_ERR_OK)  pCtx->reportComplete();
        else                         pCtx->reportError("EROFS creation failed");
        destroyProgressCtx(env, pCtx);
    }

    env->ReleaseStringUTFChars(sourceDir, cSourceDir);
    env->ReleaseStringUTFChars(imagePath, cImagePath);
    return (jint)result;
}

// ---------------------------------------------------------------------------
// nativeExtractExt4
// Java: static native int nativeExtractExt4(String imagePath, String outputDir,
//                                            ProgressListener listener);
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_xime_tools_XimeToolsNative_nativeExtractExt4(JNIEnv *env, jclass /*clazz*/,
                                                   jstring imagePath,
                                                   jstring outputDir,
                                                   jobject listener) {
    const char *cImagePath = env->GetStringUTFChars(imagePath, nullptr);
    const char *cOutputDir = env->GetStringUTFChars(outputDir, nullptr);

    LOGD("nativeExtractExt4: image=%s outputDir=%s", cImagePath, cOutputDir);

    ProgressCtx *pCtx = createProgressCtx(env, listener);
    int result = XIME_ERR_OK;
    ext2_filsys fs = nullptr;

    errcode_t err = ext2fs_open(cImagePath, 0, 0, 0, unix_io_manager, &fs);
    if (err) {
        LOGE("Failed to open EXT4 image %s: %ld", cImagePath, (long)err);
        result = XIME_ERR_IO;
        goto done;
    }

    {
    struct stat st = {};
        uint64_t totalBytes = 0;
        if (stat(cImagePath, &st) == 0) totalBytes = st.st_size;

        Ext4ExtractCtx eCtx = { pCtx, cOutputDir, fs, totalBytes, 0 };
        err = ext4_extract_inode(&eCtx, EXT2_ROOT_INO, cOutputDir);
        if (err) {
            LOGE("EXT4 extraction loop failed: %ld", (long)err);
            result = XIME_ERR_IO;
        }
    }

done:
    if (fs) ext2fs_close(fs);
    if (pCtx) {
        if (result == XIME_ERR_OK)  pCtx->reportComplete();
        else                         pCtx->reportError("EXT4 extraction failed");
        destroyProgressCtx(env, pCtx);
    }

    env->ReleaseStringUTFChars(imagePath, cImagePath);
    env->ReleaseStringUTFChars(outputDir, cOutputDir);
    return (jint)result;
}

// ---------------------------------------------------------------------------
// nativePackExt4
// Java: static native int nativePackExt4(String sourceDir, String imagePath,
//                                         Ext4Options opts, ProgressListener listener);
// ---------------------------------------------------------------------------
struct Ext4PackCtx {
    ProgressCtx *progress;
    ext2_filsys fs;
    uint64_t totalBytes;
    uint64_t processedBytes;
};

static errcode_t ext4_pack_file(Ext4PackCtx *ctx, ext2_ino_t parent, const char *name, const std::string &srcPath) {
    struct stat st;
    if (lstat(srcPath.c_str(), &st)) return -errno;

    ext2_ino_t ino;
    errcode_t err = ext2fs_new_inode(ctx->fs, parent, st.st_mode, nullptr, &ino);
    if (err) return err;

    struct ext2_inode inode;
    memset(&inode, 0, sizeof(inode));
    inode.i_mode = st.st_mode;
    inode.i_links_count = 1;
    inode.i_uid = st.st_uid;
    inode.i_gid = st.st_gid;
    inode.i_atime = st.st_atime;
    inode.i_mtime = st.st_mtime;
    inode.i_ctime = st.st_ctime;

    if (S_ISREG(st.st_mode)) {
        inode.i_size = st.st_size;
        err = ext2fs_write_new_inode(ctx->fs, ino, &inode);
        if (err) return err;

        ext2_file_t file;
        err = ext2fs_file_open(ctx->fs, ino, EXT2_FILE_WRITE, &file);
        if (err) return err;

        int fd = open(srcPath.c_str(), O_RDONLY);
        if (fd < 0) { ext2fs_file_close(file); return -errno; }

        char buf[65536];
        ssize_t nread;
        while ((nread = read(fd, buf, sizeof(buf))) > 0) {
            unsigned int written;
            err = ext2fs_file_write(file, buf, (unsigned int)nread, &written);
            if (err) break;
            ctx->processedBytes += written;
            if (ctx->progress) ctx->progress->reportProgress(ctx->processedBytes, ctx->totalBytes);
        }
        close(fd);
        ext2fs_file_close(file);
        if (err) return err;
    } else if (S_ISLNK(st.st_mode)) {
        char link[PATH_MAX];
        ssize_t len = readlink(srcPath.c_str(), link, sizeof(link) - 1);
        if (len < 0) return -errno;
        link[len] = '\0';
        err = ext2fs_symlink(ctx->fs, parent, ino, name, link);
        if (err) return err;
    } else if (S_ISDIR(st.st_mode)) {
        err = ext2fs_mkdir(ctx->fs, parent, ino, name);
        if (err) return err;

        DIR *d = opendir(srcPath.c_str());
        if (!d) return -errno;
        struct dirent *de;
        while ((de = readdir(d))) {
            if (strcmp(de->d_name, ".") == 0 || strcmp(de->d_name, "..") == 0) continue;
            err = ext4_pack_file(ctx, ino, de->d_name, srcPath + "/" + de->d_name);
            if (err) break;
        }
        closedir(d);
        if (err) return err;
    }

    return ext2fs_link(ctx->fs, parent, name, ino, EXT2_FT_UNKNOWN);
}

JNIEXPORT jint JNICALL
Java_xime_tools_XimeToolsNative_nativePackExt4(JNIEnv *env, jclass /*clazz*/,
                                                jstring sourceDir,
                                                jstring imagePath,
                                                jobject opts,
                                                jobject listener) {
    const char *cSourceDir = env->GetStringUTFChars(sourceDir, nullptr);
    const char *cImagePath = env->GetStringUTFChars(imagePath, nullptr);

    LOGD("nativePackExt4: source=%s image=%s", cSourceDir, cImagePath);

    ProgressCtx *pCtx = createProgressCtx(env, listener);
    int result = XIME_ERR_OK;
    ext2_filsys fs = nullptr;

    // Map Options
    jclass optsCls = env->GetObjectClass(opts);
    jint blockSize = env->CallIntMethod(opts, env->GetMethodID(optsCls, "getBlockSize", "()I"));
    jint inodeSize = env->CallIntMethod(opts, env->GetMethodID(optsCls, "getInodeSize", "()I"));
    jlong featureFlags = env->CallLongMethod(opts, env->GetMethodID(optsCls, "getFeatureFlags", "()J"));
    jlong volumeSize = env->CallLongMethod(opts, env->GetMethodID(optsCls, "getVolumeSizeBytes", "()J"));

    struct ext2_super_block param;
    memset(&param, 0, sizeof(param));
    // Simple feature set assignment for building
    param.s_feature_incompat = (uint32_t)featureFlags;
    param.s_log_block_size = xime_ilog2(blockSize) - 10;
    param.s_inode_size = inodeSize;

    if (volumeSize == 0) {
        // Simple heuristic: 1.2x of source size
        volumeSize = get_dir_size_recursive(cSourceDir) * 1.2 + 1024 * 1024;
    }
    ext2fs_blocks_count_set(&param, volumeSize / blockSize);

    errcode_t err = ext2fs_initialize(cImagePath, 0, &param, unix_io_manager, &fs);
    if (err) {
        LOGE("ext2fs_initialize failed: %ld", (long)err);
        result = XIME_ERR_IO;
        goto done;
    }

    err = ext2fs_allocate_tables(fs);
    if (err) {
        LOGE("ext2fs_allocate_tables failed: %ld", (long)err);
        result = XIME_ERR_IO;
        goto done;
    }

    {
        Ext4PackCtx ctx = { pCtx, fs, get_dir_size_recursive(cSourceDir), 0 };
        DIR *d = opendir(cSourceDir);
        if (d) {
            struct dirent *de;
            while ((de = readdir(d))) {
                if (strcmp(de->d_name, ".") == 0 || strcmp(de->d_name, "..") == 0) continue;
                err = ext4_pack_file(&ctx, EXT2_ROOT_INO, de->d_name, std::string(cSourceDir) + "/" + de->d_name);
                if (err) break;
            }
            closedir(d);
        } else {
            err = -errno;
        }

        if (err) {
            LOGE("EXT4 packing loop failed: %ld", (long)err);
            result = XIME_ERR_IO;
        }
    }

done:
    if (fs) ext2fs_close(fs);
    if (pCtx) {
        if (result == XIME_ERR_OK)  pCtx->reportComplete();
        else                         pCtx->reportError("EXT4 packing failed");
        destroyProgressCtx(env, pCtx);
    }

    env->ReleaseStringUTFChars(sourceDir, cSourceDir);
    env->ReleaseStringUTFChars(imagePath, cImagePath);
    return (jint)result;
}

// ---------------------------------------------------------------------------
// nativeGetSubprocessBinaryPath
// Java: static native String nativeGetSubprocessBinaryPath(String toolName);
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_xime_tools_XimeToolsNative_nativeGetSubprocessBinaryPath(JNIEnv *env, jclass /*clazz*/,
                                                               jstring toolName) {
    // Subprocess-mode: the binary is provisioned via ToolsBootstrap (Java-side),
    // which writes the path to a known location. This native entry point is a
    // future hook for path lookup that avoids a Java round-trip in tight loops.
    // For now, return null to indicate the path is not known natively.
    (void)toolName;
    return nullptr;
}

} // extern "C"
