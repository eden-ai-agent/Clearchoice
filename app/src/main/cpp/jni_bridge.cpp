#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <fstream> // For file reading
#include <sstream> // For string building

#include "whisper_cpp/whisper.h"

#define TAG "JNI_BRIDGE"

// Helper function to convert jstring to const char*
static const char* jstringToChar(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return nullptr;
    return env->GetStringUTFChars(jstr, nullptr);
}

// Helper function to release const char* from jstring
static void releaseJstringChars(JNIEnv* env, jstring jstr, const char* chars) {
    if (jstr != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(jstr, chars);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_clearchoice_WhisperService_transcribeFile(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPathJ,
        jstring audioPathJ) {

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "TranscribeFile JNI function called (refined).");

    const char* modelPath_cStr = jstringToChar(env, modelPathJ);
    const char* audioPath_cStr = jstringToChar(env, audioPathJ);

    if (modelPath_cStr == nullptr || audioPath_cStr == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Model path or audio path is null.");
        releaseJstringChars(env, modelPathJ, modelPath_cStr);
        releaseJstringChars(env, audioPathJ, audioPath_cStr);
        // Consider returning nullptr or a specific error string that Kotlin can check
        return env->NewStringUTF("ERROR: JNI received null model or audio path.");
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Model Path: %s", modelPath_cStr);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Audio Path (PCM): %s", audioPath_cStr);

    // --- 1. Initialize whisper context ---
    // Using whisper_init_from_file as the params version is more complex for a placeholder
    struct whisper_context *ctx = whisper_init_from_file(modelPath_cStr);
    if (ctx == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to initialize whisper context.");
        releaseJstringChars(env, modelPathJ, modelPath_cStr);
        releaseJstringChars(env, audioPathJ, audioPath_cStr);
        return env->NewStringUTF("ERROR: whisper_init_from_file failed.");
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "Whisper context initialized.");

    // --- 2. Read Audio Data (PCM 16-bit mono -> float vector) ---
    std::vector<float> pcm_data_f32;
    std::ifstream audio_file(audioPath_cStr, std::ios::binary);
    if (!audio_file.is_open()) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to open audio file: %s", audioPath_cStr);
        whisper_free(ctx);
        releaseJstringChars(env, modelPathJ, modelPath_cStr);
        releaseJstringChars(env, audioPathJ, audioPath_cStr);
        return env->NewStringUTF("ERROR: Failed to open PCM audio file.");
    }

    // Read 16-bit PCM samples and convert to float
    // This assumes the input file is indeed 16-bit mono PCM.
    std::vector<int16_t> pcm_data_s16;
    char buffer[2]; // 2 bytes for int16_t
    while (audio_file.read(buffer, 2)) {
        int16_t sample = (static_cast<unsigned char>(buffer[1]) << 8) | static_cast<unsigned char>(buffer[0]);
        pcm_data_s16.push_back(sample);
    }
    audio_file.close();

    if (pcm_data_s16.empty()) {
         __android_log_print(ANDROID_LOG_ERROR, TAG, "Audio file was empty or failed to read: %s", audioPath_cStr);
        whisper_free(ctx);
        releaseJstringChars(env, modelPathJ, modelPath_cStr);
        releaseJstringChars(env, audioPathJ, audioPath_cStr);
        return env->NewStringUTF("ERROR: PCM audio file is empty or read failed.");
    }

    pcm_data_f32.resize(pcm_data_s16.size());
    for (size_t i = 0; i < pcm_data_s16.size(); ++i) {
        pcm_data_f32[i] = static_cast<float>(pcm_data_s16[i]) / 32768.0f;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "Read %zu PCM samples, converted to float.", pcm_data_f32.size());


    // --- 3. Set whisper_full_params ---
    // Using greedy sampling strategy
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false; // Disable verbose progress from C++ side in release
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false; // Timestamps can be extracted per segment if needed
    params.language = "en"; // For tiny.en model
    params.n_threads = 4; // Example, adjust based on device capabilities
    // params.translate = false; // Default is false
    // params.no_context = true; // If processing independent chunks

    __android_log_print(ANDROID_LOG_INFO, TAG, "Whisper params set. Language: %s, Threads: %d", params.language, params.n_threads);

    // --- 4. Run Transcription ---
    __android_log_print(ANDROID_LOG_INFO, TAG, "Calling whisper_full...");
    int whisper_result = whisper_full(ctx, params, pcm_data_f32.data(), pcm_data_f32.size());
    if (whisper_result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full failed with code: %d", whisper_result);
        whisper_free(ctx);
        releaseJstringChars(env, modelPathJ, modelPath_cStr);
        releaseJstringChars(env, audioPathJ, audioPath_cStr);
        std::string error_msg = "ERROR: whisper_full failed, code: " + std::to_string(whisper_result);
        return env->NewStringUTF(error_msg.c_str());
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "whisper_full completed successfully.");

    // --- 5. Extract Results ---
    std::stringstream full_transcript_ss;
    int n_segments = whisper_full_n_segments(ctx);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Number of segments: %d", n_segments);
    for (int i = 0; i < n_segments; ++i) {
        const char *segment_text = whisper_full_get_segment_text(ctx, i);
        if (segment_text) {
            full_transcript_ss << segment_text;
            // Add a space or newline if desired, but for plain text, direct concatenation is fine.
            // if (i < n_segments - 1) full_transcript_ss << " ";
        } else {
             __android_log_print(ANDROID_LOG_WARN, TAG, "Segment %d text is null.", i);
        }
    }
    std::string full_transcript = full_transcript_ss.str();
    __android_log_print(ANDROID_LOG_INFO, TAG, "Full transcript extracted: %s", full_transcript.c_str());


    // --- 6. Cleanup ---
    whisper_free(ctx);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Whisper context freed.");

    releaseJstringChars(env, modelPathJ, modelPath_cStr);
    releaseJstringChars(env, audioPathJ, audioPath_cStr);

    if (full_transcript.empty() && n_segments > 0) {
         __android_log_print(ANDROID_LOG_WARN, TAG, "Transcription resulted in empty string despite segments present.");
        // Potentially return a specific indicator or let it be an empty string.
    }

    return env->NewStringUTF(full_transcript.c_str());
}
