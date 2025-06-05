#include "whisper.h"
#include <iostream>

int whisper_transcribe(struct ggml_context* ctx, const char* audio_path) {
    std::cout << "Transcribing " << audio_path << "..." << std::endl;
    return 0;
}
