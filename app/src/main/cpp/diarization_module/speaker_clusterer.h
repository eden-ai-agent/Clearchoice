// speaker_clusterer.h (placeholder)
#pragma once
#include <vector>
#include <string>
#include "embedding_extractor.h" // For SpeakerEmbedding
#include "vad_engine.h" // For SpeechSegment

#ifndef DIARIZED_SEGMENT_STRUCT_DEFINED
#define DIARIZED_SEGMENT_STRUCT_DEFINED
struct DiarizedSegment {
    SpeechSegment segment;
    std::string speaker_label;
};
#endif

// Takes non-const vectors as it might modify them or use them to build internal state if it were real
std::vector<DiarizedSegment> cluster_speaker_embeddings(
    const std::vector<SpeechSegment>& segments,
    const std::vector<SpeakerEmbedding>& embeddings);
