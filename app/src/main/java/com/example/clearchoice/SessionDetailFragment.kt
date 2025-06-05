package com.example.clearchoice

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException

// Data class for Speaker Segments from speakers.json
data class SpeakerSegmentData(
    val speakerLabel: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startCharOffset: Int,
    val endCharOffset: Int
)

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

    private val speakerColors = listOf(
        Color.parseColor("#1F77B4"), Color.parseColor("#FF7F0E"),
        Color.parseColor("#2CA02C"), Color.parseColor("#D62728"),
        Color.parseColor("#9467BD"), Color.parseColor("#8C564B"),
        Color.parseColor("#E377C2"), Color.parseColor("#7F7F7F"),
        Color.parseColor("#BCBD22"), Color.parseColor("#17BECF")
    )
    private val speakerColorMap = mutableMapOf<String, Int>()
    private var nextSpeakerColorIndex = 0

    private fun getSpeakerColor(speakerLabel: String): Int {
        return speakerColorMap.computeIfAbsent(speakerLabel) {
            val color = speakerColors[nextSpeakerColorIndex % speakerColors.size]
            nextSpeakerColorIndex++
            color
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { sessionFolderName = it.getString(ARG_SESSION_FOLDER_NAME) }
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
                val sessionsRoot = File(baseDir, SessionManager.SESSIONS_DIR_NAME)
                currentSessionFolderFile = File(sessionsRoot, name)
                if (currentSessionFolderFile?.exists() == false) {
                    Log.e(TAG, "Session folder does not exist: ${currentSessionFolderFile?.absolutePath}")
                    Toast.makeText(context, getString(R.string.session_detail_error_not_found), Toast.LENGTH_LONG).show()
                    listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
                } else {
                    displayTranscriptAndMetadata()
                }
            } else {
                Log.e(TAG, "External storage not available to access session folder.")
                Toast.makeText(context, getString(R.string.session_detail_error_storage_not_available), Toast.LENGTH_LONG).show()
                listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
            }
        } ?: run {
            textViewSessionNameDetail.text = getString(R.string.session_detail_error_no_name)
            listOf(buttonPlayAudio, buttonTranscribe, buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
            textViewTranscriptionStatus.text = getString(R.string.session_detail_transcription_status_ready)
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
            // Now using SessionManager to read metadata, which includes error handling
            return sessionManager.readMetadata(folder)
        }
        return null
    }

    private fun updateAllButtonStates(metadata: Metadata?) {
        updateRedactButtonState(metadata)
        updateDiarizeButtonState(metadata)
        updateExportButtonState(metadata)
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
            buttonRedact.isEnabled = false; textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_missing_metadata); return
        }
        if (safeMetadata.has_transcript && !safeMetadata.has_redacted) {
            buttonRedact.isEnabled = true; buttonRedact.text = getString(R.string.session_detail_redact_button)
            textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_ready)
        } else if (safeMetadata.has_transcript && safeMetadata.has_redacted) {
            buttonRedact.isEnabled = false; buttonRedact.text = getString(R.string.session_detail_redacted_button)
            textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_done)
        } else {
            buttonRedact.isEnabled = false; textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_needs_transcript)
        }
    }
    private fun updateDiarizeButtonState(metadata: Metadata?) {
        val safeMetadata = metadata ?: readMetadataForCurrentSession()
        val audioFileExists = currentSessionFolderFile?.let { sessionManager.getAudioFilePath(it).exists() } ?: false
        if (safeMetadata == null && audioFileExists) {
             buttonDiarize.isEnabled = true; buttonDiarize.text = getString(R.string.session_detail_diarize_button)
             textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_ready)
        } else if (safeMetadata != null && audioFileExists && !safeMetadata.has_diarization) {
            buttonDiarize.isEnabled = true; buttonDiarize.text = getString(R.string.session_detail_diarize_button)
            textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_ready)
        } else if (safeMetadata != null && safeMetadata.has_diarization) {
            buttonDiarize.isEnabled = false; buttonDiarize.text = getString(R.string.session_detail_diarized_button)
            textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_done)
        } else {
            buttonDiarize.isEnabled = false; textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_audio_needed)
        }
    }

    private fun displayTranscriptAndMetadata() {
        currentSessionFolderFile?.let { folder ->
            val metadata = readMetadataForCurrentSession()
            // Use SessionManager to read transcript and speakers.json for better error handling
            val transcriptText = sessionManager.readFileContent(folder, SessionManager.TRANSCRIPT_FILE_NAME) ?: ""

            if (transcriptText.isNotBlank()) {
                textViewTranscriptionStatus.text = getString(R.string.session_detail_transcript_loaded)
                scrollViewTranscript.visibility = View.VISIBLE
                if (metadata?.has_diarization == true) {
                    val speakersJson = sessionManager.readFileContent(folder, SessionManager.SPEAKERS_FILE_NAME)
                    if (speakersJson != null) {
                        val speakerSegments = parseSpeakerSegments(speakersJson)
                        val spannableTranscript = applySpeakerColors(transcriptText, speakerSegments)
                        textViewTranscript.setText(spannableTranscript, TextView.BufferType.SPANNABLE)
                    } else {
                        Log.w(TAG, "Diarization metadata true, but speakers.json missing or unreadable.")
                        textViewTranscript.text = transcriptText // Fallback
                    }
                } else {
                    textViewTranscript.text = transcriptText // Plain transcript
                }
            } else {
                textViewTranscriptionStatus.text = if (sessionManager.readFileContent(folder, SessionManager.TRANSCRIPT_FILE_NAME) == null && metadata?.has_transcript == true)
                                                    getString(R.string.session_detail_transcription_status_error_reading)
                                                 else if (metadata?.has_transcript == true) // File exists but blank
                                                    getString(R.string.session_detail_transcription_status_empty_file)
                                                 else
                                                    getString(R.string.session_detail_transcription_status_no_transcript)
                scrollViewTranscript.visibility = View.GONE
                textViewTranscript.text = ""
            }
            updateAllButtonStates(metadata)
        }
    }

    private fun parseSpeakerSegments(speakersJson: String): List<SpeakerSegmentData> { /* ... same ... */
        val segments = mutableListOf<SpeakerSegmentData>()
        try {
            val jsonArray = JSONArray(speakersJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                segments.add(SpeakerSegmentData(
                    speakerLabel = jsonObj.getString("speaker_label"),
                    startTimeMs = jsonObj.getLong("start_time_ms"),
                    endTimeMs = jsonObj.getLong("end_time_ms"),
                    startCharOffset = jsonObj.getInt("start_char_offset"),
                    endCharOffset = jsonObj.getInt("end_char_offset")
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "Error parsing speakers.json content", e) }
        return segments
    }
    private fun applySpeakerColors(transcript: String, segments: List<SpeakerSegmentData>): SpannableString { /* ... same ... */
        val spannableString = SpannableString(transcript)
        speakerColorMap.clear(); nextSpeakerColorIndex = 0
        for (segment in segments) {
            val color = getSpeakerColor(segment.speakerLabel)
            val start = segment.startCharOffset.coerceAtLeast(0)
            val end = segment.endCharOffset.coerceAtMost(transcript.length)
            if (start < end) {
                spannableString.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else { Log.w(TAG, "Invalid char offset for segment: $segment (transcript length: ${transcript.length})") }
        }
        return spannableString
    }

    private fun showExportOptionsDialog() { /* ... same, but use SessionManager constants for filenames ... */
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_export_options, null)
        val radioGroupSource: RadioGroup = dialogView.findViewById(R.id.radioGroupSource)
        val radioOriginal: RadioButton = dialogView.findViewById(R.id.radioOriginal)
        val radioRedacted: RadioButton = dialogView.findViewById(R.id.radioRedacted)
        dialogView.findViewById<TextView>(R.id.textViewDialogExportSourceLabel).text = getString(R.string.session_detail_export_source_label)
        dialogView.findViewById<TextView>(R.id.textViewDialogExportFormatLabel).text = getString(R.string.session_detail_export_format_label)
        radioOriginal.text = getString(R.string.session_detail_export_source_original); radioRedacted.text = getString(R.string.session_detail_export_source_redacted)
        dialogView.findViewById<RadioButton>(R.id.radioFormatTxt).text = getString(R.string.session_detail_export_format_txt)
        dialogView.findViewById<RadioButton>(R.id.radioFormatJson).text = getString(R.string.session_detail_export_format_json)
        dialogView.findViewById<RadioButton>(R.id.radioFormatPdf).text = getString(R.string.session_detail_export_format_pdf)
        val metadata = readMetadataForCurrentSession(); radioRedacted.isEnabled = metadata?.has_redacted == true
        if (!radioRedacted.isEnabled) { radioOriginal.isChecked = true }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.session_detail_export_options_title)).setView(dialogView)
            .setPositiveButton(getString(R.string.session_detail_export_action)) { dialog, _ ->
                val isOriginal = radioGroupSource.checkedRadioButtonId == R.id.radioOriginal
                val formatId = dialogView.findViewById<RadioGroup>(R.id.radioGroupFormat).checkedRadioButtonId
                val transcriptType = if (isOriginal) "original" else "redacted"
                val format = when (formatId) { "json" -> "json"; "pdf" -> "pdf"; else -> "txt" }
                val fileNameToRead = if (isOriginal) SessionManager.TRANSCRIPT_FILE_NAME else SessionManager.REDACTED_TRANSCRIPT_FILE_NAME

                val transcriptContent = currentSessionFolderFile?.let { sessionManager.readFileContent(it, fileNameToRead) }
                if (transcriptContent == null || transcriptContent.isBlank()){
                     Toast.makeText(context, if(transcriptContent==null) getString(R.string.session_detail_export_error_source_not_found) else getString(R.string.session_detail_export_error_source_blank) , Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prepareAndShareFile(transcriptType, transcriptContent, format)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.session_detail_export_cancel)) { dialog, _ -> dialog.cancel() }
            .create().show()
    }
    private fun prepareAndShareFile(transcriptType: String, transcriptContent: String, format: String) { /* ... same, but use SessionManager constants for filenames ... */
        viewLifecycleOwner.lifecycleScope.launch {
            val sessionName = sessionFolderName ?: "UnknownSession"
            val exportFileName = "export_${sessionName}_${transcriptType}.$format"
            val tempFile = File(requireContext().cacheDir, exportFileName)
            var mimeType = "text/plain"; var success = false
            withContext(Dispatchers.IO) {
                try {
                    when (format) {
                        "txt" -> { tempFile.writeText(transcriptContent); success = true }
                        "json" -> {
                            val speakersJsonContent = currentSessionFolderFile?.let { sessionManager.readFileContent(it, SessionManager.SPEAKERS_FILE_NAME) }
                            val jsonExportData = exportService.prepareJsonExport(sessionName, transcriptType, transcriptContent, speakersJsonContent)
                            tempFile.writeText(jsonExportData); mimeType = "application/json"; success = true
                        }
                        "pdf" -> { success = exportService.preparePdfExport(requireContext(), transcriptContent, tempFile); mimeType = "application/pdf" }
                    }
                } catch (e: Exception) { Log.e(TAG, "Error preparing export file $exportFileName", e) }
            }
            if (success) { exportService.shareFile(requireContext(), tempFile, mimeType) }
            else { Toast.makeText(context, getString(R.string.session_detail_export_error_prepare_failed), Toast.LENGTH_SHORT).show() }
        }
    }
    private fun handleDiarization() { /* ... same, but use SessionManager constants for filenames ... */
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { return }
        val audioFile = sessionManager.getAudioFilePath(currentSessionFolderFile!!)
        if (!audioFile.exists()) {
            Toast.makeText(context, getString(R.string.session_detail_diarization_audio_missing), Toast.LENGTH_SHORT).show()
            textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_audio_needed); return
        }
        buttonDiarize.isEnabled = false; buttonDiarize.text = getString(R.string.session_detail_diarizing_button)
        textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_in_progress)
        listOf(buttonTranscribe, buttonRedact, buttonExport).forEach { it.isEnabled = false }
        viewLifecycleOwner.lifecycleScope.launch {
            var diarizationJson: String? = null
            try {
                withContext(Dispatchers.IO) { diarizationService.runDiarization(requireContext(), currentSessionFolderFile!!, audioFile) { result -> diarizationJson = result } }
                if (diarizationJson != null && diarizationJson!!.isNotBlank() && !diarizationJson!!.startsWith("Error:")) {
                    val successWriting = currentSessionFolderFile?.let { sessionManager.writeFileContent(it, SessionManager.SPEAKERS_FILE_NAME, diarizationJson!!) } ?: false
                    if(successWriting) {
                        textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_complete)
                        updateMetadata(hasDiarization = true)
                    } else {
                        textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_error_saving)
                        updateMetadata(hasDiarization = false)
                    }
                } else {
                    textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_failed)
                    updateMetadata(hasDiarization = false)
                }
            } catch (e: Exception) {
                textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_error_exception)
                updateMetadata(hasDiarization = false)
            } finally { val currentMeta = readMetadataForCurrentSession(); updateAllButtonStates(currentMeta) }
        }
    }
    private fun handleRedaction() { /* ... same, but use SessionManager constants for filenames ... */
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { return }
        val transcriptContent = currentSessionFolderFile?.let { sessionManager.readFileContent(it, SessionManager.TRANSCRIPT_FILE_NAME) }
        if (transcriptContent == null || transcriptContent.isBlank()) {
            Toast.makeText(context, getString(R.string.session_detail_redaction_transcript_missing), Toast.LENGTH_SHORT).show(); return
        }
        buttonRedact.isEnabled = false; buttonRedact.text = getString(R.string.session_detail_redacting_button)
        textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_in_progress)
        listOf(buttonTranscribe, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val redactedTranscript = withContext(Dispatchers.Default) { Redactor.redact(transcriptContent) }
                val successWriting = currentSessionFolderFile?.let { sessionManager.writeFileContent(it, SessionManager.REDACTED_TRANSCRIPT_FILE_NAME, redactedTranscript) } ?: false
                if(successWriting) {
                    textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_complete)
                    updateMetadata(hasRedacted = true)
                } else {
                     textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_error) // Error saving
                }
            } catch (e: Exception) { textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_error)
            } finally { updateAllButtonStates(readMetadataForCurrentSession()) }
        }
    }
    private fun handleTranscription() { /* ... same, but use SessionManager constants for filenames ... */
        if (currentSessionFolderFile == null || !currentSessionFolderFile!!.exists()) { return }
        val audioFile = sessionManager.getAudioFilePath(currentSessionFolderFile!!)
        if (!audioFile.exists()) { /* ... dummy audio check ... */ }
        buttonTranscribe.isEnabled = false; buttonTranscribe.text = getString(R.string.session_detail_transcribing_button)
        // ... other UI updates ...
        listOf(buttonRedact, buttonDiarize, buttonExport).forEach { it.isEnabled = false }
        textViewRedactionStatus.text = getString(R.string.session_detail_redaction_status_needs_transcript)
        textViewDiarizationStatus.text = getString(R.string.session_detail_diarization_status_transcription_in_progress)

        viewLifecycleOwner.lifecycleScope.launch {
            var transcriptResult: String? = null
            try {
                withContext(Dispatchers.IO) { whisperService.runTranscription(requireContext(), currentSessionFolderFile!!, audioFile) { transcript -> transcriptResult = transcript } }
                if (transcriptResult != null && transcriptResult!!.isNotBlank() && !transcriptResult!!.startsWith("Error:")) {
                    val successWriting = saveTranscriptToFile(transcriptResult!!) // saveTranscriptToFile uses SessionManager constant
                    if(successWriting) updateMetadata(hasTranscript = true, hasRedacted = false)
                    else updateMetadata(hasTranscript = false, hasRedacted = false, hasDiarization = readMetadataForCurrentSession()?.has_diarization ?: false)
                } else {
                    updateMetadata(hasTranscript = false, hasRedacted = false, hasDiarization = readMetadataForCurrentSession()?.has_diarization ?: false)
                }
            } catch (e: Exception) {
                updateMetadata(hasTranscript = false, hasRedacted = false, hasDiarization = readMetadataForCurrentSession()?.has_diarization ?: false)
            } finally { buttonTranscribe.isEnabled = true } // updateMetadata calls displayTranscript which calls updateAllButtonStates
        }
    }
    private fun saveTranscriptToFile(transcript: String) : Boolean {
         return currentSessionFolderFile?.let { folder ->
            sessionManager.writeFileContent(folder, SessionManager.TRANSCRIPT_FILE_NAME, transcript)
        } ?: false
    }
    private fun updateMetadata(hasTranscript: Boolean? = null, hasRedacted: Boolean? = null, hasDiarization: Boolean? = null) { /* ... same ... */
        currentSessionFolderFile?.let { folder ->
            val metadata = readMetadataForCurrentSession() ?: Metadata()
            hasTranscript?.let { metadata.has_transcript = it; if (it) metadata.has_redacted = false }
            hasRedacted?.let { metadata.has_redacted = it }
            hasDiarization?.let { metadata.has_diarization = it }
            val success = sessionManager.createMetadataFile(folder, metadata)
            if(success) { displayTranscriptAndMetadata() }
            else { Log.e(TAG, "Failed to update metadata file."); updateAllButtonStates(metadata) }
        }
    }
    private fun startPlayback() { /* ... same ... */ }
    private fun stopPlayback() { /* ... same ... */ }
    private fun releaseMediaPlayer() { /* ... same ... */ }
    override fun onStop() { super.onStop(); if (isPlaying) { stopPlayback() } else { releaseMediaPlayer() } }

    companion object {
        private const val TAG = "SessionDetailFragment"
        private const val ARG_SESSION_FOLDER_NAME = "session_folder_name"
        // Filename constants are now used from SessionManager.
        // e.g. SessionManager.TRANSCRIPT_FILE_NAME
        @JvmStatic
        fun newInstance(sessionFolderName: String) = SessionDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_SESSION_FOLDER_NAME, sessionFolderName) }
        }
    }
}
