// embedding_extractor.h (placeholder)
#pragma once
#include <vector>
#include <cstdint>
#include "vad_engine.h" // For SpeechSegment

#ifndef SPEAKER_EMBEDDING_STRUCT_DEFINED
#define SPEAKER_EMBEDDING_STRUCT_DEFINED
struct SpeakerEmbedding {
    std::vector<float> embedding;
    // Add a default constructor or ensure embedding is initialized if needed by vector push_back
    SpeakerEmbedding() : embedding() {}
};
#endif


void* init_embedding_extractor(); // Returns dummy context
SpeakerEmbedding extract_speaker_embedding(void* embed_ctx, const float* segment_pcm, size_t segment_pcm_size, int sample_rate);
void free_embedding_extractor(void* embed_ctx);
