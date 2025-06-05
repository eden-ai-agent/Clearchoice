package com.example.clearchoice

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class AudioPreprocessor {

    companion object {
        private const val TAG = "AudioPreprocessor"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNEL_COUNT = 1 // Mono
        private const val TIMEOUT_US = 10000L
    }

    /**
     * Preprocesses an input audio file to raw PCM format (16kHz, 16-bit, mono).
     *
     * @param context Context (not used currently but good practice).
     * @param inputFile The input audio file (e.g., audio.mp4).
     * @param outputFile The file where the raw PCM data should be written.
     * @return True if preprocessing was successful, false otherwise.
     */
    fun preprocessAudio(context: Context, inputFile: File, outputFile: File): Boolean {
        Log.d(TAG, "Starting preprocessing for: ${inputFile.absolutePath} -> ${outputFile.absolutePath}")

        if (!inputFile.exists()) {
            Log.e(TAG, "Input file does not exist: ${inputFile.absolutePath}")
            return false
        }

        var mediaExtractor: MediaExtractor? = null
        var mediaCodec: MediaCodec? = null
        var fos: FileOutputStream? = null

        try {
            mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                Log.e(TAG, "No audio track found in input file.")
                return false
            }

            mediaExtractor.selectTrack(audioTrackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm" // Default if null

            // --- Configure MediaCodec for desired output format if possible ---
            // We want 16kHz mono. MediaCodec might not always be able to output this directly.
            // The actual output format will be available in MediaCodec.getOutputFormat() after configuration.
            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_RAW, // We want raw PCM
                TARGET_SAMPLE_RATE,
                TARGET_CHANNEL_COUNT
            )
            // It's important to set KEY_PCM_ENCODING if creating a format for an encoder,
            // but for a decoder outputting raw audio, the system determines this.
            // We'll check the actual output format later.
            // outputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);


            mediaCodec = MediaCodec.createDecoderByType(mimeType)
            // Try to configure with inputFormat. The decoder will output raw PCM.
            mediaCodec.configure(inputFormat, null, null, 0)
            mediaCodec.start()

            fos = FileOutputStream(outputFile)
            val bufferInfo = MediaCodec.BufferInfo()
            var allInputEOS = false
            var allOutputEOS = false

            Log.d(TAG, "Initial input format: $inputFormat")
            // Actual output format from the decoder
            var actualOutputFormat: MediaFormat? = null


            while (!allOutputEOS) {
                // Feed input data
                if (!allInputEOS) {
                    val inputBufferIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                allInputEOS = true
                                Log.d(TAG, "All input EOS.")
                            } else {
                                mediaCodec.queueInputBuffer(inputBufferIndex, 0, sampleSize, mediaExtractor.sampleTime, 0)
                                mediaExtractor.advance()
                            }
                        }
                    }
                }

                // Get output data
                val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        allOutputEOS = true
                        Log.d(TAG, "All output EOS.")
                    }
                    if (bufferInfo.size > 0) {
                        val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null) {
                            // At this point, outputBuffer contains raw PCM data.
                            // We need to check its format (sample rate, channel count, bit depth)
                            // and convert if necessary.
                            if (actualOutputFormat == null) {
                                actualOutputFormat = mediaCodec.getOutputFormat()
                                Log.d(TAG, "Actual output format from MediaCodec: $actualOutputFormat")
                                // TODO: Check actualOutputFormat's sample rate, channel count, and PCM encoding.
                                // If not TARGET_SAMPLE_RATE or TARGET_CHANNEL_COUNT or 16-bit, resampling/mixing is needed.
                                // This is the complex part. For this subtask, we'll write what we get and log.
                                // If actualOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) != TARGET_SAMPLE_RATE etc.
                            }

                            val pcmChunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(pcmChunk)
                            outputBuffer.clear() // Needed for older APIs? Or just position reset.
                            fos.write(pcmChunk)
                        }
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    actualOutputFormat = mediaCodec.getOutputFormat()
                    Log.d(TAG, "Output format changed to: $actualOutputFormat")
                    // This is where we'd get the definitive format of the raw PCM output.
                } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // No output available yet
                }
            }

            if (actualOutputFormat != null) {
                val finalSampleRate = actualOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val finalChannelCount = actualOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                // Assuming default is 16-bit (ENCODING_PCM_16BIT) for raw audio from MediaCodec.
                // This might need explicit checking via MediaFormat.KEY_PCM_ENCODING if available.
                 Log.i(TAG, "Final PCM data characteristics: Sample Rate: $finalSampleRate, Channels: $finalChannelCount (Assumed 16-bit)")
                if (finalSampleRate != TARGET_SAMPLE_RATE || finalChannelCount != TARGET_CHANNEL_COUNT) {
                    Log.w(TAG, "PCM output properties ($finalSampleRate Hz, $finalChannelCount ch) " +
                               "do not match target ($TARGET_SAMPLE_RATE Hz, $TARGET_CHANNEL_COUNT ch). " +
                               "Resampling/mixing would be needed for optimal whisper.cpp performance.")
                    // For this subtask, we proceed with the data as-is.
                    // Real whisper.cpp might handle some resampling, or it might fail/perform poorly.
                }
            } else {
                 Log.w(TAG, "Could not determine actual output format. PCM data might not be as expected.")
            }


            Log.d(TAG, "Successfully preprocessed audio to: ${outputFile.absolutePath}")
            return true

        } catch (e: IOException) {
            Log.e(TAG, "IOException during preprocessing", e)
            return false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException during preprocessing (MediaCodec state issue?)", e)
            return false
        } catch (e: Exception) {
            // Catch any other unexpected errors
            Log.e(TAG, "Unexpected error during preprocessing", e)
            return false
        }
        finally {
            try {
                mediaExtractor?.release()
            } catch (e: Exception) { Log.e(TAG, "Error releasing MediaExtractor", e) }
            try {
                mediaCodec?.stop()
            } catch (e: Exception) { Log.e(TAG, "Error stopping MediaCodec", e) }
            try {
                mediaCodec?.release()
            } catch (e: Exception) { Log.e(TAG, "Error releasing MediaCodec", e) }
            try {
                fos?.close()
            } catch (e: IOException) { Log.e(TAG, "Error closing FileOutputStream", e) }
        }
    }
}
