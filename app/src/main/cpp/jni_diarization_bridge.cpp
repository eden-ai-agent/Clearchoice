#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG_DIARIZATION "JNI_DIARIZATION_BRIDGE"

// Helper function to convert jstring to const char* (can be shared if in a common header)
static const char* jstringToChar_diarization(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) {
        return nullptr;
    }
    return env->GetStringUTFChars(jstr, nullptr);
}

// Helper function to release const char* from jstring (can be shared)
static void releaseJstringChars_diarization(JNIEnv* env, jstring jstr, const char* chars) {
    if (jstr != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(jstr, chars);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_clearchoice_DiarizationService_diarizeAudio(
        JNIEnv* env,
        jobject /* this */,
        jstring audioPath) {

    __android_log_print(ANDROID_LOG_DEBUG, TAG_DIARIZATION, "DiarizeAudio JNI function called.");

    const char* audioPath_cStr = jstringToChar_diarization(env, audioPath);

    if (audioPath_cStr == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG_DIARIZATION, "Audio path is null.");
        releaseJstringChars_diarization(env, audioPath, audioPath_cStr);
        return env->NewStringUTF("Error: Audio path is null.");
    }

    __android_log_print(ANDROID_LOG_INFO, TAG_DIARIZATION, "Audio Path for Diarization: %s", audioPath_cStr);

    // Placeholder Diarization Logic:
    // In a real scenario, this would involve complex audio processing and speaker diarization algorithms.
    // Here, we just simulate success and return a fixed JSON string.

    const char* placeholderJson = R"([
        {"speaker_label": "SPEAKER_00", "start_time_ms": 500, "end_time_ms": 2500},
        {"speaker_label": "SPEAKER_01", "start_time_ms": 2800, "end_time_ms": 4500},
        {"speaker_label": "SPEAKER_00", "start_time_ms": 4800, "end_time_ms": 6000},
        {"speaker_label": "SPEAKER_01", "start_time_ms": 6200, "end_time_ms": 8000}
    ])";

    __android_log_print(ANDROID_LOG_INFO, TAG_DIARIZATION, "Returning placeholder diarization JSON.");

    releaseJstringChars_diarization(env, audioPath, audioPath_cStr);

    return env->NewStringUTF(placeholderJson);
}
