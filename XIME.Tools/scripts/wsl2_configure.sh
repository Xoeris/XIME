#!/usr/bin/env bash
# =============================================================================
# wsl2_configure.sh, XIME.Tools native pre-build configuration
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(dirname "$SCRIPT_DIR")"
THIRD_PARTY="$MODULE_DIR/src/main/cpp/third_party"
EROFS_DIR="$THIRD_PARTY/erofs-utils"
E2FS_DIR="$THIRD_PARTY/e2fsprogs"

# NDK Configuration
NDK_PATH="/mnt/c/Users/Acelbyte/AppData/Local/Android/Sdk/ndk/28.2.13676358"
API_LEVEL=24
HOST_TAG="windows-x86_64"
TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/$HOST_TAG"

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")

declare -A TRIPLES
TRIPLES["arm64-v8a"]="aarch64-linux-android"
TRIPLES["armeabi-v7a"]="armv7a-linux-androideabi"
TRIPLES["x86_64"]="x86_64-linux-android"
TRIPLES["x86"]="i686-linux-android"

configure_and_capture() {
    local name="$1"
    local repo_url="$2"
    local branch="$3"
    local abi="$4"
    local triple="${TRIPLES[$abi]}"
    local extra_args="$5"
    local dest_config_dir="$6"

    echo "  [$abi] Configuring $name..."
    mkdir -p "$dest_config_dir"

    local scratch_dir="/tmp/${name}_scratch_${abi}"
    rm -rf "$scratch_dir"
    git clone --depth 1 --branch "$branch" "$repo_url" "$scratch_dir" > /dev/null 2>&1

    pushd "$scratch_dir" > /dev/null

    local CC_WRAPPER="$TOOLCHAIN/bin/${triple}${API_LEVEL}-clang"
    local CXX_WRAPPER="$TOOLCHAIN/bin/${triple}${API_LEVEL}-clang++"
    if [[ "$abi" == "armeabi-v7a" ]]; then
        CC_WRAPPER="$TOOLCHAIN/bin/armv7a-linux-androideabi${API_LEVEL}-clang"
        CXX_WRAPPER="$TOOLCHAIN/bin/armv7a-linux-androideabi${API_LEVEL}-clang++"
    fi

    export CC="$CC_WRAPPER"
    export CXX="$CXX_WRAPPER"
    export AR="$TOOLCHAIN/bin/llvm-ar.exe"
    export RANLIB="$TOOLCHAIN/bin/llvm-ranlib.exe"
    export STRIP="$TOOLCHAIN/bin/llvm-strip.exe"
    export NM="$TOOLCHAIN/bin/llvm-nm.exe"

    echo "    Running autoreconf..."
    autoreconf -fi > /dev/null 2>&1 || true

    echo "    Running configure..."
    ./configure --host="$triple" \
                --prefix=/tmp/xime-tools-build \
                --disable-shared \
                --enable-static \
                --disable-dependency-tracking \
                $extra_args \
                > configure.log 2>&1 || echo "    Warning: configure returned error (expected if some tools missing)"

    # Capture results
    local captured=0
    for f in "config.h" "lib/config.h" "include/config.h"; do
        if [ -f "$f" ]; then
            cp "$f" "$dest_config_dir/config.h"
            echo "    config.h captured ✓"
            captured=1
            break
        fi
    done

    if [[ "$name" == "e2fsprogs" ]]; then
        if [ -f "lib/ext2fs/ext2_types.h" ]; then
            cp "lib/ext2fs/ext2_types.h" "$dest_config_dir/ext2_types.h"
            echo "    ext2_types.h captured ✓"
        fi
    fi

    popd > /dev/null
    rm -rf "$scratch_dir"

    if [ "$captured" -eq 0 ]; then
        echo "    Error: failed to generate config.h for $name [$abi]"
        exit 1
    fi
}

echo "=== XIME.Tools: wsl2_configure.sh ==="
for abi in "${ABIS[@]}"; do
    echo ""
    echo "--- ABI: $abi ---"

    # erofs-utils v1.9.3
    configure_and_capture "erofs-utils" "https://github.com/erofs/erofs-utils.git" "v1.9.3" \
                          "$abi" "--disable-multithreading --without-uuid" \
                          "$EROFS_DIR/android-config/$abi"

    # e2fsprogs v1.47.4
    configure_and_capture "e2fsprogs" "https://github.com/tytso/e2fsprogs.git" "v1.47.4" \
                          "$abi" "--disable-elf-shlibs --disable-bsd-shlibs" \
                          "$E2FS_DIR/android-config/$abi"
done

echo ""
echo "=== Done ==="
