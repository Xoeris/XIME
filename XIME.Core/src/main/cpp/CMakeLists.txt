cmake_minimum_required(VERSION 3.22.1)
project("lucine-interpolator")

# --- Existing classical motion-compensation lib (unchanged) ---
add_library(lucine-interpolator SHARED lucine-interpolator.c)

find_library(log-lib log)

target_link_libraries(lucine-interpolator
        ${log-lib}
)

# --- New: ncnn-vulkan RIFE bridge ---
#
# Expects a prebuilt ncnn distribution (built with -DNCNN_VULKAN=ON) unpacked
# somewhere Gradle can point CMake at, e.g. via a Gradle property:
#   -DNCNN_ROOT_DIR=/path/to/ncnn-YYYYMMDD-android-vulkan
# with that directory laid out as:
#   ${NCNN_ROOT_DIR}/${ANDROID_ABI}/include/ncnn/*.h
#   ${NCNN_ROOT_DIR}/${ANDROID_ABI}/lib/libncnn.a
# (this is exactly how the official ncnn Android release archives are shaped).
# Grab one from https://github.com/Tencent/ncnn/releases (the
# "*-android-vulkan.zip" asset) or build it yourself per ncnn's
# build-for-android docs.
if(NOT DEFINED NCNN_ROOT_DIR)
    set(NCNN_ROOT_DIR "${CMAKE_SOURCE_DIR}/../ncnn-vulkan" CACHE PATH "Path to prebuilt ncnn-vulkan release")
endif()

set(NCNN_INCLUDE_DIR "${NCNN_ROOT_DIR}/${ANDROID_ABI}/include/ncnn")
set(NCNN_LIB_DIR "${NCNN_ROOT_DIR}/${ANDROID_ABI}/lib")

add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES
        IMPORTED_LOCATION "${NCNN_LIB_DIR}/libncnn.a"
        INTERFACE_INCLUDE_DIRECTORIES "${NCNN_INCLUDE_DIR}"
)

# The Vulkan backend compiles GLSL to SPIR-V at runtime via glslang, and the
# official release zip ships glslang's own static libs (libglslang.a,
# libSPIRV.a, libMachineIndependent.a, libOSDependent.a, libOGLCompiler.a,
# libGenericCodeGen.a, libSPVRemapper.a, libGenericCodeGen.a, ...) in the
# SAME lib/ folder as libncnn.a. Exact filenames drift a bit between ncnn
# release builds, so glob everything there instead of hardcoding names --
# any .a that isn't libncnn.a itself is one of these glslang/SPIRV pieces.
file(GLOB NCNN_EXTRA_STATIC_LIBS "${NCNN_LIB_DIR}/*.a")
list(FILTER NCNN_EXTRA_STATIC_LIBS EXCLUDE REGEX ".*libncnn\\.a$")

if(NOT NCNN_EXTRA_STATIC_LIBS)
    message(WARNING "No glslang/SPIRV static libs found next to libncnn.a in "
            "${NCNN_LIB_DIR} -- if the link step fails with undefined glslang::* "
            "symbols, re-download the *-android-vulkan.zip release asset (not "
            "the non-vulkan one) and make sure the whole lib/ folder was "
            "extracted, not just libncnn.a.")
endif()

find_library(vulkan-lib vulkan)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

add_library(rife_ncnn_bridge SHARED rife_ncnn_bridge.cpp)

target_include_directories(rife_ncnn_bridge PRIVATE ${NCNN_INCLUDE_DIR})

# ncnn's CPU-fallback ops are parallelized with OpenMP (that's where the
# __kmpc_*/omp_*/kmp_* symbols come from). The NDK ships libomp but does not
# link it by default -- ask clang for it explicitly. -static-openmp folds
# libomp directly into this .so instead of depending on a shared libomp.so,
# which avoids ODR/duplicate-runtime problems if some other native lib in
# the app also links OpenMP separately (Google's own recommendation for NDK
# OpenMP use). Must be on both the compile and link lines.
target_compile_options(rife_ncnn_bridge PRIVATE -fopenmp)
target_link_options(rife_ncnn_bridge PRIVATE -fopenmp -static-openmp)

target_link_libraries(rife_ncnn_bridge
        ncnn
        ${NCNN_EXTRA_STATIC_LIBS}
        ${vulkan-lib}
        ${android-lib}
        ${jnigraphics-lib}
        ${log-lib}
)

# ncnn's static lib expects C++17 and RTTI/exceptions off, per its own build.
target_compile_features(rife_ncnn_bridge PRIVATE cxx_std_17)
set_target_properties(rife_ncnn_bridge PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
)