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

    /**
     * Reads metadata from the metadata.json file in a given session folder.
     * @param sessionFolder The File object representing the session folder.
     * @return A Metadata object if successful, null otherwise.
     */
    fun readMetadata(sessionFolder: File): Metadata? {
        val metadataFile = File(sessionFolder, METADATA_FILE_NAME)
        if (!metadataFile.exists() || !metadataFile.canRead()) {
            Log.w(TAG, "Metadata file does not exist or cannot be read: ${metadataFile.absolutePath}")
            return null
        }
        return try {
            val content = metadataFile.readText()
            val json = JSONObject(content)
            Metadata(
                has_transcript = json.optBoolean("has_transcript", false),
                has_redacted = json.optBoolean("has_redacted", false),
                has_diarization = json.optBoolean("has_diarization", false)
            )
        } catch (e: Exception) { // Covers IOException, JSONException, etc.
            Log.e(TAG, "Error reading or parsing metadata file: ${metadataFile.absolutePath}", e)
            null
        }
    }

    /**
     * Reads the content of a specified file within a session folder.
     * @param sessionFolder The session folder.
     * @param fileName The name of the file to read (e.g., "transcript.txt").
     * @return The file content as a String, or null if an error occurs.
     */
    fun readFileContent(sessionFolder: File, fileName: String): String? {
        val fileToRead = File(sessionFolder, fileName)
        if (!fileToRead.exists() || !fileToRead.canRead()) {
            Log.w(TAG, "File does not exist or cannot be read: ${fileToRead.absolutePath}")
            return null
        }
        return try {
            fileToRead.readText()
        } catch (e: IOException) {
            Log.e(TAG, "Error reading file content: ${fileToRead.absolutePath}", e)
            null
        }
    }

    /**
     * Writes content to a specified file within a session folder.
     * @param sessionFolder The session folder.
     * @param fileName The name of the file to write to (e.g., "transcript.txt").
     * @param content The String content to write.
     * @return True if writing was successful, false otherwise.
     */
    fun writeFileContent(sessionFolder: File, fileName: String, content: String): Boolean {
        val fileToWrite = File(sessionFolder, fileName)
        return try {
            // Ensure parent directory exists (though sessionFolder should already exist)
            if (!sessionFolder.exists() && !sessionFolder.mkdirs()) {
                 Log.e(TAG, "Cannot create session folder for writing file: ${sessionFolder.absolutePath}")
                 return false
            }
            FileWriter(fileToWrite).use { writer ->
                writer.write(content)
            }
            Log.d(TAG, "Content successfully written to: ${fileToWrite.absolutePath}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error writing file content: ${fileToWrite.absolutePath}", e)
            false
        }
    }


    companion object {
        private const val TAG = "SessionManager"
        const val SESSIONS_DIR_NAME = "ClearChoiceSessions" // Made public for RecordFragment extension access
        const val METADATA_FILE_NAME = "metadata.json" // Made public
        const val AUDIO_FILE_NAME = "audio.mp4" // Consistent with AudioRecorderManager
        const val TRANSCRIPT_FILE_NAME = "transcript.txt"
        const val REDACTED_TRANSCRIPT_FILE_NAME = "redacted.txt"
        const val SPEAKERS_FILE_NAME = "speakers.json"
    }
}
