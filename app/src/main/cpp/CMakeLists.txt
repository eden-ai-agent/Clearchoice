cmake_minimum_required(VERSION 3.10)
project(ClearChoiceWhisper)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Add sources
set(SOURCES
    ggml.c
    whisper.cpp
)

# Include headers
include_directories(${CMAKE_SOURCE_DIR})

# Create executable (or library if you're integrating elsewhere)
add_library(clearchoice_whisper STATIC ${SOURCES})
