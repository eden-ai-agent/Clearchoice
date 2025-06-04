package com.example.clearchoice

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class SessionListFragment : Fragment() {

    private lateinit var recyclerViewSessions: RecyclerView
    private lateinit var sessionAdapter: SessionAdapter
    private lateinit var sessionManager: SessionManager // Instantiate or inject

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_session_list, container, false)
        recyclerViewSessions = view.findViewById(R.id.recyclerViewSessions)
        recyclerViewSessions.layoutManager = LinearLayoutManager(context)

        sessionManager = SessionManager() // Simple instantiation for now

        // Load sessions and display
        loadSessionData()

        return view
    }

    override fun onResume() {
        super.onResume()
        // Refresh list when fragment becomes visible, e.g., after a new recording
        loadSessionData()
    }

    private fun loadSessionData() {
        val sessionsDir = context?.getExternalFilesDir(null)?.let { File(it, "ClearChoiceSessions") }
        if (sessionsDir != null && sessionsDir.exists() && sessionsDir.isDirectory) {
            val sessionFolders = sessionsDir.listFiles { file -> file.isDirectory }?.sortedDescending() ?: emptyArray()
            val sessionNames = sessionFolders.map { it.name }.toMutableList()

            // TODO: Later, load metadata for each session to display more details
            val sessionItems = sessionFolders.map { folder ->
                // For now, just using folder name. Later, load metadata.json.
                val metadataFile = File(folder, "metadata.json")
                var details = "Details: metadata.json " + if (metadataFile.exists()) "found" else "missing"
                // Example of trying to read metadata (simplified)
                // if (metadataFile.exists()) {
                // try {
                // val content = metadataFile.readText()
                // val json = org.json.JSONObject(content)
                // val hasTranscript = json.optBoolean("has_transcript", false)
                // details = "Transcript: $hasTranscript"
                // } catch (e: Exception) { Log.e(TAG, "Error reading metadata for ${folder.name}", e) }
                // }
                SessionDisplayItem(folder.name, details)
            }.toMutableList()


            if (::sessionAdapter.isInitialized) {
                sessionAdapter.updateData(sessionItems)
            } else {
                sessionAdapter = SessionAdapter(sessionItems)
                recyclerViewSessions.adapter = sessionAdapter
            }
            Log.d(TAG, "Loaded ${sessionItems.size} sessions.")

        } else {
            Log.d(TAG, "Sessions directory not found or is not a directory.")
            if (::sessionAdapter.isInitialized) {
                sessionAdapter.updateData(mutableListOf()) // Clear list if dir not found
            } else {
                sessionAdapter = SessionAdapter(mutableListOf())
                recyclerViewSessions.adapter = sessionAdapter
            }
        }
    }

    // Simple data class for display purposes
    data class SessionDisplayItem(val name: String, val details: String)

    // Inner class for SessionAdapter
    private inner class SessionAdapter(private var sessions: MutableList<SessionDisplayItem>) :
        RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_session, parent, false)
            return SessionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
            val session = sessions[position]
            holder.textViewSessionName.text = session.name
            holder.textViewSessionDetails.text = session.details
            holder.itemView.setOnClickListener {
                Log.d(TAG, "Clicked on session: ${session.name}")
                val fragment = SessionDetailFragment.newInstance(session.name)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null) // Add to back stack
                    .commit()
            }
        }

        override fun getItemCount(): Int = sessions.size

        fun updateData(newSessions: MutableList<SessionDisplayItem>) {
            sessions.clear()
            sessions.addAll(newSessions)
            notifyDataSetChanged() // Consider DiffUtil for better performance later
        }

        inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textViewSessionName: TextView = itemView.findViewById(R.id.textViewSessionName)
            val textViewSessionDetails: TextView = itemView.findViewById(R.id.textViewSessionDetails)
        }
    }

    companion object {
        private const val TAG = "SessionListFragment"
    }
}
