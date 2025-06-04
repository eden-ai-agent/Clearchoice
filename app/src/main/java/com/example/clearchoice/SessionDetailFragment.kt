package com.example.clearchoice

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException

class SessionDetailFragment : Fragment() {

    private lateinit var textViewSessionNameDetail: TextView
    private lateinit var buttonPlayAudio: Button
    private lateinit var buttonTranscribe: Button
    private lateinit var textViewTranscriptionStatus: TextView
    private lateinit var scrollViewTranscript: ScrollView
    private lateinit var textViewTranscript: TextView
    private lateinit var buttonRedact: Button
    private lateinit var textViewRedactionStatus: TextView
    private lateinit var buttonDiarize: Button
    private lateinit var textViewDiarizationStatus: TextView
    // New UI for Export
    private lateinit var buttonExport: Button


    private var sessionFolderName: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private lateinit var sessionManager: SessionManager
    private lateinit var whisperService: WhisperService
    private lateinit var diarizationService: DiarizationService
    private lateinit var exportService: ExportService // Added
    private var currentSessionFolderFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            sessionFolderName = it.getString(ARG_SESSION_FOLDER_NAME)
        }
        sessionManager = SessionManager()
        whisperService = WhisperService()
        diarizationService = DiarizationService()
        exportService = ExportService() // Initialize ExportService
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_session_detail, container, false)
        // Initialize all views...
        textViewSessionNameDetail = view.findViewById(R.id.textViewSessionNameDetail)
        buttonPlayAudio = view.findViewById(R.id.buttonPlayAudio)
        buttonTranscribe = view.findViewById(R.id.buttonTranscribe)
        textViewTranscriptionStatus = view.findViewById(R.id.textViewTranscriptionStatus)
        scrollViewTranscript = view.findViewById(R.id.scrollViewTranscript)
        textViewTranscript = view.findViewById(R.id.textViewTranscript)
        buttonRedact = view.findViewById(R.id.buttonRedact)
        textViewRedactionStatus = view.findViewById(R.id.textViewRedactionStatus)
        buttonDiarize = view.findViewById(R.id.buttonDiarize)
        textViewDiarizationStatus = view.findViewById(R.id.textViewDiarizationStatus)
        buttonExport = view.findViewById(R.id.buttonExport) // Initialize export button

        sessionFolderName?.let { name ->
            textViewSessionNameDetail.text = "Session: $name"
            textViewTranscriptionStatus.text = "Status: Ready"
            textViewRedactionStatus.text = "Status: "
            textViewDiarizationStatus.text = "Status: "

            val baseDir = context?.getExternalFilesDir(null)
            if (baseDir != null) {
                val sessionsRoot = File(baseDir, "ClearChoiceSessions")
                currentSessionFolderFile = File(sessionsRoot, name)
                if (currentSessionFolderFile?.exists() == false) {
                    // Disable all buttons if session folder is invalid
                    listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
                } else {
                    loadAndDisplayExistingTranscript()
                }
            } else {
                listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
            }
        } ?: run {
            listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
            // Update status texts for error
        }

        buttonPlayAudio.setOnClickListener { if (isPlaying) { stopPlayback() } else { startPlayback() } }
        buttonTranscribe.setOnClickListener { handleTranscription() }
        buttonRedact.setOnClickListener { handleRedaction() }
        buttonDiarize.setOnClickListener { handleDiarization() }
        buttonExport.setOnClickListener { showExportOptionsDialog() }

        return view
    }

    private fun readMetadataForCurrentSession(): Metadata? {
        currentSessionFolderFile?.let { folder ->
            val metadataFile = File(folder, METADATA_FILE_NAME)
            if (metadataFile.exists()) {
                try {
                    val content = metadataFile.readText()
                    val json = org.json.JSONObject(content)
                    return Metadata(
                        has_transcript = json.optBoolean("has_transcript", false),
                        has_redacted = json.optBoolean("has_redacted", false),
                        has_diarization = json.optBoolean("has_diarization", false)
                    )
                } catch (e: Exception) { Log.e(TAG, "Error reading metadata file", e) }
            }
        }
        return null
    }

    private fun updateAllButtonStates(metadata: Metadata?) {
        updateRedactButtonState(metadata)
        updateDiarizeButtonState(metadata)
        updateExportButtonState(metadata)
    }

    private fun updateExportButtonState(metadata: Metadata?) {
        val safeMetadata = metadata ?: readMetadataForCurrentSession()
        buttonExport.isEnabled = safeMetadata?.has_transcript == true
    }


    private fun updateRedactButtonState(metadata: Metadata?) { /* ... unchanged ... */
        val safeMetadata = metadata ?: readMetadataForCurrentSession()
        if (safeMetadata == null) {
            buttonRedact.isEnabled = false
            textViewRedactionStatus.text = "Status: (metadata missing)"
            return
        }

        if (safeMetadata.has_transcript && !safeMetadata.has_redacted) {
            buttonRedact.isEnabled = true
            buttonRedact.text = "Redact Transcript"
            textViewRedactionStatus.text = "Status: Ready to Redact"
        } else if (safeMetadata.has_transcript && safeMetadata.has_redacted) {
            buttonRedact.isEnabled = false
            buttonRedact.text = "Redacted"
            textViewRedactionStatus.text = "Status: Redaction already done."
        } else {
            buttonRedact.isEnabled = false
            textViewRedactionStatus.text = "Status: (needs transcript first)"
        }
    }

    private fun updateDiarizeButtonState(metadata: Metadata?) { /* ... unchanged ... */
        val safeMetadata = metadata ?: readMetadataForCurrentSession()
        val audioFileExists = currentSessionFolderFile?.let { sessionManager.getAudioFilePath(it).exists() } ?: false

        if (safeMetadata == null && audioFileExists) {
             buttonDiarize.isEnabled = true
             buttonDiarize.text = "Diarize Speakers"
             textViewDiarizationStatus.text = "Status: Ready to Diarize"
        } else if (safeMetadata != null && audioFileExists && !safeMetadata.has_diarization) {
            buttonDiarize.isEnabled = true
            buttonDiarize.text = "Diarize Speakers"
            textViewDiarizationStatus.text = "Status: Ready to Diarize"
        } else if (safeMetadata != null && safeMetadata.has_diarization) {
            buttonDiarize.isEnabled = false
            buttonDiarize.text = "Diarized"
            textViewDiarizationStatus.text = "Status: Diarization already done."
        } else {
            buttonDiarize.isEnabled = false
            textViewDiarizationStatus.text = "Status: (audio file needed)"
        }
    }

    private fun loadAndDisplayExistingTranscript() { /* ... calls updateAllButtonStates ... */
        currentSessionFolderFile?.let { folder ->
            val transcriptFile = File(folder, TRANSCRIPT_FILE_NAME)
            val metadata = readMetadataForCurrentSession()

            if (transcriptFile.exists()) {
                try {
                    val transcriptText = transcriptFile.readText()
                    if (transcriptText.isNotBlank()) {
                        textViewTranscript.text = transcriptText
                        scrollViewTranscript.visibility = View.VISIBLE
                        textViewTranscriptionStatus.text = "Status: Transcript loaded."
                        buttonTranscribe.text = "Re-Transcribe"
                    } else {
                         textViewTranscriptionStatus.text = "Status: Ready (empty transcript file)."
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading transcript file", e)
                    textViewTranscriptionStatus.text = "Status: Error reading transcript."
                }
            } else {
                 textViewTranscriptionStatus.text = "Status: No transcript yet."
            }
            updateAllButtonStates(metadata)
        }
    }

    private fun showExportOptionsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_export_options, null)
        val radioGroupSource: RadioGroup = dialogView.findViewById(R.id.radioGroupSource)
        val radioOriginal: RadioButton = dialogView.findViewById(R.id.radioOriginal)
        val radioRedacted: RadioButton = dialogView.findViewById(R.id.radioRedacted)
        val radioGroupFormat: RadioGroup = dialogView.findViewById(R.id.radioGroupFormat)

        val metadata = readMetadataForCurrentSession()
        radioRedacted.isEnabled = metadata?.has_redacted == true
        if (!radioRedacted.isEnabled) { radioOriginal.isChecked = true }


        AlertDialog.Builder(requireContext())
            .setTitle("Export Options")
            .setView(dialogView)
            .setPositiveButton("Export") { dialog, _ ->
                val selectedSourceId = radioGroupSource.checkedRadioButtonId
                val selectedFormatId = radioGroupFormat.checkedRadioButtonId

                val isOriginal = selectedSourceId == R.id.radioOriginal
                val transcriptType = if (isOriginal) "original" else "redacted"

                val format = when (selectedFormatId) {
                    R.id.radioFormatJson -> "json"
                    R.id.radioFormatPdf -> "pdf"
                    else -> "txt" // Default to txt
                }

                val contentFile = if (isOriginal) {
                    File(currentSessionFolderFile!!, TRANSCRIPT_FILE_NAME)
                } else {
                    File(currentSessionFolderFile!!, REDACTED_TRANSCRIPT_FILE_NAME)
                }

                if (!contentFile.exists()) {
                    Toast.makeText(context, "Selected transcript file not found.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val transcriptContent = contentFile.readText()
                if (transcriptContent.isBlank()){
                     Toast.makeText(context, "Selected transcript content is blank.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                prepareAndShareFile(transcriptType, transcriptContent, format)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    private fun prepareAndShareFile(transcriptType: String, transcriptContent: String, format: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val sessionName = sessionFolderName ?: "UnknownSession"
            val exportFileName = "export_${sessionName}_${transcriptType}.$format"
            val tempFile = File(requireContext().cacheDir, exportFileName)
            var mimeType = "text/plain"
            var success = false

            withContext(Dispatchers.IO) {
                try {
                    when (format) {
                        "txt" -> {
                            tempFile.writeText(transcriptContent)
                            success = true
                        }
                        "json" -> {
                            val speakersFile = File(currentSessionFolderFile!!, SPEAKERS_FILE_NAME)
                            val speakersJsonContent = if (speakersFile.exists()) speakersFile.readText() else null
                            val jsonExportData = exportService.prepareJsonExport(
                                sessionName, transcriptType, transcriptContent, speakersJsonContent
                            )
                            tempFile.writeText(jsonExportData)
                            mimeType = "application/json"
                            success = true
                        }
                        "pdf" -> {
                            success = exportService.preparePdfExport(requireContext(), transcriptContent, tempFile)
                            mimeType = "application/pdf"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error preparing export file $exportFileName", e)
                }
            }

            if (success) {
                exportService.shareFile(requireContext(), tempFile, mimeType)
            } else {
                Toast.makeText(context, "Failed to prepare export file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Methods for handleDiarization, handleRedaction, handleTranscription, saveTranscriptToFile, updateMetadata, MediaPlayer are largely the same
    // ... (ensure they call updateAllButtonStates where appropriate, especially in finally blocks or after metadata changes) ...
    private fun handleDiarization() { /* ... calls updateMetadata ... */
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { /* ... */ return }
        val audioFile = sessionManager.getAudioFilePath(currentSessionFolderFile!!)
        if (!audioFile.exists()) { /* ... */ return }

        buttonDiarize.isEnabled = false; buttonDiarize.text = "Diarizing..."
        textViewDiarizationStatus.text = "Status: Diarizing..."
        // Disable other ops
        buttonTranscribe.isEnabled = false; buttonRedact.isEnabled = false; buttonExport.isEnabled = false;

        viewLifecycleOwner.lifecycleScope.launch {
            var diarizationJson: String? = null
            try {
                withContext(Dispatchers.IO) {
                    diarizationService.runDiarization(requireContext(), currentSessionFolderFile!!, audioFile) { result ->
                        diarizationJson = result
                    }
                }
                if (diarizationJson != null && diarizationJson!!.isNotBlank() && !diarizationJson!!.startsWith("Error:")) {
                    val speakersFile = File(currentSessionFolderFile!!, SPEAKERS_FILE_NAME)
                    try {
                        withContext(Dispatchers.IO) { FileWriter(speakersFile).use { it.write(diarizationJson) } }
                        textViewDiarizationStatus.text = "Status: Diarization Complete."
                        updateMetadata(hasDiarization = true)
                    } catch (e: IOException) {
                        textViewDiarizationStatus.text = "Status: Error saving results."
                        updateMetadata(hasDiarization = false)
                    }
                } else {
                    textViewDiarizationStatus.text = "Status: Diarization Failed or empty."
                    updateMetadata(hasDiarization = false)
                }
            } catch (e: Exception) {
                textViewDiarizationStatus.text = "Status: Diarization Error (Exception)."
                updateMetadata(hasDiarization = false)
            } finally {
                 updateAllButtonStates(readMetadataForCurrentSession()) // Re-enable relevant buttons
                 buttonTranscribe.isEnabled = readMetadataForCurrentSession()?.has_transcript != true // Example logic
            }
        }
    }
    private fun handleRedaction() { /* ... calls updateMetadata ... */
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { /* ... */ return }
        val transcriptFile = File(currentSessionFolderFile!!, TRANSCRIPT_FILE_NAME)
        if (!transcriptFile.exists() || transcriptFile.length() == 0L) { /* ... */ return }

        buttonRedact.isEnabled = false; buttonRedact.text = "Redacting..."
        textViewRedactionStatus.text = "Status: Redacting..."
        buttonDiarize.isEnabled = false; buttonTranscribe.isEnabled = false; buttonExport.isEnabled = false;


        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val originalTranscript = withContext(Dispatchers.IO) { transcriptFile.readText() }
                if (originalTranscript.isBlank()) { /* ... */ updateAllButtonStates(readMetadataForCurrentSession()); return@launch }
                val redactedTranscript = withContext(Dispatchers.Default) { Redactor.redact(originalTranscript) }
                val redactedFile = File(currentSessionFolderFile!!, REDACTED_TRANSCRIPT_FILE_NAME)
                withContext(Dispatchers.IO) { FileWriter(redactedFile).use { it.write(redactedTranscript) } }
                updateMetadata(hasRedacted = true)
                textViewRedactionStatus.text = "Status: Redaction Complete."
            } catch (e: Exception) {
                textViewRedactionStatus.text = "Status: Redaction Error."
            } finally {
                updateAllButtonStates(readMetadataForCurrentSession())
                buttonTranscribe.isEnabled = true // Example logic
            }
        }
    }
    private fun handleTranscription() { /* ... calls updateMetadata ... */
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { /* ... */ return }
        val audioFile = sessionManager.getAudioFilePath(currentSessionFolderFile!!)
        // ... rest of audio file check ...

        buttonTranscribe.isEnabled = false; buttonTranscribe.text = "Transcribing..."
        textViewTranscriptionStatus.text = "Status: Transcribing..."
        scrollViewTranscript.visibility = View.GONE; textViewTranscript.text = ""
        buttonRedact.isEnabled = false; textViewRedactionStatus.text = "Status: (transcription in progress)"
        buttonDiarize.isEnabled = false; textViewDiarizationStatus.text = "Status: (transcription in progress)"
        buttonExport.isEnabled = false;


        viewLifecycleOwner.lifecycleScope.launch {
            var transcriptResult: String? = null
            try {
                withContext(Dispatchers.IO) {
                    whisperService.runTranscription(requireContext(), currentSessionFolderFile!!, audioFile) { transcript ->
                        transcriptResult = transcript
                    }
                }
                if (transcriptResult != null && transcriptResult!!.isNotBlank() && !transcriptResult!!.startsWith("Error:")) {
                    saveTranscriptToFile(transcriptResult!!)
                    updateMetadata(hasTranscript = true, hasRedacted = false)
                } else {
                    updateMetadata(hasTranscript = false, hasRedacted = false, hasDiarization = readMetadataForCurrentSession()?.has_diarization ?: false)
                }
                 // UI update for transcript text and status is done within updateMetadata via loadAndDisplay
            } catch (e: Exception) {
                updateMetadata(hasTranscript = false, hasRedacted = false, hasDiarization = readMetadataForCurrentSession()?.has_diarization ?: false)
            } finally {
                buttonTranscribe.isEnabled = true; buttonTranscribe.text = "Re-Transcribe"
                loadAndDisplayExistingTranscript() // This will call updateAllButtonStates
            }
        }
    }
    private fun saveTranscriptToFile(transcript: String) { /* ... unchanged ... */
         currentSessionFolderFile?.let { folder ->
            val transcriptFile = File(folder, TRANSCRIPT_FILE_NAME)
            try { FileWriter(transcriptFile).use { it.write(transcript) }
                Log.d(TAG, "Transcript saved to ${transcriptFile.absolutePath}")
            } catch (e: IOException) { Log.e(TAG, "Failed to save transcript to file", e) }
        }
    }
    private fun updateMetadata(hasTranscript: Boolean? = null, hasRedacted: Boolean? = null, hasDiarization: Boolean? = null) { /* ... unchanged ... */
        currentSessionFolderFile?.let { folder ->
            val metadata = readMetadataForCurrentSession() ?: Metadata()

            hasTranscript?.let { metadata.has_transcript = it }
            // If a new transcript is set, previous redaction is no longer valid for this new transcript.
            if (hasTranscript == true) metadata.has_redacted = false
            hasRedacted?.let { metadata.has_redacted = it }
            hasDiarization?.let { metadata.has_diarization = it }

            val success = sessionManager.createMetadataFile(folder, metadata)
            // After metadata is updated, reload the transcript display and button states
            if(success) loadAndDisplayExistingTranscript() //This reloads transcript and calls updateAllButtonStates
            else Log.e(TAG, "Failed to update metadata file, UI might be stale.")
        }
    }

    // MediaPlayer methods
    private fun startPlayback() { /* ... unchanged ... */
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { return }
        val audioFile = sessionManager.getAudioFilePath(currentSessionFolderFile!!)
        if (!audioFile.exists()) { return }
        mediaPlayer = MediaPlayer().apply {
            try { setDataSource(audioFile.absolutePath); prepareAsync()
                setOnPreparedListener { start(); isPlaying = true; buttonPlayAudio.text = "Stop Playback" }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ -> stopPlayback(); true }
            } catch (e: IOException) { releaseMediaPlayer() }
        }
    }
    private fun stopPlayback() { /* ... unchanged ... */
        mediaPlayer?.apply { if (this.isPlaying) { try { stop() } catch (e: IllegalStateException) {} }; release() }
        mediaPlayer = null; isPlaying = false
        if (::buttonPlayAudio.isInitialized) { buttonPlayAudio.text = "Play Audio" }
    }
    private fun releaseMediaPlayer() { /* ... unchanged ... */
        mediaPlayer?.release(); mediaPlayer = null; isPlaying = false
        if (view != null && ::buttonPlayAudio.isInitialized) { buttonPlayAudio.text = "Play Audio"}
    }
    override fun onStop() { super.onStop(); if (isPlaying) { stopPlayback() } else { releaseMediaPlayer() } }

    companion object {
        private const val TAG = "SessionDetailFragment"
        private const val ARG_SESSION_FOLDER_NAME = "session_folder_name"
        private const val TRANSCRIPT_FILE_NAME = "transcript.txt"
        private const val REDACTED_TRANSCRIPT_FILE_NAME = "redacted.txt"
        private const val METADATA_FILE_NAME = "metadata.json"
        private const val SPEAKERS_FILE_NAME = "speakers.json"

        @JvmStatic
        fun newInstance(sessionFolderName: String) = SessionDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_SESSION_FOLDER_NAME, sessionFolderName) }
        }
    }
}
