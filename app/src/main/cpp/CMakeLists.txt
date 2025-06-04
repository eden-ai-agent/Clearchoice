# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.22.1 or higher.
cmake_minimum_required(VERSION 3.22.1)

# Declares and names the project.
project("clearchoice")

# Creates and names a library, sets it as SHARED, and provides its source
# files. You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        native-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        jni_bridge.cpp
        jni_diarization_bridge.cpp
        whisper_cpp/whisper.cpp
        )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries from the Android NDK,
# or prebuilt libraries with TARGET_LINK_LIBRARIES.
target_link_libraries( # Specifies the target library.
        native-lib

        # Links the target library to the log library
        # included in the NDK.
        android
        log )
