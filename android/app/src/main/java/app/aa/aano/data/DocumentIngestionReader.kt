// Document Ingestion Reader for text, markdown and JSON documents.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.UUID

object DocumentIngestionReader {

    data class IngestionResult(
        val note: Note?,
        val errorMessage: String? = null
    )

    fun readDocument(context: Context, uri: Uri): IngestionResult {
        try {
            val maxChars = 250_000
            val fileName = resolveFileName(context, uri) ?: "Imported Note"
            val cleanTitle = fileName.substringBeforeLast(".")

            val stringBuilder = StringBuilder()
            var truncated = false

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val reader = java.io.BufferedReader(java.io.InputStreamReader(stream, Charsets.UTF_8))
                val buffer = CharArray(8192)
                var readCount: Int
                while (reader.read(buffer).also { readCount = it } != -1) {
                    if (stringBuilder.length + readCount > maxChars) {
                        val remaining = maxChars - stringBuilder.length
                        if (remaining > 0) {
                            stringBuilder.append(buffer, 0, remaining)
                        }
                        truncated = true
                        break
                    } else {
                        stringBuilder.append(buffer, 0, readCount)
                    }
                }
            }

            if (stringBuilder.isBlank()) {
                return IngestionResult(null, "Document is empty")
            }

            if (truncated) {
                stringBuilder.append("\n\n[Content Truncated - Exceeds 250KB Safe Limit]")
            }

            val now = System.currentTimeMillis()
            val note = Note(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                content = stringBuilder.toString(),
                folder = "Imported",
                color = 0,
                createdAt = now,
                updatedAt = now,
                isLocal = true
            )
            return IngestionResult(note)
        } catch (e: Exception) {
            Log.e("AANO", "Failed to open document: ", e)
            return IngestionResult(null, e.localizedMessage ?: "Failed to read document")
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("DocumentIngestion", "Resolve file name notice: ${e.message}")
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
