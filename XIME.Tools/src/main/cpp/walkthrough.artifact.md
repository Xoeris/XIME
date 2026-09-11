# EROFS and EXT4 Vendoring Walkthrough

Successfully vendored and configured `erofs-utils` and `e2fsprogs` (libext2fs) for the `XIME.Tools` module. The build now completes successfully for all target ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).

## Changes Made

### E2FSPROGS (libext2fs) Configuration
- **Generated Headers**:
    - Generated `ext2_err.h/c` and `prof_err.h/c` from error table files using `awk` via WSL.
    - Generated `crc32c_table.h` using the `gen_crc32ctable` tool.
- **Header Fixes**:
    - Relocated ABI-specific `ext2_types.h` to correct paths (`android-config/<ABI>/ext2fs/ext2_types.h`).
    - Provided `include/uuid/uuid.h` and `include/blkid/blkid.h` (stubs) to satisfy dependencies.
    - Vendored `version.h` to define the library version.
- **Build Refinement**:
    - Updated `CMakeLists.txt` to exclude OS-specific (`dosio.c`, `nt_io.c`, etc.) and problematic files (`bmove.c`, `irel_ma.c`).
    - Defined `_FILE_OFFSET_BITS=64` to ensure 64-bit block support across all architectures.

### EROFS-UTILS Configuration
- **Source Patches**:
    - Updated `lib/compress.c` to include `config.h`, ensuring `EROFS_MT_ENABLED` is correctly handled.
    - Patched `include/erofs/defs.h` to use `__typeof__` instead of `typeof` for compatibility with C++ compilers (Clang++).
- **Facade Headers**:
    - Created `include/erofs/erofs.h` and `include/erofs/compress.h` to match the expected JNI bridge structure.

### JNI Bridge Fixes
- **Macro Collisions**: Fixed collisions between `ext2fs.h` macros and JNI bridge `constexpr` variables by renaming `SUPERBLOCK_OFFSET`, `EROFS_MAGIC`, and `EXT4_MAGIC`.

## Verification Results

### Build Success
- Ran `./gradlew :XIME.Tools:assembleDebug` successfully.
- Verified that `liberofs.a` and `libext2fs.a` are built and linked into `libxime-tools.so`.

### Architecture Support
- Confirmed compatibility with 32-bit platforms (`armeabi-v7a`, `x86`) by resolving `off_t` size assertions.

## Next Steps
- [ ] Un-stub `xime_tools_jni.cpp` by implementing the actual EROFS/EXT4 extraction and creation logic using the now-linked libraries.
- [ ] Implement Java-side unit tests to verify detection and extraction of real filesystem images.
