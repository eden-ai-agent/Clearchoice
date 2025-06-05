# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.22.1 or higher.
cmake_minimum_required(VERSION 3.22.1)

# Declares and names the project.
project("clearchoice")

# Output compiled libraries to jniLibs so that Gradle can package them
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Creates and names a library, sets it as SHARED, and provides its source
# files. You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

# First build whisper.cpp and ggml.c into a separate shared library
add_library(
        whisper
        SHARED
        whisper_cpp/whisper.cpp
        whisper_cpp/ggml.c
)

# JNI glue and other native code are compiled into native-lib and linked
# against the whisper library above
add_library(
        native-lib
        SHARED
        native-lib.cpp
        jni_bridge.cpp
        jni_diarization_bridge.cpp
        # Add diarization module sources
        diarization_module/vad_engine.cpp
        diarization_module/embedding_extractor.cpp
        diarization_module/speaker_clusterer.cpp
)

# Prevent accidental inclusion of standalone entry points.
set_source_files_properties(main.cpp PROPERTIES HEADER_FILE_ONLY TRUE)

# Include directories for both libraries
target_include_directories(whisper PUBLIC
    ${CMAKE_SOURCE_DIR}/whisper_cpp
)

# Include directories
target_include_directories(native-lib PRIVATE
    ${CMAKE_SOURCE_DIR}/whisper_cpp
    ${CMAKE_SOURCE_DIR}/diarization_module
)

# Add compile definitions
target_compile_definitions(whisper PRIVATE WHISPER_BUILD=1 GGML_USE_K_QUANTS=1)
target_compile_definitions(native-lib PRIVATE)

# Optional: NEON flags for ARM (Uncomment if building for ARM and testing performance)
# if(ANDROID_ABI MATCHES "armeabi-v7a")
#    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon -mfloat-abi=softfp")
#    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon -mfloat-abi=softfp")
# elseif(ANDROID_ABI MATCHES "arm64-v8a")
#    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -flax-vector-conversions") # For ARM64, Neon is often default or implied by architecture.
#    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -flax-vector-conversions")
# endif()


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries from the Android NDK,
# or prebuilt libraries with TARGET_LINK_LIBRARIES.
target_link_libraries(
        native-lib
        whisper
        android
        log
)
