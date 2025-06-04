#include <jni.h>
#include <string>
#include <android/log.h> // For __android_log_print
#include "whisper_cpp/whisper.h"

#define TAG "JNI_BRIDGE"

// Helper function to convert jstring to const char*
const char* jstringToChar(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) {
        return nullptr;
    }
    return env->GetStringUTFChars(jstr, nullptr);
}

// Helper function to release const char* from jstring
void releaseJstringChars(JNIEnv* env, jstring jstr, const char* chars) {
    if (jstr != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(jstr, chars);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_clearchoice_WhisperService_transcribeFile(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jstring audioPath) {

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "TranscribeFile JNI function called.");

    const char* modelPath_cStr = jstringToChar(env, modelPath);
    const char* audioPath_cStr = jstringToChar(env, audioPath);

    if (modelPath_cStr == nullptr || audioPath_cStr == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Model path or audio path is null.");
        releaseJstringChars(env, modelPath, modelPath_cStr);
        releaseJstringChars(env, audioPath, audioPath_cStr);
        return env->NewStringUTF("Error: Model or audio path is null.");
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Model Path: %s", modelPath_cStr);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Audio Path: %s", audioPath_cStr);

    struct whisper_context *ctx = whisper_init_from_file(modelPath_cStr);
    if (ctx == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to initialize whisper context.");
        releaseJstringChars(env, modelPath, modelPath_cStr);
        releaseJstringChars(env, audioPath, audioPath_cStr);
        return env->NewStringUTF("Error: Failed to initialize whisper context.");
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "Whisper context initialized.");

    // In a real scenario, you'd load audio data and pass it to whisper_full.
    // The placeholder whisper_full takes audioPath_cStr and a buffer.
    // We'll use a simple buffer for the placeholder.
    char output_buffer[1024]; // Example buffer size
    int result = whisper_full(ctx, audioPath_cStr, output_buffer, sizeof(output_buffer));

    std::string full_transcript;

    if (result == 0) {
        // This part is for the placeholder; real whisper.cpp might structure this differently.
        // Using the placeholder's segment functions
        int n_segments = whisper_full_n_segments(ctx);
        __android_log_print(ANDROID_LOG_INFO, TAG, "Number of segments: %d", n_segments);
        for (int i = 0; i < n_segments; ++i) {
            const char *segment_text = whisper_full_get_segment_text(ctx, i);
            if (segment_text) {
                full_transcript += segment_text;
                // Add a space or newline if multiple segments (not for placeholder)
            }
        }
        if (n_segments == 0 && output_buffer[0] != '\0') {
             // Fallback for the very simple whisper_full placeholder that directly writes to output_buffer
            full_transcript = output_buffer;
        }
         __android_log_print(ANDROID_LOG_INFO, TAG, "Transcription result: %s", full_transcript.c_str());
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Whisper_full failed with code: %d", result);
        full_transcript = "Error: Transcription failed.";
    }

    whisper_free(ctx);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Whisper context freed.");

    releaseJstringChars(env, modelPath, modelPath_cStr);
    releaseJstringChars(env, audioPath, audioPath_cStr);

    return env->NewStringUTF(full_transcript.c_str());
}
