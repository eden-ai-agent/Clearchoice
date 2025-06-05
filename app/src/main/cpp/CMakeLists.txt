cmake_minimum_required(VERSION 3.18.1)

project(ClearChoiceNative LANGUAGES C CXX)

# Set C++ standard to C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add whisper.cpp static library
add_library(whisper_cpp_lib STATIC
    whisper/whisper.c
    whisper/ggml.c
)

# Specify include directories for whisper_cpp_lib
target_include_directories(whisper_cpp_lib PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper
)

# Compiler flags
# Common flags for release builds
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")

# Architecture specific flags
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(whisper_cpp_lib PRIVATE -fomit-frame-pointer -ftree-vectorize -march=armv8-a+crc+crypto)
    message(STATUS "Added ARM64 specific optimization flags for whisper_cpp_lib")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(whisper_cpp_lib PRIVATE -mfpu=neon -mfloat-abi=softfp -ftree-vectorize)
    message(STATUS "Added ARMv7-A specific optimization flags for whisper_cpp_lib")
endif()

# Add any other libraries or configurations needed later (e.g., JNI library)
# For now, this CMakeLists.txt focuses only on whisper_cpp_lib

# --- JNI Library for the Application ---
# Add the JNI shared library
add_library(clearchoice_jni SHARED
    native-lib.cpp
)

# Specify include directories for the JNI library (if any beyond whisper_cpp_lib)
# target_include_directories(clearchoice_jni PRIVATE ...)

# Link JNI library against whisper_cpp_lib and Android NDK libraries
target_link_libraries(clearchoice_jni
    whisper_cpp_lib
    android # Provides JNI bindings
    log     # For __android_log_print
    # jnigraphics # Uncomment if needed for graphics operations
)
