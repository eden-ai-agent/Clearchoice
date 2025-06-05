#include <jni.h>
#include "whisper_cpp/whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_clearchoice_WhisperBridge_whisperInitFromFile(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, 0);
    struct whisper_context* ctx = whisper_init_from_file(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_clearchoice_WhisperBridge_whisperFree(JNIEnv* env, jobject thiz, jlong ctxPtr) {
    whisper_free(reinterpret_cast<struct whisper_context*>(ctxPtr));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_clearchoice_WhisperBridge_whisperFull(JNIEnv* env, jobject thiz, jlong ctxPtr, jfloatArray samples) {
    jsize len = env->GetArrayLength(samples);
    jfloat* data = env->GetFloatArrayElements(samples, nullptr);
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    int ret = whisper_full(reinterpret_cast<struct whisper_context*>(ctxPtr), params, data, len);
    env->ReleaseFloatArrayElements(samples, data, 0);
    return ret;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_clearchoice_WhisperBridge_whisperFullNSegments(JNIEnv* env, jobject thiz, jlong ctxPtr) {
    return whisper_full_n_segments(reinterpret_cast<struct whisper_context*>(ctxPtr));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_clearchoice_WhisperBridge_whisperFullGetSegmentText(JNIEnv* env, jobject thiz, jlong ctxPtr, jint index) {
    const char* txt = whisper_full_get_segment_text(reinterpret_cast<struct whisper_context*>(ctxPtr), index);
    return env->NewStringUTF(txt);
}
