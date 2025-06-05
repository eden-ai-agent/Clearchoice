package com.example.clearchoice

import android.content.Context
import android.util.Log
import java.io.File

class DiarizationService {

    init {
        try {
            System.loadLibrary("native-lib") // Assumes jni_diarization_bridge.cpp is part of native-lib
            Log.i(TAG, "Successfully loaded native-lib (for DiarizationService)")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native-lib (for DiarizationService)", e)
            // Handle error: inform user, disable feature, etc.
        }
    }

    /**
     * Calls the native JNI function to perform speaker diarization.
     *
     * @param audioPath The absolute path to the audio file to be diarized.
     * @return A JSON string representing speaker segments, or null/error string on failure.
     */
    private external fun diarizeAudio(audioPath: String): String?

    /**
     * Orchestrates the diarization process.
     * For this placeholder version, it directly calls the native method.
     * A real implementation might involve audio preprocessing if the native engine requires a specific format.
     *
     * @param context The application context.
     * @param sessionFolder The folder where output files (like speakers.json) might be stored.
     * @param audioFile The input audio file (e.g., audio.mp4).
     * @param callback Invoked with the diarization result (JSON string) or null on failure.
     */
    fun runDiarization(
        context: Context, // Not used in placeholder but good for consistency
        sessionFolder: File, // Not used directly by native placeholder but good for consistency
        audioFile: File,
        callback: (diarizationJson: String?) -> Unit
    ) {
        Log.d(TAG, "Starting diarization process for audio: ${audioFile.name}")

        // Step 1: (Optional) Audio Preprocessing
        // If the native diarization engine required a specific audio format (e.g., raw PCM),
        // preprocessing would happen here, similar to WhisperService.
        // For this placeholder, we assume the native function can handle the input audioPath directly
        // or that it's a placeholder that doesn't actually process audio.
        Log.d(TAG, "Audio preprocessing for diarization skipped for placeholder.")

        // Step 2: Call native JNI function for diarization
        var diarizationResult: String? = null
        try {
            Log.d(TAG, "Calling native diarizeAudio function with path: ${audioFile.absolutePath}")
            diarizationResult = diarizeAudio(audioFile.absolutePath)
            Log.i(TAG, "Native diarization returned: $diarizationResult")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method call failed (UnsatisfiedLinkError). Is native-lib loaded and function signature correct?", e)
            diarizationResult = "Error: Native library link error for diarization."
        } catch (e: Exception) {
            Log.e(TAG, "Exception during native diarizeAudio call", e)
            diarizationResult = "Error: Exception during diarization native call."
        }

        // Step 3: (Optional) Clean up temporary files if any were created.
        // Not applicable for this placeholder.

        // Step 4: Invoke callback with the result
        callback(diarizationResult)
    }

    companion object {
        private const val TAG = "DiarizationService"
    }
}
