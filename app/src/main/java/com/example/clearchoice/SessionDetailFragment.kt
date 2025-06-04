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
    private lateinit var buttonExport: Button


    private var sessionFolderName: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private lateinit var sessionManager: SessionManager
    private lateinit var whisperService: WhisperService
    private lateinit var diarizationService: DiarizationService
    private lateinit var exportService: ExportService
    private var currentSessionFolderFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            sessionFolderName = it.getString(ARG_SESSION_FOLDER_NAME)
        }
        sessionManager = SessionManager()
        whisperService = WhisperService()
        diarizationService = DiarizationService()
        exportService = ExportService()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_session_detail, container, false)
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
        buttonExport = view.findViewById(R.id.buttonExport)

        sessionFolderName?.let { name ->
            textViewSessionNameDetail.text = getString(R.string.session_detail_title_prefix) + name
            textViewTranscriptionStatus.text = getString(R.string.session_detail_transcription_status_ready)
            textViewRedactionStatus.text = "Status: "
            textViewDiarizationStatus.text = "Status: "

            val baseDir = context?.getExternalFilesDir(null)
            if (baseDir != null) {
                val sessionsRoot = File(baseDir, "ClearChoiceSessions")
                currentSessionFolderFile = File(sessionsRoot, name)
                if (currentSessionFolderFile?.exists() == false) {
                    Log.e(TAG, "Session folder does not exist: ${currentSessionFolderFile?.absolutePath}")
                    Toast.makeText(context, getString(R.string.session_detail_error_not_found), Toast.LENGTH_LONG).show()
                    listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
                } else {
                    loadAndDisplayExistingTranscript()
                }
            } else {
                Log.e(TAG, "External storage not available to access session folder.")
                Toast.makeText(context, getString(R.string.session_detail_error_storage_not_available), Toast.LENGTH_LONG).show()
                listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
            }
        } ?: run {
            textViewSessionNameDetail.text = getString(R.string.session_detail_error_no_name)
            listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
            textViewTranscriptionStatus.text = getString(R.string.session_detail_transcription_status_ready) // Default
            textViewRedactionStatus.text = ""
            textViewDiarizationStatus.text = ""
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
        // Update transcribe button text based on transcript existence
        val safeMetadata = metadata ?: readMetadataForCurrentSession()
        if (safeMetadata?.has_transcript == true) {
            buttonTranscribe.text = getString(R.string.session_detail_retranscribe_button)
        } else {
            buttonTranscribe.text = getString(R.string.session_detail_transcribe_button)
        }
    }

    private fun updateExportButtonState(metadata: Metadata?) {
        val safeMetadata = metadata ?: readMetadataForCurrentSession()
        buttonExport.isEnabled = safeMetadata?.has_transcript == true
        if(buttonExport.isEnabled) {
            buttonExport.text = getString(R.string.session_detail_export_button)
        }
    }

    private fun updateRedactButtonState(metadata: Metadata?) {
        val safeMetadata = metadata ?: readMetadataForCurrentSession()
        if (safeMetadata == null) {
            buttonRedact.isEnabled = false
            textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_missing_metadata)
            return
        }

        if (safeMetadata.has_transcript && !safeMetadata.has_redacted) {
            buttonRedact.isEnabled = true
            buttonRedact.text = getString(R.string.session_detail_redact_button)
            textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_ready)
        } else if (safeMetadata.has_transcript && safeMetadata.has_redacted) {
            buttonRedact.isEnabled = false
            buttonRedact.text = getString(R.string.session_detail_redacted_button)
            textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_done)
        } else {
            buttonRedact.isEnabled = false
            textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_needs_transcript)
        }
    }

    private fun updateDiarizeButtonState(metadata: Metadata?) {
        val safeMetadata = metadata ?: readMetadataForCurrentSession()
        val audioFileExists = currentSessionFolderFile?.let { sessionManager.getAudioFilePath(it).exists() } ?: false

        if (safeMetadata == null && audioFileExists) {
             buttonDiarize.isEnabled = true
             buttonDiarize.text = getString(R.string.session_detail_diarize_button)
             textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_ready)
        } else if (safeMetadata != null && audioFileExists && !safeMetadata.has_diarization) {
            buttonDiarize.isEnabled = true
            buttonDiarize.text = getString(R.string.session_detail_diarize_button)
            textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_ready)
        } else if (safeMetadata != null && safeMetadata.has_diarization) {
            buttonDiarize.isEnabled = false
            buttonDiarize.text = getString(R.string.session_detail_diarized_button)
            textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_done)
        } else {
            buttonDiarize.isEnabled = false
            textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_audio_needed)
        }
    }

    private fun loadAndDisplayExistingTranscript() {
        currentSessionFolderFile?.let { folder ->
            val transcriptFile = File(folder, TRANSCRIPT_FILE_NAME)
            val metadata = readMetadataForCurrentSession()

            if (transcriptFile.exists()) {
                try {
                    val transcriptText = transcriptFile.readText()
                    if (transcriptText.isNotBlank()) {
                        textViewTranscript.text = transcriptText
                        scrollViewTranscript.visibility = View.VISIBLE
                        textViewTranscriptionStatus.text = getString(R.string.session_detail_transcript_loaded)
                    } else {
                         textViewTranscriptionStatus.text = getString(R.string.session_detail_transcription_status_empty_file)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading transcript file", e)
                    textViewTranscriptionStatus.text = getString(R.string.session_detail_transcription_status_error_reading)
                }
            } else {
                 textViewTranscriptionStatus.text = getString(R.string.session_detail_transcription_status_no_transcript)
                 scrollViewTranscript.visibility = View.GONE
                 textViewTranscript.text = ""
            }
            updateAllButtonStates(metadata)
        }
    }

    private fun showExportOptionsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_export_options, null)
        val radioGroupSource: RadioGroup = dialogView.findViewById(R.id.radioGroupSource)
        val radioOriginal: RadioButton = dialogView.findViewById(R.id.radioOriginal)
        val radioRedacted: RadioButton = dialogView.findViewById(R.id.radioRedacted)
        // Set texts from strings.xml for dialog
        radioOriginal.text = getString(R.string.session_detail_export_source_original)
        radioRedacted.text = getString(R.string.session_detail_export_source_redacted)
        dialogView.findViewById<TextView>(R.id.textViewDialogExportSourceLabel).text = getString(R.string.session_detail_export_source_label)
        dialogView.findViewById<TextView>(R.id.textViewDialogExportFormatLabel).text = getString(R.string.session_detail_export_format_label)
        dialogView.findViewById<RadioButton>(R.id.radioFormatTxt).text = getString(R.string.session_detail_export_format_txt)
        dialogView.findViewById<RadioButton>(R.id.radioFormatJson).text = getString(R.string.session_detail_export_format_json)
        dialogView.findViewById<RadioButton>(R.id.radioFormatPdf).text = getString(R.string.session_detail_export_format_pdf)


        val metadata = readMetadataForCurrentSession()
        radioRedacted.isEnabled = metadata?.has_redacted == true
        if (!radioRedacted.isEnabled) { radioOriginal.isChecked = true }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.session_detail_export_options_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.session_detail_export_action)) { dialog, _ ->
                val selectedSourceId = radioGroupSource.checkedRadioButtonId
                val selectedFormatId = dialogView.findViewById<RadioGroup>(R.id.radioGroupFormat).checkedRadioButtonId

                val isOriginal = selectedSourceId == R.id.radioOriginal
                val transcriptType = if (isOriginal) "original" else "redacted"

                val format = when (selectedFormatId) {
                    R.id.radioFormatJson -> "json"
                    R.id.radioFormatPdf -> "pdf"
                    else -> "txt"
                }

                val contentFile = if (isOriginal) { File(currentSessionFolderFile!!, TRANSCRIPT_FILE_NAME) }
                                  else { File(currentSessionFolderFile!!, REDACTED_TRANSCRIPT_FILE_NAME) }

                if (!contentFile.exists()) {
                    Toast.makeText(context, getString(R.string.session_detail_export_error_source_not_found), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val transcriptContent = contentFile.readText()
                if (transcriptContent.isBlank()){
                     Toast.makeText(context, getString(R.string.session_detail_export_error_source_blank), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prepareAndShareFile(transcriptType, transcriptContent, format)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.session_detail_export_cancel)) { dialog, _ -> dialog.cancel() }
            .create().show()
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
                        "txt" -> { tempFile.writeText(transcriptContent); success = true }
                        "json" -> {
                            val speakersFile = File(currentSessionFolderFile!!, SPEAKERS_FILE_NAME)
                            val speakersJsonContent = if (speakersFile.exists()) speakersFile.readText() else null
                            val jsonExportData = exportService.prepareJsonExport(sessionName, transcriptType, transcriptContent, speakersJsonContent)
                            tempFile.writeText(jsonExportData); mimeType = "application/json"; success = true
                        }
                        "pdf" -> {
                            success = exportService.preparePdfExport(requireContext(), transcriptContent, tempFile)
                            mimeType = "application/pdf"
                        }
                    }
                } catch (e: Exception) { Log.e(TAG, "Error preparing export file $exportFileName", e) }
            }

            if (success) { exportService.shareFile(requireContext(), tempFile, mimeType) }
            else { Toast.makeText(context, getString(R.string.session_detail_export_error_prepare_failed), Toast.LENGTH_SHORT).show() }
        }
    }

    private fun handleDiarization() {
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { /* ... */ return }
        val audioFile = sessionManager.getAudioFilePath(currentSessionFolderFile!!)
        if (!audioFile.exists()) {
            Toast.makeText(context, getString(R.string.session_detail_diarization_audio_missing), Toast.LENGTH_SHORT).show()
            textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_audio_needed)
            return
        }

        buttonDiarize.isEnabled = false; buttonDiarize.text = getString(R.string.session_detail_diarizing_button)
        textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_in_progress)
        buttonTranscribe.isEnabled = false; buttonRedact.isEnabled = false; buttonExport.isEnabled = false;

        viewLifecycleOwner.lifecycleScope.launch {
            var diarizationJson: String? = null
            try {
                withContext(Dispatchers.IO) {
                    diarizationService.runDiarization(requireContext(), currentSessionFolderFile!!, audioFile) { result -> diarizationJson = result }
                }
                if (diarizationJson != null && diarizationJson!!.isNotBlank() && !diarizationJson!!.startsWith("Error:")) {
                    val speakersFile = File(currentSessionFolderFile!!, SPEAKERS_FILE_NAME)
                    try {
                        withContext(Dispatchers.IO) { FileWriter(speakersFile).use { it.write(diarizationJson) } }
                        textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_complete)
                        updateMetadata(hasDiarization = true)
                    } catch (e: IOException) {
                        textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_error_saving)
                        updateMetadata(hasDiarization = false)
                    }
                } else {
                    textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_failed)
                    Log.e(TAG, "Diarization failed. Result: $diarizationJson")
                    updateMetadata(hasDiarization = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during diarization", e)
                textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_error_exception)
                updateMetadata(hasDiarization = false)
            } finally {
                 updateAllButtonStates(readMetadataForCurrentSession())
                 buttonTranscribe.isEnabled = readMetadataForCurrentSession()?.has_transcript != true
            }
        }
    }
    private fun handleRedaction() {
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { /* ... */ return }
        val transcriptFile = File(currentSessionFolderFile!!, TRANSCRIPT_FILE_NAME)
        if (!transcriptFile.exists() || transcriptFile.length() == 0L) {
            Toast.makeText(context, getString(R.string.session_detail_redaction_transcript_missing), Toast.LENGTH_SHORT).show()
            return
        }

        buttonRedact.isEnabled = false; buttonRedact.text = getString(R.string.session_detail_redacting_button)
        textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_in_progress)
        buttonDiarize.isEnabled = false; buttonTranscribe.isEnabled = false; buttonExport.isEnabled = false;

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val originalTranscript = withContext(Dispatchers.IO) { transcriptFile.readText() }
                if (originalTranscript.isBlank()) {
                    textViewRedactionStatus.text = getString(R.string.session_detail_redaction_transcript_blank)
                    updateAllButtonStates(readMetadataForCurrentSession()); return@launch
                }
                val redactedTranscript = withContext(Dispatchers.Default) { Redactor.redact(originalTranscript) }
                val redactedFile = File(currentSessionFolderFile!!, REDACTED_TRANSCRIPT_FILE_NAME)
                withContext(Dispatchers.IO) { FileWriter(redactedFile).use { it.write(redactedTranscript) } }
                updateMetadata(hasRedacted = true)
                textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_complete)
            } catch (e: Exception) {
                Log.e(TAG, "Error during redaction", e)
                textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_error)
            } finally {
                updateAllButtonStates(readMetadataForCurrentSession())
            }
        }
    }
    private fun handleTranscription() {
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { /* ... */ return }
        val audioFile = sessionManager.getAudioFilePath(currentSessionFolderFile!!)
        if (!audioFile.exists()) {
            val dummyAudioFile = File(currentSessionFolderFile, "audio.mp4")
            if (!dummyAudioFile.exists()) { Toast.makeText(context, getString(R.string.session_detail_audio_file_not_found), Toast.LENGTH_SHORT).show(); return }
             Log.w(TAG, "Using DUMMY audio file: ${dummyAudioFile.absolutePath}")
        }

        buttonTranscribe.isEnabled = false; buttonTranscribe.text = getString(R.string.session_detail_transcribing_button)
        textViewTranscriptionStatus.text = getString(R.string.session_detail_transcription_status_in_progress)
        scrollViewTranscript.visibility = View.GONE; textViewTranscript.text = ""
        buttonRedact.isEnabled = false; textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_needs_transcript)
        buttonDiarize.isEnabled = false; textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_transcription_in_progress)
        buttonExport.isEnabled = false;

        viewLifecycleOwner.lifecycleScope.launch {
            var transcriptResult: String? = null
            try {
                withContext(Dispatchers.IO) {
                    whisperService.runTranscription(requireContext(), currentSessionFolderFile!!, audioFile) { transcript -> transcriptResult = transcript }
                }
                if (transcriptResult != null && transcriptResult!!.isNotBlank() && !transcriptResult!!.startsWith("Error:")) {
                    saveTranscriptToFile(transcriptResult!!)
                    updateMetadata(hasTranscript = true, hasRedacted = false)
                } else {
                    updateMetadata(hasTranscript = false, hasRedacted = false, hasDiarization = readMetadataForCurrentSession()?.has_diarization ?: false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during transcription coroutine", e)
                updateMetadata(hasTranscript = false, hasRedacted = false, hasDiarization = readMetadataForCurrentSession()?.has_diarization ?: false)
            } finally {
                // UI update for transcript text and status is done within updateMetadata -> loadAndDisplayExistingTranscript -> updateAllButtonStates
                // Explicitly re-enable transcribe button here as it's the primary action that finished.
                buttonTranscribe.isEnabled = true
                // loadAndDisplayExistingTranscript will be called by updateMetadata, which then calls updateAllButtonStates
            }
        }
    }
    private fun saveTranscriptToFile(transcript: String) {
         currentSessionFolderFile?.let { folder ->
            val transcriptFile = File(folder, TRANSCRIPT_FILE_NAME)
            try { FileWriter(transcriptFile).use { it.write(transcript) }
                Log.d(TAG, "Transcript saved to ${transcriptFile.absolutePath}")
            } catch (e: IOException) { Log.e(TAG, "Failed to save transcript to file", e) }
        }
    }
    private fun updateMetadata(hasTranscript: Boolean? = null, hasRedacted: Boolean? = null, hasDiarization: Boolean? = null) {
        currentSessionFolderFile?.let { folder ->
            val metadata = readMetadataForCurrentSession() ?: Metadata()

            hasTranscript?.let { metadata.has_transcript = it }
            if (hasTranscript == true) metadata.has_redacted = false
            hasRedacted?.let { metadata.has_redacted = it }
            hasDiarization?.let { metadata.has_diarization = it }

            val success = sessionManager.createMetadataFile(folder, metadata)
            if(success) {
                Log.d(TAG, "Metadata file updated: $metadata")
                // Reload transcript and update all button states, as metadata change can affect multiple things
                loadAndDisplayExistingTranscript()
            }
            else {
                Log.e(TAG, "Failed to update metadata file, UI might be stale.")
                // Fallback to manually updating button states if metadata save failed but we want UI to reflect intent
                updateAllButtonStates(metadata)
            }
        }
    }

    private fun startPlayback() {
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { return }
        val audioFile = sessionManager.getAudioFilePath(currentSessionFolderFile!!)
        if (!audioFile.exists()) {
            Toast.makeText(context, getString(R.string.session_detail_audio_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        mediaPlayer = MediaPlayer().apply {
            try { setDataSource(audioFile.absolutePath); prepareAsync()
                setOnPreparedListener { start(); isPlaying = true; buttonPlayAudio.text = getString(R.string.session_detail_stop_playback) }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(context, getString(R.string.session_detail_error_playing_audio), Toast.LENGTH_SHORT).show()
                    stopPlayback(); true
                }
            } catch (e: IOException) {
                Toast.makeText(context, getString(R.string.session_detail_playback_setup_error), Toast.LENGTH_SHORT).show()
                releaseMediaPlayer()
            }
        }
    }
    private fun stopPlayback() {
        mediaPlayer?.apply { if (this.isPlaying) { try { stop() } catch (e: IllegalStateException) {} }; release() }
        mediaPlayer = null; isPlaying = false
        if (::buttonPlayAudio.isInitialized) { buttonPlayAudio.text = getString(R.string.session_detail_play_audio) }
    }
    private fun releaseMediaPlayer() {
        mediaPlayer?.release(); mediaPlayer = null; isPlaying = false
        if (view != null && ::buttonPlayAudio.isInitialized) { buttonPlayAudio.text = getString(R.string.session_detail_play_audio)}
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
