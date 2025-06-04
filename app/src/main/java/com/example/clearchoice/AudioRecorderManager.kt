package com.example.clearchoice

import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorderManager {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    fun startRecording(outputFile: File) {
        if (isRecording) {
            Log.d(TAG, "Recording is already in progress.")
            return
        }

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)

            // Attempt to set higher quality sampling and bit rates
            // These might not be supported by all devices, wrap in try-catch or check capabilities
            try {
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(192000)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set custom audio sampling rate or bit rate.", e)
                // Fallback to default if custom settings fail
            }

            try {
                prepare()
                start()
                isRecording = true
                Log.d(TAG, "Recording started: ${outputFile.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "prepare() failed for MediaRecorder", e)
                releaseMediaRecorder() // Clean up
            } catch (e: IllegalStateException) {
                Log.e(TAG, "start() failed for MediaRecorder", e)
                releaseMediaRecorder() // Clean up
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            Log.d(TAG, "No recording in progress to stop.")
            return
        }

        mediaRecorder?.apply {
            try {
                stop()
                Log.d(TAG, "Recording stopped.")
            } catch (e: IllegalStateException) {
                // This can happen if stop() is called after the MediaRecorder has already been stopped or encountered an error.
                Log.e(TAG, "stop() failed for MediaRecorder, possibly already stopped or in error state.", e)
            } finally {
                releaseMediaRecorder()
            }
        }
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.apply {
            try {
                // reset() clears the configuration and state of the MediaRecorder.
                reset()
                // release() frees the resources associated with the MediaRecorder.
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error during MediaRecorder reset/release", e)
            }
        }
        mediaRecorder = null
        isRecording = false
        Log.d(TAG, "MediaRecorder released.")
    }

    fun isRecording(): Boolean {
        return isRecording
    }

    companion object {
        private const val TAG = "AudioRecorderManager"
    }
}
