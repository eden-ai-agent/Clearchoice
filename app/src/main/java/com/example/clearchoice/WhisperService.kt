package com.example.clearchoice

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WhisperService {

    private val audioPreprocessor = AudioPreprocessor()

    init {
        try {
            System.loadLibrary("native-lib") // Matches CMakeLists.txt
            Log.i(TAG, "Successfully loaded native-lib")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native-lib", e)
            // Handle error: inform the user, disable transcription feature, etc.
        }
    }

    private external fun transcribeFile(modelPath: String, audioPath: String): String?

    private fun getModelPath(context: Context): String? {
        val modelFile = File(context.filesDir, MODEL_NAME)
        if (modelFile.exists()) {
            Log.d(TAG, "Model already exists at: ${modelFile.absolutePath}")
            return modelFile.absolutePath
        }

        Log.d(TAG, "Model not found in internal storage. Copying from assets...")
        try {
            context.assets.open(MODEL_NAME).use { inputStream ->
                FileOutputStream(modelFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "Model copied successfully to: ${modelFile.absolutePath}")
            return modelFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy model from assets", e)
            return null
        }
    }

    fun runTranscription(
        context: Context,
        sessionFolder: File, // Used for storing temp PCM and potentially transcript
        audioFile: File,     // Input audio.mp4
        callback: (transcript: String?) -> Unit
    ) {
        Log.d(TAG, "Starting transcription process for audio: ${audioFile.name} in session: ${sessionFolder.name}")

        val modelPath = getModelPath(context)
        if (modelPath == null) {
            Log.e(TAG, "Model path is null. Aborting transcription.")
            callback(null)
            return
        }
        Log.d(TAG, "Using model at: $modelPath")

        val tempPcmFile = File(sessionFolder, TEMP_PCM_FILE_NAME)

        // Step 1: Preprocess audio (convert to PCM)
        Log.d(TAG, "Preprocessing audio...")
        val preprocessingSuccess = audioPreprocessor.preprocessAudio(context, audioFile, tempPcmFile)

        if (!preprocessingSuccess) {
            Log.e(TAG, "Audio preprocessing failed. Aborting transcription.")
            callback(null)
            // Clean up temp PCM file if it was created partially or is empty
            if (tempPcmFile.exists()) {
                tempPcmFile.delete()
            }
            return
        }
        Log.d(TAG, "Audio preprocessing succeeded. PCM at: ${tempPcmFile.absolutePath}")

        // Step 2: Call native JNI function for transcription
        var transcript: String? = null
        try {
            Log.d(TAG, "Calling native transcribeFile function...")
            transcript = transcribeFile(modelPath, tempPcmFile.absolutePath)
            Log.i(TAG, "Native transcription returned: $transcript")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method call failed (UnsatisfiedLinkError). Is native-lib loaded?", e)
            transcript = "Error: Native library link error."
        } catch (e: Exception) {
            Log.e(TAG, "Exception during native transcribeFile call", e)
            transcript = "Error: Exception during transcription native call."
        }


        // Step 3: Clean up temporary PCM file
        if (tempPcmFile.exists()) {
            if (tempPcmFile.delete()) {
                Log.d(TAG, "Temporary PCM file deleted: ${tempPcmFile.name}")
            } else {
                Log.w(TAG, "Failed to delete temporary PCM file: ${tempPcmFile.name}")
            }
        }

        // Step 4: Invoke callback with the result
        callback(transcript)
    }

    companion object {
        private const val TAG = "WhisperService"
        private const val MODEL_NAME = "ggml-tiny.en-q8.bin" // Ensure this matches the assets file
        private const val TEMP_PCM_FILE_NAME = "temp_audio.pcm"
    }
}
