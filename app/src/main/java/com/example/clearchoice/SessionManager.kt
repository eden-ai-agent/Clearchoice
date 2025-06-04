package com.example.clearchoice

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionManager {

    fun createNewSessionFolder(context: Context): File? {
        val baseDir = context.getExternalFilesDir(null)
        if (baseDir == null) {
            Log.e(TAG, "External storage not available.")
            return null
        }

        val sessionsRoot = File(baseDir, SESSIONS_DIR_NAME)
        if (!sessionsRoot.exists() && !sessionsRoot.mkdirs()) {
            Log.e(TAG, "Failed to create root sessions directory: ${sessionsRoot.absolutePath}")
            return null
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val newSessionFolder = File(sessionsRoot, timestamp)

        if (!newSessionFolder.mkdirs()) {
            Log.e(TAG, "Failed to create new session directory: ${newSessionFolder.absolutePath}")
            return null
        }

        Log.d(TAG, "Successfully created new session folder: ${newSessionFolder.absolutePath}")
        return newSessionFolder
    }

    fun createMetadataFile(sessionFolder: File, metadata: Metadata): Boolean {
        val metadataFile = File(sessionFolder, METADATA_FILE_NAME)
        try {
            val jsonObject = JSONObject()
            jsonObject.put("has_transcript", metadata.has_transcript)
            jsonObject.put("has_redacted", metadata.has_redacted)
            jsonObject.put("has_diarization", metadata.has_diarization)

            FileWriter(metadataFile).use { writer ->
                writer.write(jsonObject.toString(4)) // Indent with 4 spaces for readability
            }
            Log.d(TAG, "Metadata file created: ${metadataFile.absolutePath}")
            return true
        } catch (e: Exception) { // Catches JSONException and IOException
            Log.e(TAG, "Failed to create or write metadata file: ${metadataFile.absolutePath}", e)
            return false
        }
    }

    fun getAudioFilePath(sessionFolder: File): File {
        return File(sessionFolder, AUDIO_FILE_NAME)
    }

    companion object {
        private const val TAG = "SessionManager"
        private const val SESSIONS_DIR_NAME = "ClearChoiceSessions"
        private const val METADATA_FILE_NAME = "metadata.json"
        private const val AUDIO_FILE_NAME = "audio.mp4" // Consistent with AudioRecorderManager
    }
}
