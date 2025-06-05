#include <jni.h>
#include <string>
#include <vector>
#include <fstream> // For file reading
#include <sstream> // For string building for JSON
#include <android/log.h>

// Include new placeholder module headers
#include "diarization_module/vad_engine.h"
#include "diarization_module/embedding_extractor.h"
#include "diarization_module/speaker_clusterer.h"

#define TAG_DIARIZATION "JNI_DIARIZATION_BRIDGE"

// Helper function to convert jstring to const char*
static const char* jstringToChar_diarization(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return nullptr;
    return env->GetStringUTFChars(jstr, nullptr);
}

// Helper function to release const char* from jstring
static void releaseJstringChars_diarization(JNIEnv* env, jstring jstr, const char* chars) {
    if (jstr != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(jstr, chars);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_clearchoice_DiarizationService_diarizeAudio(
        JNIEnv* env,
        jobject /* this */,
        jstring audioPathJ) {

    __android_log_print(ANDROID_LOG_DEBUG, TAG_DIARIZATION, "DiarizeAudio JNI function called (refined pipeline).");

    const char* audioPath_cStr = jstringToChar_diarization(env, audioPathJ);

    if (audioPath_cStr == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG_DIARIZATION, "Audio path is null.");
        // releaseJstringChars_diarization(env, audioPathJ, audioPath_cStr); // Not needed, audioPath_cStr is already null
        return env->NewStringUTF("{\"error\": \"Audio path is null.\"}");
    }
    __android_log_print(ANDROID_LOG_INFO, TAG_DIARIZATION, "Audio Path (PCM): %s", audioPath_cStr);

    // --- 1. Read PCM Audio Data ---
    std::vector<float> pcm_audio_data;
    std::ifstream audio_file(audioPath_cStr, std::ios::binary);
    if (!audio_file.is_open()) {
        __android_log_print(ANDROID_LOG_ERROR, TAG_DIARIZATION, "Failed to open audio file: %s", audioPath_cStr);
        releaseJstringChars_diarization(env, audioPathJ, audioPath_cStr);
        return env->NewStringUTF("{\"error\": \"Failed to open PCM audio file.\"}");
    }
    // Assuming 16-bit mono PCM, convert to float
    std::vector<int16_t> pcm_s16_data;
    char buffer[2];
    while(audio_file.read(buffer, 2)) {
        int16_t sample = (static_cast<unsigned char>(buffer[1]) << 8) | static_cast<unsigned char>(buffer[0]);
        pcm_s16_data.push_back(sample);
    }
    audio_file.close();

    if (pcm_s16_data.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, TAG_DIARIZATION, "PCM audio file is empty or read failed: %s", audioPath_cStr);
        releaseJstringChars_diarization(env, audioPathJ, audioPath_cStr);
        return env->NewStringUTF("{\"error\": \"PCM audio file is empty or read failed.\"}");
    }
    pcm_audio_data.resize(pcm_s16_data.size());
    for(size_t i = 0; i < pcm_s16_data.size(); ++i) {
        pcm_audio_data[i] = static_cast<float>(pcm_s16_data[i]) / 32768.0f;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG_DIARIZATION, "Read %zu PCM samples.", pcm_audio_data.size());


    // --- 2. Simulate Diarization Pipeline ---
    const int sample_rate = 16000; // Assuming 16kHz

    void* vad_ctx = init_vad_engine();
    std::vector<SpeechSegment> speech_segments = process_audio_for_vad(vad_ctx, pcm_audio_data.data(), pcm_audio_data.size(), sample_rate);
    free_vad_engine(vad_ctx); // Free VAD context after use

    if (speech_segments.empty() && !pcm_audio_data.empty()) {
         __android_log_print(ANDROID_LOG_WARN, TAG_DIARIZATION, "VAD produced no speech segments from non-empty audio.");
    }


    void* embed_ctx = init_embedding_extractor();
    std::vector<SpeakerEmbedding> embeddings;
    embeddings.reserve(speech_segments.size()); // Pre-allocate memory

    for (const auto& segment : speech_segments) {
        // In a real implementation, you would extract the PCM data for this specific segment.
        // For this placeholder, we pass null/zero as extract_speaker_embedding is also a placeholder.
        // Example: extract sub-array from pcm_audio_data based on segment.start_ms and segment.end_ms
        // const float* segment_audio_ptr = pcm_audio_data.data() + (segment.start_ms * sample_rate / 1000);
        // size_t segment_audio_size = ((segment.end_ms - segment.start_ms) * sample_rate / 1000);
        // For placeholder, just call with dummy data:
        embeddings.push_back(extract_speaker_embedding(embed_ctx, nullptr, 0, sample_rate));
    }
    free_embedding_extractor(embed_ctx); // Free embedding context

    std::vector<DiarizedSegment> diarized_segments = cluster_speaker_embeddings(speech_segments, embeddings);
    __android_log_print(ANDROID_LOG_INFO, TAG_DIARIZATION, "Pipeline simulation complete. Got %zu diarized segments.", diarized_segments.size());


    // --- 3. Format to JSON with Character Offsets ---
    std::stringstream json_ss;
    json_ss << "[";

    // Simulate character offsets for the placeholder diarized_segments
    // Assume a mock total transcript length to divide, e.g., 200 characters.
    // This is highly artificial for the placeholder.
    int mock_total_transcript_length = 200;
    int num_placeholder_segments = diarized_segments.size();
    int char_offset_step = (num_placeholder_segments > 0) ? (mock_total_transcript_length / num_placeholder_segments) : 0;
    int current_char_offset = 0;

    for (size_t i = 0; i < diarized_segments.size(); ++i) {
        int start_char = current_char_offset;
        int end_char = current_char_offset + char_offset_step;
        if (i == diarized_segments.size() - 1) { // Ensure last segment goes to end
            end_char = mock_total_transcript_length;
        }
        // Prevent overlap if step is too small or segments too many (basic check)
        if (end_char <= start_char && i < diarized_segments.size() -1) end_char = start_char + 10;


        json_ss << "{";
        json_ss << "\"speaker_label\": \"" << diarized_segments[i].speaker_label << "\", ";
        // Keep time_ms for potential future use, but focus on char_offset for this task
        json_ss << "\"start_time_ms\": " << diarized_segments[i].segment.start_ms << ", ";
        json_ss << "\"end_time_ms\": " << diarized_segments[i].segment.end_ms << ", ";
        json_ss << "\"start_char_offset\": " << start_char << ", ";
        json_ss << "\"end_char_offset\": " << end_char;
        json_ss << "}";

        current_char_offset = end_char; // Next segment starts where this one ended

        if (i < diarized_segments.size() - 1) {
            json_ss << ", ";
        }
    }
    json_ss << "]";
    std::string result_json = json_ss.str();

    __android_log_print(ANDROID_LOG_INFO, TAG_DIARIZATION, "Resulting JSON with char_offsets: %s", result_json.c_str());

    releaseJstringChars_diarization(env, audioPathJ, audioPath_cStr);
    return env->NewStringUTF(result_json.c_str());
}
