#ifndef WHISPER_H
#define WHISPER_H

#ifdef __cplusplus
extern "C" {
#endif

#include "ggml.h"

int whisper_transcribe(struct ggml_context* ctx, const char* audio_path);

#ifdef __cplusplus
}
#endif

#endif // WHISPER_H
