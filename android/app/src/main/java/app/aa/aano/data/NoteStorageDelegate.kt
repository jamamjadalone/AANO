// Storage and encryption delegate for AANO note persistence.
// Handles JSON file read/write, AES-256-GCM encryption, in-memory cache, and media cleanup.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NoteStorageDelegate(private val context: Context) {

    companion object {
        @Volatile
        private var memoryCache: MutableList<Note>? = null
        private val cacheLock = Any()
    }

    private val notesFile: File = File(context.filesDir, "notes.json")
    val cipher = NoteCipher()
    val ftsIndex = NoteFtsIndex(context)

    fun readNotes(): List<Note> {
        synchronized(cacheLock) {
            memoryCache?.let { return it.toList() }
            if (!notesFile.exists()) {
                memoryCache = mutableListOf()
                return emptyList()
            }
            return try {
                val list = deserializeNotes(notesFile.readText())
                var needsResave = false
                val fixed = list.map { note ->
                    if (note.deleted && note.deletedAt > 0 && note.updatedAt < note.deletedAt) {
                        needsResave = true
                        note.copy(updatedAt = note.deletedAt)
                    } else {
                        note
                    }
                }
                memoryCache = fixed.toMutableList()
                try {
                    ftsIndex.syncAll(fixed)
                } catch (fe: Exception) {
                    Log.w("AANO", "Failed to init FTS index: ${fe.message}")
                }
                if (needsResave) {
                    val array = JSONArray()
                    fixed.forEach { array.put(serializeNote(it)) }
                    notesFile.writeText(array.toString())
                }
                fixed
            } catch (e: Exception) {
                Log.e("AANO", "Failed to deserialize notes. Preserving corrupted backup file.", e)
                try {
                    val corruptBackup = File(notesFile.parentFile, "notes.json.corrupt.${System.currentTimeMillis()}.bak")
                    notesFile.copyTo(corruptBackup, overwrite = true)
                } catch (be: Exception) {
                    Log.e("AANO", "Failed to create corrupt notes backup", be)
                }
                emptyList()
            }
        }
    }

    fun writeNotes(notes: List<Note>): Boolean {
        synchronized(cacheLock) {
            val prevCache = memoryCache?.toMutableList()
            return try {
                val array = JSONArray()
                notes.forEach { array.put(serializeNote(it)) }
                val tmpFile = File(notesFile.parentFile, "notes.json.tmp")
                tmpFile.writeText(array.toString())
                val renamed = if (tmpFile.renameTo(notesFile)) {
                    true
                } else {
                    if (notesFile.exists()) notesFile.delete()
                    tmpFile.renameTo(notesFile)
                }
                if (!renamed && tmpFile.exists()) {
                    notesFile.writeText(array.toString())
                    tmpFile.delete()
                }
                memoryCache = notes.toMutableList()
                try {
                    ftsIndex.syncAll(notes)
                } catch (fe: Exception) {
                    Log.w("AANO", "Failed to update FTS index on write: ${fe.message}")
                }
                true
            } catch (e: Exception) {
                Log.e("AANO", "writeNotes failed: ${e.message}", e)
                memoryCache = prevCache
                false
            }
        }
    }

    fun deserializeNotes(json: String): List<Note> {
        val notes = mutableListOf<Note>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            notes.add(deserializeNote(array.getJSONObject(i)))
        }
        return notes
    }

    fun deserializeNote(obj: JSONObject): Note {
        val encrypted = obj.optBoolean("enc", false)
        fun field(name: String): String {
            val raw = obj.optString(name, "")
            return if (encrypted && raw.isNotEmpty()) {
                try {
                    cipher.decrypt(raw)
                } catch (e: Exception) {
                    Log.e("AANO", "Failed to decrypt note field '$name': ${e.message}")
                    raw
                }
            } else raw
        }
        val rawImages = obj.optJSONArray("images")
        val imagesList = if (rawImages != null) {
            (0 until rawImages.length()).map { rawImages.getString(it) }
        } else emptyList()
        val audioUriStr = if (obj.has("audioUri") && !obj.isNull("audioUri")) obj.optString("audioUri") else null

        return Note(
            id = obj.getString("id"),
            title = field("title"),
            content = field("content"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            deleted = obj.optBoolean("deleted", false),
            deletedAt = obj.optLong("deletedAt", 0),
            pinned = obj.optBoolean("pinned", false),
            color = obj.optInt("color", Note.COLOR_DEFAULT),
            folder = field("folder"),
            tags = obj.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val raw = arr.getString(i)
                    if (encrypted && raw.isNotEmpty()) {
                        try {
                            cipher.decrypt(raw)
                        } catch (e: Exception) {
                            Log.e("AANO", "Failed to decrypt tag: ${e.message}")
                            raw
                        }
                    } else raw
                }
            } ?: emptyList(),
            reminderAt = obj.optLong("reminderAt", 0),
            archived = obj.optBoolean("archived", false),
            isLocal = obj.optBoolean("isLocal", false),
            images = imagesList,
            audioUri = audioUriStr,
            audioDuration = obj.optLong("audioDuration", 0L),
            purged = obj.optBoolean("purged", false),
            type = obj.optInt("type", Note.TYPE_TEXT),
            colorIndex = obj.optInt("colorIndex", 0),
            colorLabel = field("colorLabel"),
            importance = obj.optInt("importance", 0),
            reminderType = obj.optInt("reminderType", Note.REMINDER_NONE),
            reminderDate = obj.optLong("reminderDate", 0L),
            minorModifiedDate = obj.optLong("minorModifiedDate", 0L),
            noteExt = obj.optString("noteExt", "")
        )
    }

    fun serializeNote(note: Note): JSONObject = JSONObject().apply {
        put("id", note.id)
        put("title", cipher.encrypt(note.title))
        put("content", cipher.encrypt(note.content))
        put("createdAt", note.createdAt)
        put("updatedAt", note.updatedAt)
        put("deleted", note.deleted)
        put("deletedAt", note.deletedAt)
        put("pinned", note.pinned)
        put("color", note.color)
        put("folder", cipher.encrypt(note.folder))
        put("tags", JSONArray(note.tags.map { cipher.encrypt(it) }))
        put("reminderAt", note.reminderAt)
        put("archived", note.archived)
        put("isLocal", note.isLocal)
        put("images", JSONArray(note.images))
        if (note.audioUri != null) put("audioUri", note.audioUri)
        put("audioDuration", note.audioDuration)
        put("purged", note.purged)
        put("type", note.type)
        put("colorIndex", note.colorIndex)
        put("colorLabel", cipher.encrypt(note.colorLabel))
        put("importance", note.importance)
        put("reminderType", note.reminderType)
        put("reminderDate", note.reminderDate)
        put("minorModifiedDate", note.minorModifiedDate)
        put("noteExt", note.noteExt)
        put("enc", true)
    }

    fun cleanupMediaFiles(note: Note) {
        try {
            for (imgPath in note.images) {
                val f = File(imgPath)
                if (f.exists() && f.isFile) f.delete()
            }
            note.audioUri?.let { uriStr ->
                val audioPath = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
                val f = File(audioPath)
                if (f.exists() && f.isFile) f.delete()
            }
        } catch (e: Exception) {
            Log.w("AANO", "Suppressed cleanupMediaFiles exception: ${e.message}", e)
        }
    }
}
