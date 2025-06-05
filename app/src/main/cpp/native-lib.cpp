#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h> // For logging

// Make sure this path is correct relative to this file's location
// or that CMake include paths are set up correctly.
#include "whisper/whisper.h"

#define LOG_TAG "ClearChoiceJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Helper function to convert jstring to std::string
std::string jstringToStdString(JNIEnv *env, jstring jStr) {
    if (!jStr) return "";
    const jclass stringClass = env->GetObjectClass(jStr);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    const jbyteArray stringJbytes = (jbyteArray) env->CallObjectMethod(jStr, getBytes, env->NewStringUTF("UTF-8"));

    size_t length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte* pBytes = env->GetByteArrayElements(stringJbytes, NULL);

    std::string ret = std::string((char *)pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);

    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);
    return ret;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_clearchoice_MainActivity_nativeTranscribe(
        JNIEnv* env,
        jobject /* this */,
        jstring audioFilePathJStr) {

    std::string audioFilePath = jstringToStdString(env, audioFilePathJStr);
    LOGI("Native transcribe called with audio file: %s", audioFilePath.c_str());

    // --- Whisper.cpp Initialization ---
    // NOTE: This is a placeholder. A real model path is needed.
    // The model should be bundled with the app or downloaded.
    // For now, we'll simulate a very basic context initialization.
    // We are also not loading a model here, which is essential for actual transcription.

    // Example: Load model path (this needs to be a valid path in the app's accessible storage)
    // const char* model_path = "/path/to/your/ggml-model.bin";
    // struct whisper_context_params cparams = whisper_context_default_params();
    // struct whisper_context * ctx = whisper_init_from_file_with_params(model_path, cparams);

    // For demonstration, let's assume whisper_init_context is enough to check linking,
    // though it won't be functional for transcription without a model.
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context * ctx = whisper_init_context(&cparams);


    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper context.");
        std::string errorMsg = "Error: Failed to initialize whisper context. Model may be missing or invalid.";
        return env->NewStringUTF(errorMsg.c_str());
    }

    LOGI("Whisper context initialized (mock).");

    // --- Placeholder for actual transcription logic ---
    // In a real implementation, you would:
    // 1. Load audio data using the audioFilePath.
    // 2. Convert audio to the format whisper.cpp expects (16-bit PCM mono, 16kHz).
    // 3. Run whisper_full() or similar functions.
    // 4. Get the transcribed text segments.

    std::string resultText = "Transcription placeholder for: " + audioFilePath;
    LOGI("Transcription result: %s", resultText.c_str());

    // --- Cleanup ---
    whisper_free(ctx);
    LOGI("Whisper context freed.");

    return env->NewStringUTF(resultText.c_str());
}
