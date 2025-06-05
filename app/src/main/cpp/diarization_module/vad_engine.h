// vad_engine.h (placeholder)
#pragma once
#include <vector>
#include <cstdint>

// Define SpeechSegment in a way that's accessible if other headers include this one first.
#ifndef SPEECH_SEGMENT_STRUCT_DEFINED
#define SPEECH_SEGMENT_STRUCT_DEFINED
struct SpeechSegment {
    int64_t start_ms;
    int64_t end_ms;
};
#endif

void* init_vad_engine(); // Returns dummy context
std::vector<SpeechSegment> process_audio_for_vad(void* vad_ctx, const float* pcm_data, size_t pcm_data_size, int sample_rate);
void free_vad_engine(void* vad_ctx);
