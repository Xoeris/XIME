cmake_minimum_required(VERSION 3.22.1)
project("xime-tools" LANGUAGES C CXX)

# ---------------------------------------------------------------------------
# Language standards
# ---------------------------------------------------------------------------
set(CMAKE_C_STANDARD   11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# NDK system libraries
# ---------------------------------------------------------------------------
find_library(log-lib  log)
find_library(z-lib    z)

# ---------------------------------------------------------------------------
# Third-party source root
# ---------------------------------------------------------------------------
set(TP "${CMAKE_SOURCE_DIR}/third_party")

# ---------------------------------------------------------------------------
# liberofs (static) — erofs-utils v1.9.3, lib/ (GPL-2.0+ OR MIT)
#
# Explicitly listed rather than globbed to avoid accidentally pulling in
# compressor_lz4.c etc. which unconditionally include <lz4.h>/<lzma.h>
# headers that are NOT in the NDK sysroot.
#
# Excluded:
#   compressor_lz4.c / compressor_lz4hc.c  — unconditional #include <lz4.h>
#   compressor_liblzma.c                   — unconditional #include <lzma.h>
#   compressor_libzstd.c                   — unconditional #include <zstd.h>
#   compressor_libdeflate.c                — unconditional #include <libdeflate.h>
#   backends/fanotify.c                    — requires Linux fanotify (not Bionic)
#   backends/nbd.c                         — Network Block Device (not on Android)
#   backends/ublk.c                        — userspace block (not on Android)
#   gzran.c                                — requires zlib inflate internals
#   importer.c                             — OCI/tar/VMDK import, not needed
#   tar.c                                  — tar parsing (pulls gzran/lzma)
#   vmdk.c                                 — VMware disk format
# ---------------------------------------------------------------------------
set(EROFS_SOURCES
    "${TP}/erofs-utils/lib/base64.c"
    "${TP}/erofs-utils/lib/bitops.c"
    "${TP}/erofs-utils/lib/blobchunk.c"
    "${TP}/erofs-utils/lib/block_list.c"
    "${TP}/erofs-utils/lib/cache.c"
    "${TP}/erofs-utils/lib/compress.c"
    "${TP}/erofs-utils/lib/compress_hints.c"
    "${TP}/erofs-utils/lib/compressor.c"
    "${TP}/erofs-utils/lib/compressor_deflate.c"
    "${TP}/erofs-utils/lib/config.c"
    "${TP}/erofs-utils/lib/data.c"
    "${TP}/erofs-utils/lib/decompress.c"
    "${TP}/erofs-utils/lib/dedupe.c"
    "${TP}/erofs-utils/lib/dedupe_ext.c"
    "${TP}/erofs-utils/lib/dir.c"
    "${TP}/erofs-utils/lib/diskbuf.c"
    "${TP}/erofs-utils/lib/exclude.c"
    "${TP}/erofs-utils/lib/fragments.c"
    "${TP}/erofs-utils/lib/global.c"
    "${TP}/erofs-utils/lib/hashmap.c"
    "${TP}/erofs-utils/lib/io.c"
    "${TP}/erofs-utils/lib/importer.c"
    "${TP}/erofs-utils/lib/inode.c"
    "${TP}/erofs-utils/lib/kite_deflate.c"
    "${TP}/erofs-utils/lib/metabox.c"
    "${TP}/erofs-utils/lib/namei.c"
    "${TP}/erofs-utils/lib/rebuild.c"
    "${TP}/erofs-utils/lib/rolling_hash.h"  # header included as source by Makefile.am
    "${TP}/erofs-utils/lib/sha256.c"
    "${TP}/erofs-utils/lib/super.c"
    "${TP}/erofs-utils/lib/uuid.c"
    "${TP}/erofs-utils/lib/uuid_unparse.c"
    "${TP}/erofs-utils/lib/workqueue.c"
    "${TP}/erofs-utils/lib/xattr.c"
    "${TP}/erofs-utils/lib/xxhash.c"
    "${TP}/erofs-utils/lib/zmap.c"
)

# Remove rolling_hash.h from sources (it's a header, not compiled directly)
list(FILTER EROFS_SOURCES EXCLUDE REGEX ".*\\.h$")

add_library(erofs STATIC ${EROFS_SOURCES})

target_include_directories(erofs PUBLIC
    "${TP}/erofs-utils/include"
    "${TP}/erofs-utils/lib"
    # ABI-specific autoconf-generated config.h:
    "${TP}/erofs-utils/android-config/${ANDROID_ABI}")

target_compile_definitions(erofs PRIVATE
    HAVE_CONFIG_H
    __ANDROID__
    _FILE_OFFSET_BITS=64
    _LARGEFILE_SOURCE)

target_compile_options(erofs PRIVATE
    -Wno-unused-parameter
    -Wno-unused-function
    -Wno-sign-compare
    -Wno-implicit-function-declaration
    -Wno-missing-field-initializers)

# ---------------------------------------------------------------------------
# libext2fs (static) — e2fsprogs v1.47.4
#
# Includes: lib/ext2fs, lib/et, lib/uuid, lib/support, lib/e2p
#
# Files excluded from ext2fs/ because they contain unconditional main():
#   crc32c.c, csum.c, digest_encode.c, finddev.c, gen_crc32ctable.c,
#   getsize.c, icount.c (main under DEBUG), sha256.c, sha512.c,
#   tdbtool.c, tst_*.c  (test programs)
#
# Note: crc32c.c and sha256.c have main() only under #ifdef DEBUG / test
# guards evaluated at runtime, so they ARE safe to include — the main()
# bodies are inside #ifdef DEBUG blocks. gen_crc32ctable.c is a code
# generator run at build time only, so it IS excluded.
# ---------------------------------------------------------------------------

# ext2fs core
file(GLOB EXT2FS_SOURCES "${TP}/e2fsprogs/lib/ext2fs/*.c")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/gen_crc32ctable\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/tst_.*\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/tdbtool\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/dosio\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/nt_io\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/windows_io\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/bmove\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/brel_ma\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/irel_ma\\.c$")
list(FILTER EXT2FS_SOURCES EXCLUDE REGEX ".*/qcow2\\.c$") # often depends on external headers if not configured right

# lib/et — com_err (MIT SIPB license)
file(GLOB ET_SOURCES "${TP}/e2fsprogs/lib/et/*.c")
list(FILTER ET_SOURCES EXCLUDE REGEX ".*/test_.*\\.c$")

# lib/uuid — UUID generation (BSD 3-clause)
file(GLOB UUID_SOURCES "${TP}/e2fsprogs/lib/uuid/*.c")
list(FILTER UUID_SOURCES EXCLUDE REGEX ".*/gen_uuid_nt\\.c$")  # Windows-only

# lib/support — dict, etc. (permissive)
file(GLOB SUPPORT_SOURCES "${TP}/e2fsprogs/lib/support/*.c")

# lib/e2p — extended properties (LGPL v2)
file(GLOB E2P_SOURCES "${TP}/e2fsprogs/lib/e2p/*.c")
list(FILTER E2P_SOURCES EXCLUDE REGEX ".*/tst_.*\\.c$")

add_library(ext2fs STATIC
    ${EXT2FS_SOURCES}
    ${ET_SOURCES}
    ${UUID_SOURCES}
    ${SUPPORT_SOURCES}
    ${E2P_SOURCES})

target_include_directories(ext2fs PUBLIC
    # Include path for <ext2fs/ext2fs.h> style includes:
    "${TP}/e2fsprogs/include"
    # lib/ext2fs headers included as "ext2fs.h" (without prefix):
    "${TP}/e2fsprogs/lib/ext2fs"
    "${TP}/e2fsprogs/lib/et"
    "${TP}/e2fsprogs/lib/uuid"
    "${TP}/e2fsprogs/lib/support"
    "${TP}/e2fsprogs/lib" # for "e2p/e2p.h" style includes
    # ABI-specific config.h and ext2_types.h:
    "${TP}/e2fsprogs/android-config/${ANDROID_ABI}")

target_compile_definitions(ext2fs PRIVATE
    HAVE_CONFIG_H
    __ANDROID__
    _FILE_OFFSET_BITS=64
    _LARGEFILE_SOURCE
    # Disable features that require kernel ioctl privileges not available on Android
    NO_CHECK_BB_OUT_OF_BOUNDS)

target_compile_options(ext2fs PRIVATE
    -Wno-unused-parameter
    -Wno-unused-function
    -Wno-sign-compare
    -Wno-implicit-function-declaration
    -Wno-missing-field-initializers
    -Wno-char-subscripts)

# ---------------------------------------------------------------------------
# libxime-tools (shared) — the JNI bridge .so packaged by Gradle
# ---------------------------------------------------------------------------
add_library(xime-tools SHARED
    xime_tools_jni.cpp)

target_include_directories(xime-tools PRIVATE
    "${TP}/erofs-utils/include"
    "${TP}/erofs-utils/lib"
    "${TP}/erofs-utils/android-config/${ANDROID_ABI}"
    "${TP}/e2fsprogs/include"
    "${TP}/e2fsprogs/lib/ext2fs"
    "${TP}/e2fsprogs/lib/et"
    "${TP}/e2fsprogs/android-config/${ANDROID_ABI}")

target_compile_definitions(xime-tools PRIVATE
    HAVE_CONFIG_H
    __ANDROID__
    _FILE_OFFSET_BITS=64
    _LARGEFILE_SOURCE)

# Link order matters: xime-tools -> erofs -> ext2fs -> z (zlib) -> log
target_link_libraries(xime-tools
    erofs
    ext2fs
    ${z-lib}
    ${log-lib})

# ---------------------------------------------------------------------------
# Build pipeline notes
# ---------------------------------------------------------------------------
# The android-config/<ABI>/ directories contain config.h (and ext2_types.h
# for e2fsprogs) that were generated from Bionic API 24 feature inspection
# against NDK 28. They are committed to the repo so Gradle builds do not
# require WSL2 at every invocation.
#
# The wsl2_configure.sh script in scripts/ can re-generate these headers
# if the NDK version or min API level is changed.
#
# Compression backends not included in this build:
#   LZ4  — add -DHAVE_LIBLZ4 and link liblz4 to re-enable
#   LZMA — add -DHAVE_LIBLZMA and link liblzma to re-enable
#   ZSTD — add -DHAVE_LIBZSTD and link libzstd to re-enable
