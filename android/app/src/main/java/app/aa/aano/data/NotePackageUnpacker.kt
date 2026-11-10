// Ingestion unpacker for .aano packages (ZIP archive of note.json + attachments).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

object NotePackageUnpacker {

    data class UnpackedNote(
        val note: Note?,
        val errorMessage: String? = null
    )

    fun unpack(context: Context, uri: Uri): UnpackedNote {
        val mediaImgDir = File(context.filesDir, "media/images").apply { mkdirs() }
        val mediaAudioDir = File(context.filesDir, "media/audio").apply { mkdirs() }

        var noteJsonStr: String? = null
        var fallbackMdStr: String? = null
        var fallbackTxtStr: String? = null
        val savedImages = mutableListOf<String>()
        var savedAudioPath: String? = null
        var audioDuration = 0L

        try {
            context.contentResolver.openInputStream(uri)?.use { rawIn ->
                java.util.zip.ZipInputStream(rawIn).use { zis ->
                    val maxEntryBytes = 15L * 1024 * 1024 // 15MB max per entry
                    val maxPackageBytes = 25L * 1024 * 1024 // 25MB max total
                    var totalBytes = 0L
                    var entry = zis.nextEntry
                    val buffer = ByteArray(8192)
                    while (entry != null) {
                        val name = entry.name
                        var entryBytes = 0L
                        if (name == "note.json") {
                            val baos = java.io.ByteArrayOutputStream()
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                totalBytes += len
                                entryBytes += len
                                if (entryBytes > maxEntryBytes || totalBytes > maxPackageBytes) break
                                baos.write(buffer, 0, len)
                            }
                            noteJsonStr = baos.toString("UTF-8")
                        } else if (name.endsWith(".md") && !name.contains("/")) {
                            val baos = java.io.ByteArrayOutputStream()
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                totalBytes += len
                                entryBytes += len
                                if (entryBytes > maxEntryBytes || totalBytes > maxPackageBytes) break
                                baos.write(buffer, 0, len)
                            }
                            fallbackMdStr = baos.toString("UTF-8")
                        } else if (name.endsWith(".txt") && !name.contains("/")) {
                            val baos = java.io.ByteArrayOutputStream()
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                totalBytes += len
                                entryBytes += len
                                if (entryBytes > maxEntryBytes || totalBytes > maxPackageBytes) break
                                baos.write(buffer, 0, len)
                            }
                            fallbackTxtStr = baos.toString("UTF-8")
                        } else if (name.startsWith("media/images/") || name.startsWith("images/")) {
                            val fn = File(name).name
                            val ext = fn.substringAfterLast('.', "").lowercase()
                            val allowedImageExts = setOf("png", "jpg", "jpeg", "webp", "gif")
                            if (ext in allowedImageExts) {
                                val destFile = File(mediaImgDir, "pkg__")
                                destFile.outputStream().use { fos ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        totalBytes += len
                                        entryBytes += len
                                        if (entryBytes > maxEntryBytes || totalBytes > maxPackageBytes) break
                                        fos.write(buffer, 0, len)
                                    }
                                }
                                if (destFile.length() > 0) {
                                    savedImages.add(destFile.absolutePath)
                                }
                            }
                        } else if (name.startsWith("media/audio/") || name.startsWith("audio/")) {
                            val fn = File(name).name
                            val ext = fn.substringAfterLast('.', "").lowercase()
                            val allowedAudioExts = setOf("m4a", "aac", "wav", "mp3", "ogg")
                            if (ext in allowedAudioExts) {
                                val destFile = File(mediaAudioDir, "pkg__")
                                destFile.outputStream().use { fos ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        totalBytes += len
                                        entryBytes += len
                                        if (entryBytes > maxEntryBytes || totalBytes > maxPackageBytes) break
                                        fos.write(buffer, 0, len)
                                    }
                                }
                                if (destFile.length() > 0) {
                                    savedAudioPath = destFile.absolutePath
                                }
                            }
                        }
                        if (totalBytes > maxPackageBytes) {
                            Log.w("AANO", "Package extraction capped at 25MB safety limit.")
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val now = System.currentTimeMillis()
            val validJsonStr = noteJsonStr
            if (!validJsonStr.isNullOrBlank()) {
                val jsonObj = org.json.JSONObject(validJsonStr)
                val noteId = UUID.randomUUID().toString()
                val title = jsonObj.optString("title", "Imported Note")
                val content = jsonObj.optString("content", "")
                val folder = jsonObj.optString("folder", "General")
                val color = jsonObj.optInt("color", 0)
                audioDuration = jsonObj.optLong("audioDuration", 0L)

                val note = Note(
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
                return UnpackedNote(note)
            } else if (!fallbackMdStr.isNullOrBlank() || !fallbackTxtStr.isNullOrBlank()) {
                val rawText = fallbackMdStr ?: fallbackTxtStr ?: ""
                val firstLine = rawText.lines().firstOrNull { it.isNotBlank() } ?: "Imported Note"
                val cleanTitle = firstLine.replace(Regex("^#+\\s*"), "").take(50)
                val note = Note(
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
                return UnpackedNote(note)
            } else {
                return UnpackedNote(null, "Invalid or empty note package")
            }
        } catch (e: Exception) {
            Log.e("AANO", "Failed to open package: ", e)
            return UnpackedNote(null, e.localizedMessage ?: "Failed to open note package")
        }
    }
}
