package com.example.clearchoice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SimpleWhisperActivity : AppCompatActivity() {
    private lateinit var recordButton: Button
    private lateinit var textView: TextView
    private val sampleRate = 16000
    private val recordSeconds = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView = TextView(this)
        recordButton = Button(this)
        recordButton.text = "Record & Transcribe"
        val layout = androidx.constraintlayout.widget.ConstraintLayout(this)
        layout.addView(recordButton)
        layout.addView(textView)
        setContentView(layout)

        recordButton.setOnClickListener { startRecordAndTranscribe() }
    }

    private fun startRecordAndTranscribe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            return
        }
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val buffer = ShortArray(sampleRate * recordSeconds)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
        rec.startRecording()
        rec.read(buffer, 0, buffer.size)
        rec.stop()
        rec.release()

        val floatSamples = FloatArray(buffer.size) { i -> buffer[i] / 32768f }
        val modelPath = filesDir.absolutePath + "/ggml-tiny.en-q8.bin"
        // ensure dummy model exists
        openFileOutput("ggml-tiny.en-q8.bin", MODE_PRIVATE).use { }
        val ctx = WhisperBridge.whisperInitFromFile(modelPath)
        WhisperBridge.whisperFull(ctx, floatSamples)
        val sb = StringBuilder()
        val count = WhisperBridge.whisperFullNSegments(ctx)
        for (i in 0 until count) {
            sb.append(WhisperBridge.whisperFullGetSegmentText(ctx, i)).append(" ")
        }
        WhisperBridge.whisperFree(ctx)
        textView.text = sb.toString()
    }
}
