package com.example.clearchoice

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ExportService {

    /**
     * Prepares a JSON string for export.
     * @param sessionId The name of the session.
     * @param transcriptType "original" or "redacted".
     * @param transcriptContent The actual transcript text.
     * @param speakersJsonContent Optional JSON string from speakers.json.
     * @return A JSON formatted string.
     */
    fun prepareJsonExport(
        sessionId: String,
        transcriptType: String,
        transcriptContent: String,
        speakersJsonContent: String?
    ): String {
        val jsonOutput = JSONObject()
        jsonOutput.put("session_id", sessionId)
        jsonOutput.put("transcript_type", transcriptType)
        jsonOutput.put("content", transcriptContent)

        if (speakersJsonContent != null) {
            try {
                // Attempt to parse speakersJsonContent to ensure it's valid JSON array/object
                // and then put it as a parsed object rather than a string if desired.
                // For simplicity here, just putting it as a string if it's not null.
                // A more robust way would be:
                // val speakerData = org.json.JSONTokener(speakersJsonContent).nextValue()
                // jsonOutput.put("diarization", speakerData)
                jsonOutput.put("diarization_data_str", speakersJsonContent) // Or parse and put as JSONArray
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing speakersJsonContent, adding as raw string.", e)
                jsonOutput.put("diarization_data_raw", speakersJsonContent)
            }
        } else {
            jsonOutput.put("diarization", JSONObject.NULL) // Or omit if preferred
        }
        return jsonOutput.toString(4) // Indent for readability
    }

    /**
     * Creates a basic PDF from text content.
     * @param context Context.
     * @param textContent The text to put in the PDF.
     * @param destinationFile The file to write the PDF to.
     * @return True on success, false otherwise.
     */
    fun preparePdfExport(context: Context, textContent: String, destinationFile: File): Boolean {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        paint.textSize = 10f // Small text size

        val x = 10f
        var y = 20f
        val lineSpacing = paint.fontSpacing + 2 // Add a little extra spacing

        // Simple text wrapping (manual)
        val textLines = textContent.split('\n')
        for (line in textLines) {
            // If line is too long, break it. This is a very basic way.
            var currentLine = line
            while (paint.measureText(currentLine) > pageInfo.pageWidth - 2 * x) {
                // Find a suitable break point (e.g., last space)
                var breakIndex = currentLine.length -1
                while(breakIndex > 0 && currentLine[breakIndex] != ' ' && paint.measureText(currentLine, 0, breakIndex) > pageInfo.pageWidth - 2*x) {
                    breakIndex--
                }
                if(breakIndex == 0) breakIndex = currentLine.length // cannot break

                val part = currentLine.substring(0, breakIndex)
                canvas.drawText(part, x, y, paint)
                y += lineSpacing
                currentLine = currentLine.substring(breakIndex).trimStart()
                if (y > pageInfo.pageHeight - x) { // Check for page overflow
                    pdfDocument.finishPage(page)    // Finish current page
                    // TODO: Create new page if text continues. For simplicity, truncating here.
                    Log.w(TAG, "PDF content truncated due to page limit (simple exporter).")
                    // page = pdfDocument.startPage(pageInfo) // Start new page
                    // canvas = page.canvas
                    // y = 20f
                    currentLine = "" // Stop processing further lines for this simple version
                    break
                }
            }
            if (currentLine.isNotEmpty()) {
                 canvas.drawText(currentLine, x, y, paint)
                 y += lineSpacing
                 if (y > pageInfo.pageHeight - x) {
                    Log.w(TAG, "PDF content might be truncated (simple exporter).")
                    // TODO: Handle multi-page properly for robust PDF export.
                 }
            }
        }

        pdfDocument.finishPage(page)

        try {
            FileOutputStream(destinationFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            Log.d(TAG, "PDF created successfully at ${destinationFile.absolutePath}")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error writing PDF to file", e)
            pdfDocument.close()
            return false
        }
    }


    /**
     * Shares a file using an ACTION_SEND Intent.
     * @param context Context.
     * @param file The file to share.
     * @param mimeType The MIME type of the file.
     */
    fun shareFile(context: Context, file: File, mimeType: String) {
        if (!file.exists()) {
            Log.e(TAG, "File to share does not exist: ${file.absolutePath}")
            Toast.makeText(context, "Error: File to share not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val authority = "${context.packageName}.provider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(Intent.createChooser(shareIntent, "Export Session File"))
            Log.d(TAG, "Share intent launched for URI: $contentUri with MIME: $mimeType")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching share intent", e)
            Toast.makeText(context, "Error: Could not launch share action. No app can handle this type of file?", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "ExportService"
    }
}
