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

        if (Intent.ACTION_VIEW == action) {
            val uri = intent.data
            if (uri != null) {
                if (uri.scheme == "aano" && uri.host == "drop") {
                    handleDropPairing(uri)
                    return
                }
                // Directly open and unpack .aano note package
                handleOpenPackage(uri)
                return
            }
        }

        if (Intent.ACTION_SEND == action) {
            val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }

            if (streamUri != null && isAanoPackageUri(streamUri)) {
                handleOpenPackage(streamUri)
                return
            }

            if ("text/plain" == type) {
                handleSendText(intent)
            } else if (type != null && type.startsWith("image/")) {
                handleSendImage(intent)
            } else if (streamUri != null) {
                handleOpenPackage(streamUri)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun isAanoPackageUri(uri: Uri): Boolean {
        val path = uri.path?.lowercase() ?: ""
        return path.endsWith(".aano") || path.endsWith(".zip") || uri.toString().lowercase().contains(".aano")
    }

    private fun handleOpenPackage(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaImgDir = File(filesDir, "media/images").apply { mkdirs() }
                val mediaAudioDir = File(filesDir, "media/audio").apply { mkdirs() }

                var noteJsonStr: String? = null
                var fallbackMdStr: String? = null
                var fallbackTxtStr: String? = null
                val savedImages = mutableListOf<String>()
                var savedAudioPath: String? = null
                var audioDuration = 0L

                contentResolver.openInputStream(uri)?.use { rawIn ->
                    java.util.zip.ZipInputStream(rawIn).use { zis ->
                        var entry = zis.nextEntry
                        val buffer = ByteArray(8192)
                        while (entry != null) {
                            val name = entry.name
                            if (name == "note.json") {
                                val baos = java.io.ByteArrayOutputStream()
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    baos.write(buffer, 0, len)
                                }
                                noteJsonStr = baos.toString("UTF-8")
                            } else if (name.endsWith(".md") && !name.contains("/")) {
                                val baos = java.io.ByteArrayOutputStream()
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    baos.write(buffer, 0, len)
                                }
                                fallbackMdStr = baos.toString("UTF-8")
                            } else if (name.endsWith(".txt") && !name.contains("/")) {
                                val baos = java.io.ByteArrayOutputStream()
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    baos.write(buffer, 0, len)
                                }
                                fallbackTxtStr = baos.toString("UTF-8")
                            } else if (name.startsWith("media/images/") || name.startsWith("images/")) {
                                val fn = File(name).name
                                val destFile = File(mediaImgDir, "pkg_${System.currentTimeMillis()}_$fn")
                                destFile.outputStream().use { fos ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        fos.write(buffer, 0, len)
                                    }
                                }
                                if (destFile.length() > 0) {
                                    savedImages.add(destFile.absolutePath)
                                }
                            } else if (name.startsWith("media/audio/") || name.startsWith("audio/")) {
                                val fn = File(name).name
                                val destFile = File(mediaAudioDir, "pkg_${System.currentTimeMillis()}_$fn")
                                destFile.outputStream().use { fos ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        fos.write(buffer, 0, len)
                                    }
                                }
                                if (destFile.length() > 0) {
                                    savedAudioPath = destFile.absolutePath
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val targetNote: Note
                if (!noteJsonStr.isNullOrBlank()) {
                    val jsonObj = org.json.JSONObject(noteJsonStr)
                    val noteId = UUID.randomUUID().toString()
                    val title = jsonObj.optString("title", "Imported Note")
                    val content = jsonObj.optString("content", "")
                    val folder = jsonObj.optString("folder", "General")
                    val color = jsonObj.optInt("color", 0)
                    audioDuration = jsonObj.optLong("audioDuration", 0L)

                    targetNote = Note(
                        id = noteId,
                        title = title,
                        content = content,
                        folder = folder,
                        color = color,
                        images = savedImages.ifEmpty { 
                            val origImages = jsonObj.optJSONArray("images")
                            val list = mutableListOf<String>()
                            if (origImages != null) {
                                for (i in 0 until origImages.length()) {
                                    list.add(origImages.getString(i))
                                }
                            }
                            list
                        },
                        audioUri = savedAudioPath,
                        audioDuration = audioDuration,
                        createdAt = now,
                        updatedAt = now,
                        isLocal = true
                    )
                } else if (!fallbackMdStr.isNullOrBlank() || !fallbackTxtStr.isNullOrBlank()) {
                    val rawText = fallbackMdStr ?: fallbackTxtStr ?: ""
                    val firstLine = rawText.lines().firstOrNull { it.isNotBlank() } ?: "Imported Note"
                    val cleanTitle = firstLine.replace(Regex("^#+\\s*"), "").take(50)
                    targetNote = Note(
                        id = UUID.randomUUID().toString(),
                        title = cleanTitle,
                        content = rawText,
                        folder = "General",
                        color = 0,
                        images = savedImages,
                        audioUri = savedAudioPath,
                        audioDuration = audioDuration,
                        createdAt = now,
                        updatedAt = now,
                        isLocal = true
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ShareReceiverActivity, "Invalid or empty note package", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                NoteRepository(this@ShareReceiverActivity).upsertNote(targetNote)

                withContext(Dispatchers.Main) {
                    val editIntent = Intent(this@ShareReceiverActivity, EditorActivity::class.java).apply {
                        putExtra(EditorActivity.EXTRA_NOTE_ID, targetNote.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(editIntent)
                    Toast.makeText(this@ShareReceiverActivity, "⚡ Opened in AANO Notes", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("AANO", "Failed to open package: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ShareReceiverActivity, "Failed to open note: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
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
    private fun handleDropPairing(uri: Uri) {
        val ip = uri.getQueryParameter("ip") ?: ""
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8765
        val pin = uri.getQueryParameter("pin") ?: ""

        if (ip.isBlank()) {
            Toast.makeText(this, "Invalid AANO Drop QR Code", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val prefs = app.aa.aano.data.Prefs(this)
        prefs.lastDropIp = if (port == 8765) ip else "$ip:$port"

        Toast.makeText(this, "⚡ AANO Drop: Paired with $ip (PIN $pin)", Toast.LENGTH_LONG).show()

        val homeIntent = Intent(this, HomeActivity::class.java).apply {
            putExtra("DROP_TARGET_IP", prefs.lastDropIp)
            putExtra("DROP_TARGET_PIN", pin)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(homeIntent)
        finish()
    }
}