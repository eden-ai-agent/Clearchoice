package com.example.clearchoice

object WhisperBridge {
    init {
        try {
            System.loadLibrary("native-lib")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    external fun whisperInitFromFile(modelPath: String): Long
    external fun whisperFree(ctxPtr: Long)
    external fun whisperFull(ctxPtr: Long, samples: FloatArray): Int
    external fun whisperFullNSegments(ctxPtr: Long): Int
    external fun whisperFullGetSegmentText(ctxPtr: Long, index: Int): String?
}
