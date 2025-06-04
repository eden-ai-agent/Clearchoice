package com.example.clearchoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File // Required for File operations
import java.text.SimpleDateFormat // Required for dummy session name
import java.util.Date // Required for dummy session name
import java.util.Locale // Required for dummy session name

class RecordFragment : Fragment() {

    private lateinit var buttonRecord: Button
    private lateinit var textViewStatus: TextView
    private var isActuallyRecording = false // Simulates recording state

    private lateinit var sessionManager: SessionManager // For creating dummy session folder name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager() // Initialize SessionManager
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_record, container, false)
        buttonRecord = view.findViewById(R.id.buttonRecord)
        textViewStatus = view.findViewById(R.id.textViewStatus)

        buttonRecord.setOnClickListener {
            if (isActuallyRecording) {
                // Simulate stopping recording
                isActuallyRecording = false
                buttonRecord.text = getString(R.string.record_button_start)
                textViewStatus.text = getString(R.string.record_status_stopped)
                Log.d(TAG, "Simulated recording stopped.")

                // Create a dummy session name for navigation
                // In a real scenario, this would come from the actual recording session
                val dummySessionName = "DUMMY_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                // We need to ensure this dummy session folder and a dummy audio file exist for SessionDetailFragment to work
                createDummySessionFiles(dummySessionName)


                navigateToDetailFragment(dummySessionName)

            } else {
                // Simulate starting recording
                if (checkRecordAudioPermission()) {
                    isActuallyRecording = true
                    buttonRecord.text = getString(R.string.record_button_stop)
                    textViewStatus.text = getString(R.string.record_status_recording)
                    Log.d(TAG, "Simulated recording started.")
                    // Actual recording logic will be added later
                } else {
                    requestRecordAudioPermission()
                    Toast.makeText(context, getString(R.string.record_permission_needed), Toast.LENGTH_LONG).show()
                }
            }
        }

        // Initial state
        buttonRecord.text = getString(R.string.record_button_start)
        textViewStatus.text = getString(R.string.record_status_idle)

        // Placeholder for permission check (remains useful)
        if (!checkRecordAudioPermission()) {
            // Request permission if not already granted.
            // The button click will re-check, or you can disable the button until granted.
            // requestRecordAudioPermission() // Optionally request immediately
        }

        return view
    }

    private fun createDummySessionFiles(sessionName: String) {
        // This is purely for allowing SessionDetailFragment to somewhat function with a dummy session
        // In a real implementation, AudioRecorderManager would create the actual audio file.
        context?.let { ctx ->
            val sessionFolder = sessionManager.createNewSessionFolder(ctx, sessionName) // Modified SessionManager for this
            if (sessionFolder != null) {
                val audioFile = sessionManager.getAudioFilePath(sessionFolder)
                try {
                    // Create an empty dummy audio file
                    if (audioFile.parentFile?.exists() == true || audioFile.parentFile?.mkdirs() == true) {
                        audioFile.createNewFile()
                        Log.d(TAG, "Created dummy audio file: ${audioFile.absolutePath}")

                        // Create a dummy metadata file
                        val metadata = Metadata(has_transcript = false, has_redacted = false, has_diarization = false)
                        sessionManager.createMetadataFile(sessionFolder, metadata)
                        Log.d(TAG, "Created dummy metadata file for $sessionName")
                    } else {
                        Log.e(TAG, "Could not create parent directory for dummy audio file.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating dummy session files for $sessionName", e)
                }
            } else {
                Log.e(TAG, "Failed to create dummy session folder for $sessionName")
            }
        }
    }


    private fun navigateToDetailFragment(sessionFolderName: String) {
        val fragment = SessionDetailFragment.newInstance(sessionFolderName)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null) // Add to back stack so user can navigate back
            .commit()
    }

    private fun checkRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION_CODE
        )
    }

    // TODO: Override onRequestPermissionsResult to handle permission grant/denial
    // and enable/disable record button or update UI accordingly.

    companion object {
        private const val TAG = "RecordFragment"
        private const val RECORD_AUDIO_PERMISSION_CODE = 101
    }
}

// Extension to SessionManager to allow named session folder for dummy creation
fun SessionManager.createNewSessionFolder(context: Context, specificName: String): File? {
    val baseDir = context.getExternalFilesDir(null)
    if (baseDir == null) {
        Log.e("SessionManagerExt", "External storage not available.")
        return null
    }
    val sessionsRoot = File(baseDir, "ClearChoiceSessions")
    if (!sessionsRoot.exists() && !sessionsRoot.mkdirs()) {
        Log.e("SessionManagerExt", "Failed to create root sessions directory.")
        return null
    }
    val newSessionFolder = File(sessionsRoot, specificName)
    if (!newSessionFolder.mkdirs()) {
        Log.e("SessionManagerExt", "Failed to create specific session directory: $specificName")
        // It might already exist if we are clicking "stop" multiple times for the same dummy
        if (!newSessionFolder.exists() || !newSessionFolder.isDirectory) return null
    }
    Log.d("SessionManagerExt", "Ensured session folder exists: ${newSessionFolder.absolutePath}")
    return newSessionFolder
}
