// vad_engine.cpp (placeholder)
#include "vad_engine.h"
#include <cstdio> // For printf if debugging

void* init_vad_engine() {
    printf("VAD_ENGINE (placeholder): init_vad_engine called.\n");
    // Return a dummy non-null pointer as context
    return reinterpret_cast<void*>(new char[1]); // Dummy allocation
}

std::vector<SpeechSegment> process_audio_for_vad(void* vad_ctx, const float* pcm_data, size_t pcm_data_size, int sample_rate) {
    printf("VAD_ENGINE (placeholder): process_audio_for_vad called with %zu samples at %d Hz.\n", pcm_data_size, sample_rate);
    std::vector<SpeechSegment> segments;
    if (pcm_data_size > 0) { // Only generate segments if there's data
        segments.push_back({500, 2500});    // Dummy segment 1: 0.5s to 2.5s
        segments.push_back({2800, 4500});   // Dummy segment 2: 2.8s to 4.5s
        segments.push_back({4800, 6000});   // Dummy segment 3: 4.8s to 6.0s
        segments.push_back({6200, 8000});   // Dummy segment 4: 6.2s to 8.0s
    }
    printf("VAD_ENGINE (placeholder): Produced %zu dummy speech segments.\n", segments.size());
    return segments;
}

void free_vad_engine(void* vad_ctx) {
    printf("VAD_ENGINE (placeholder): free_vad_engine called.\n");
    if (vad_ctx) {
        delete[] reinterpret_cast<char*>(vad_ctx); // Free dummy allocation
    }
}
