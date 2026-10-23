// ShareReceiverActivity: Smart Android Web & Text Clipper (Intent.ACTION_SEND).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.regex.Pattern

class ShareReceiverActivity : AppCompatActivity() {

    private val urlPattern = Pattern.compile("https?://[^\\s<>\"]+")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                handleSendText(intent)
            } else if (type.startsWith("image/")) {
                handleSendImage(intent)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun handleSendText(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        if (sharedText.isBlank()) {
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var title = sharedSubject?.trim() ?: ""
            var content = sharedText.trim()

            // Check if text is or contains a web URL
            val matcher = urlPattern.matcher(sharedText)
            if (matcher.find()) {
                val foundUrl = matcher.group()
                if (title.isEmpty()) {
                    val fetchedTitle = fetchPageTitle(foundUrl)
                    if (fetchedTitle != null && fetchedTitle.isNotBlank()) {
                        title = fetchedTitle
                    }
                }
            }

            if (title.isEmpty()) {
                val firstLine = sharedText.lines().firstOrNull { it.isNotBlank() } ?: "Shared Note"
                title = if (firstLine.length > 50) firstLine.take(47) + "..." else firstLine
            }

            val now = System.currentTimeMillis()
            val note = Note(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                folder = "General",
                color = 0,
                createdAt = now,
                updatedAt = now
            )

            NoteRepository(this@ShareReceiverActivity).upsertNote(note)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@ShareReceiverActivity, "Saved to AANO Notes", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun handleSendImage(intent: Intent) {
        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (imageUri == null) {
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaDir = File(filesDir, "media/images").apply { mkdirs() }
                val destFile = File(mediaDir, "clip_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(imageUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val now = System.currentTimeMillis()
                val note = Note(
                    id = UUID.randomUUID().toString(),
                    title = "Clipped Image",
                    content = "",
                    folder = "General",
                    images = listOf(destFile.absolutePath),
                    color = 0,
                    createdAt = now,
                    updatedAt = now
                )
                NoteRepository(this@ShareReceiverActivity).upsertNote(note)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ShareReceiverActivity, "Image clipped to AANO Notes", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.w("AANO", "Failed to clip image: ${e.message}")
                withContext(Dispatchers.Main) { finish() }
            }
        }
    }

    private fun fetchPageTitle(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }
            conn.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                var line: String?
                var count = 0
                while (reader.readLine().also { line = it } != null && count < 60) {
                    sb.append(line)
                    count++
                    if (sb.contains("</title>", ignoreCase = true)) break
                }
                val html = sb.toString()
                val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
                if (titleMatcher.find()) {
                    titleMatcher.group(1)?.trim()
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
