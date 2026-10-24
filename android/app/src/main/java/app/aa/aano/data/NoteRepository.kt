// Local note storage for AANO (JSON file in app-private storage).
// All note titles and contents are encrypted with AES-256-GCM using a key
// held in the Android Keystore (see NoteCipher).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import app.aa.aano.net.NoteReminderReceiver

enum class SortBy { UPDATED_DESC, CREATED_DESC, TITLE_ASC, PINNED_FIRST }

class NoteRepository(private val context: Context) {

    companion object {
        @Volatile
        private var memoryCache: MutableList<Note>? = null
        private val cacheLock = Any()
    }

    private val notesFile: File = File(context.filesDir, "notes.json")
    private val cipher = NoteCipher()
    private val prefs = Prefs(context)

    suspend fun getAll(): List<Note> = withContext(Dispatchers.IO) {
        readNotes()
    }

    suspend fun getVisible(): List<Note> = withContext(Dispatchers.IO) {
        sortNotes(readNotes().filter { !it.deleted && !it.archived })
    }

    suspend fun getArchived(): List<Note> = withContext(Dispatchers.IO) {
        sortNotes(readNotes().filter { !it.deleted && it.archived })
    }

    suspend fun getById(id: String): Note? = withContext(Dispatchers.IO) {
        readNotes().find { it.id == id }
    }

    suspend fun search(query: String): List<Note> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val all = readNotes().filter { !it.deleted && !it.archived }
        if (q.isEmpty()) sortNotes(all)
        else sortNotes(
            all.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.content.contains(q, ignoreCase = true) ||
                    it.folder.contains(q, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(q, ignoreCase = true) }
            }
        )
    }

    /** Distinct folder names in use + custom folders. */
    suspend fun folders(): List<String> = withContext(Dispatchers.IO) {
        val fromNotes = readNotes().filter { !it.deleted && !it.archived }.map { it.folder.trim() }.filter { it.isNotBlank() }
        val custom = prefs.customFolders.map { it.trim() }.filter { it.isNotBlank() }
        (fromNotes + custom).distinct().sorted()
    }

    suspend fun addFolder(name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            val updated = prefs.customFolders.toMutableSet()
            updated.add(trimmed)
            prefs.customFolders = updated
        }
    }

    suspend fun renameFolder(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        if (trimmedNew.isNotBlank() && trimmedOld != trimmedNew) {
            val updatedFolders = prefs.customFolders.toMutableSet()
            updatedFolders.remove(trimmedOld)
            updatedFolders.add(trimmedNew)
            prefs.customFolders = updatedFolders

            val notes = readNotes().toMutableList()
            var modified = false
            for (i in notes.indices) {
                if (notes[i].folder.equals(trimmedOld, ignoreCase = true)) {
                    notes[i] = notes[i].copy(folder = trimmedNew, updatedAt = System.currentTimeMillis())
                    modified = true
                }
            }
            if (modified) writeNotes(notes)
        }
    }

    suspend fun deleteFolder(name: String, deleteNotes: Boolean = false) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        val updatedFolders = prefs.customFolders.toMutableSet()
        updatedFolders.remove(trimmed)
        prefs.customFolders = updatedFolders

        val notes = readNotes().toMutableList()
        var modified = false
        for (i in notes.indices) {
            if (notes[i].folder.equals(trimmed, ignoreCase = true)) {
                val now = maxOf(System.currentTimeMillis(), notes[i].updatedAt + 1000L)
                if (deleteNotes) {
                    notes[i] = notes[i].copy(deleted = true, deletedAt = now, updatedAt = now)
                } else {
                    notes[i] = notes[i].copy(folder = "", updatedAt = now)
                }
                modified = true
            }
        }
        if (modified) writeNotes(notes)
    }

    suspend fun getFolderCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        val all = readNotes().filter { !it.deleted && !it.archived }
        val counts = mutableMapOf<String, Int>()
        // Initialize custom folders with 0
        prefs.customFolders.forEach { f ->
            val clean = f.trim()
            if (clean.isNotBlank()) counts[clean] = 0
        }
        all.forEach { note ->
            val f = note.folder.trim().ifBlank { "General" }
            counts[f] = (counts[f] ?: 0) + 1
        }
        counts
    }

    /** Distinct tags in use, for the filter chips. */
    suspend fun tags(): List<String> = withContext(Dispatchers.IO) {
        readNotes().filter { !it.deleted && !it.archived }.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()
    }

    suspend fun getVisibleInFolder(folder: String?): List<Note> = withContext(Dispatchers.IO) {
        val all = readNotes().filter { !it.deleted && !it.archived }
        val filtered = when {
            folder.isNullOrBlank() -> all
            folder.equals("General", ignoreCase = true) -> all.filter { it.folder.isBlank() || it.folder.equals("General", ignoreCase = true) }
            else -> all.filter { it.folder.equals(folder, ignoreCase = true) }
        }
        sortNotes(filtered)
    }

    suspend fun getLocalNotes(): List<Note> = withContext(Dispatchers.IO) {
        val filtered = readNotes().filter { !it.deleted && !it.archived && it.isLocal }
        sortNotes(filtered)
    }

    suspend fun getTrashed(): List<Note> = withContext(Dispatchers.IO) {
        readNotes().filter { it.deleted && !it.purged }.sortedByDescending { it.deletedAt }
    }

    suspend fun create(
        title: String,
        content: String,
        color: Int = Note.COLOR_DEFAULT,
        folder: String = "",
        tags: List<String> = emptyList(),
        isLocal: Boolean = false,
        images: List<String> = emptyList(),
        audioUri: String? = null,
        audioDuration: Long = 0L
    ): Note = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            createdAt = now,
            updatedAt = now,
            color = color,
            folder = folder,
            tags = tags,
            isLocal = isLocal,
            images = images,
            audioUri = audioUri,
            audioDuration = audioDuration
        )
        val notes = readNotes().toMutableList()
        notes.add(0, note)
        writeNotes(notes)
        note
    }

    suspend fun update(
        id: String,
        title: String,
        content: String,
        color: Int = Note.COLOR_DEFAULT,
        folder: String = "",
        tags: List<String> = emptyList(),
        isLocal: Boolean? = null,
        images: List<String>? = null,
        audioUri: String? = null,
        audioDuration: Long? = null
    ) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                val current = notes[index]
                notes[index] = current.copy(
                    title = title,
                    content = content,
                    color = color,
                    folder = folder,
                    tags = tags,
                    isLocal = isLocal ?: current.isLocal,
                    images = images ?: current.images,
                    audioUri = if (audioUri != null) audioUri else current.audioUri,
                    audioDuration = if (audioDuration != null) audioDuration else current.audioDuration,
                    updatedAt = System.currentTimeMillis()
                )
                writeNotes(notes)
            }
        }
    }

    suspend fun toggleLocal(id: String) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                notes[index] = notes[index].copy(
                    isLocal = !notes[index].isLocal,
                    updatedAt = System.currentTimeMillis()
                )
                writeNotes(notes)
            }
        }
    }

    suspend fun togglePin(id: String) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                notes[index] = notes[index].copy(pinned = !notes[index].pinned)
                writeNotes(notes)
            }
        }
    }

    suspend fun upsertNote(note: Note) = withContext(Dispatchers.IO) {
        val notes = readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == note.id }
        if (index >= 0) {
            notes[index] = note
        } else {
            notes.add(0, note)
        }
        writeNotes(notes)
    }

    fun serialize(note: Note): JSONObject = serializeNote(note)
    fun deserialize(obj: JSONObject): Note = deserializeNote(obj)

    suspend fun setReminder(id: String, reminderAt: Long) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                notes[index] = notes[index].copy(reminderAt = reminderAt)
                writeNotes(notes)
            }
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                val now = maxOf(System.currentTimeMillis(), notes[index].updatedAt + 1000L)
                notes[index] = notes[index].copy(
                    deleted = true,
                    deletedAt = now,
                    updatedAt = now
                )
                writeNotes(notes)
            }
            NoteReminderReceiver.cancel(context, id)
        }
    }

    suspend fun deleteLocalPermanently(id: String) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                val note = notes[index]
                cleanupMediaFiles(note)
                NoteReminderReceiver.cancel(context, id)
                notes.removeAt(index)
                writeNotes(notes)
            }
        }
    }

    suspend fun restore(id: String) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                val now = maxOf(System.currentTimeMillis(), notes[index].updatedAt + 1000L)
                notes[index] = notes[index].copy(deleted = false, deletedAt = 0, updatedAt = now)
                writeNotes(notes)
            }
        }
    }

    suspend fun archive(id: String) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                val now = maxOf(System.currentTimeMillis(), notes[index].updatedAt + 1000L)
                notes[index] = notes[index].copy(archived = true, updatedAt = now)
                writeNotes(notes)
            }
        }
    }

    suspend fun unarchive(id: String) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                val now = maxOf(System.currentTimeMillis(), notes[index].updatedAt + 1000L)
                notes[index] = notes[index].copy(archived = false, updatedAt = now)
                writeNotes(notes)
            }
        }
    }

    suspend fun deleteMultiple(ids: Set<String>) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            var modified = false
            for (i in notes.indices) {
                if (ids.contains(notes[i].id)) {
                    val now = maxOf(System.currentTimeMillis(), notes[i].updatedAt + 1000L)
                    notes[i] = notes[i].copy(
                        deleted = true,
                        deletedAt = now,
                        updatedAt = now
                    )
                    NoteReminderReceiver.cancel(context, notes[i].id)
                    modified = true
                }
            }
            if (modified) writeNotes(notes)
        }
    }

    suspend fun archiveMultiple(ids: Set<String>) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            var modified = false
            for (i in notes.indices) {
                if (ids.contains(notes[i].id)) {
                    val now = maxOf(System.currentTimeMillis(), notes[i].updatedAt + 1000L)
                    notes[i] = notes[i].copy(archived = true, updatedAt = now)
                    modified = true
                }
            }
            if (modified) writeNotes(notes)
        }
    }

    suspend fun unarchiveMultiple(ids: Set<String>) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            var modified = false
            for (i in notes.indices) {
                if (ids.contains(notes[i].id)) {
                    val now = maxOf(System.currentTimeMillis(), notes[i].updatedAt + 1000L)
                    notes[i] = notes[i].copy(archived = false, updatedAt = now)
                    modified = true
                }
            }
            if (modified) writeNotes(notes)
        }
    }

    private fun cleanupMediaFiles(note: Note) {
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

    suspend fun purge(id: String) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                val note = notes[index]
                cleanupMediaFiles(note)
                if (note.isLocal) {
                    notes.removeAt(index)
                } else {
                    val now = maxOf(System.currentTimeMillis(), note.updatedAt + 1000L)
                    notes[index] = note.copy(
                        title = "",
                        content = "",
                        images = emptyList(),
                        audioUri = null,
                        audioDuration = 0L,
                        deleted = true,
                        purged = true,
                        deletedAt = if (note.deletedAt > 0) note.deletedAt else now,
                        updatedAt = now
                    )
                }
                writeNotes(notes)
            }
            NoteReminderReceiver.cancel(context, id)
        }
    }

    suspend fun emptyTrash() {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val now = System.currentTimeMillis()
            val updated = mutableListOf<Note>()
            for (note in notes) {
                if (note.deleted && !note.purged) {
                    cleanupMediaFiles(note)
                    NoteReminderReceiver.cancel(context, note.id)
                    if (!note.isLocal) {
                        val stamp = maxOf(now, note.updatedAt + 1000L)
                        updated.add(
                            note.copy(
                                title = "",
                                content = "",
                                images = emptyList(),
                                audioUri = null,
                                audioDuration = 0L,
                                deleted = true,
                                purged = true,
                                deletedAt = if (note.deletedAt > 0) note.deletedAt else stamp,
                                updatedAt = stamp
                            )
                        )
                    }
                } else {
                    updated.add(note)
                }
            }
            writeNotes(updated)
        }
    }

    suspend fun purgeExpiredTrash(maxAgeMs: Long) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            val all = readNotes()
            val remaining = mutableListOf<Note>()
            for (note in all) {
                val isExpired = (note.deleted || note.purged) && note.deletedAt > 0 && note.deletedAt < cutoff
                if (isExpired) {
                    cleanupMediaFiles(note)
                    NoteReminderReceiver.cancel(context, note.id)
                } else {
                    remaining.add(note)
                }
            }
            writeNotes(remaining)
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            writeNotes(emptyList())
        }
    }

    suspend fun replaceAll(remote: List<Note>) {
        withContext(Dispatchers.IO) {
            val local = readNotes()
            val merged = merge(local, remote)
            writeNotes(merged)
        }
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        JSONArray().apply {
            readNotes().filter { !it.isLocal }.forEach { put(serializeNoteForCloud(it)) }
        }.toString()
    }

    suspend fun importJson(json: String) {
        withContext(Dispatchers.IO) {
            val remote = deserializeNotes(json)
            writeNotes(merge(readNotes(), remote))
        }
    }

    suspend fun importLocalVaultNotes(restoredNotes: List<Note>) = withContext(Dispatchers.IO) {
        val currentNotes = readNotes().toMutableList()
        for (rn in restoredNotes) {
            val idx = currentNotes.indexOfFirst { it.id == rn.id }
            if (idx >= 0) {
                currentNotes[idx] = rn
            } else {
                currentNotes.add(0, rn)
            }
        }
        writeNotes(currentNotes)
    }

    private fun serializeNoteForCloud(note: Note): JSONObject = JSONObject().apply {
        put("id", note.id)
        put("title", note.title)
        put("content", note.content)
        put("createdAt", note.createdAt)
        put("updatedAt", note.updatedAt)
        put("deleted", note.deleted)
        put("deletedAt", note.deletedAt)
        put("pinned", note.pinned)
        put("color", note.color)
        put("folder", note.folder)
        put("tags", JSONArray(note.tags))
        put("reminderAt", note.reminderAt)
        put("archived", note.archived)
        put("purged", note.purged)
        put("enc", false)
    }

    private fun sortNotes(notes: List<Note>): List<Note> {
        val comparator: Comparator<Note> = when (prefs.sortBy) {
            SortBy.CREATED_DESC -> compareByDescending { it.createdAt }
            SortBy.TITLE_ASC -> compareBy({ it.title.lowercase() }, { it.updatedAt })
            SortBy.PINNED_FIRST -> compareByDescending { it.updatedAt }
            SortBy.UPDATED_DESC -> compareByDescending { it.updatedAt }
        }
        // Pinned notes always float to the top.
        val pinned = notes.filter { it.pinned }
        val rest = notes.filterNot { it.pinned }
        return pinned.sortedWith(comparator) + rest.sortedWith(comparator)
    }

    private fun merge(local: List<Note>, remote: List<Note>): List<Note> {
        val byId = local.associateBy { it.id }.toMutableMap()
        remote.forEach { note ->
            val existing = byId[note.id]
            if (existing != null && existing.isLocal) {
                // Local-only note must never be overwritten by remote data
                return@forEach
            }
            val isPurged = note.purged || (existing != null && existing.purged)
            if (isPurged) {
                if (existing != null && !existing.purged) {
                    cleanupMediaFiles(existing)
                }
                val delAt = maxOf(existing?.deletedAt ?: 0L, note.deletedAt, note.updatedAt)
                val upAt = maxOf(existing?.updatedAt ?: 0L, note.updatedAt)
                byId[note.id] = (existing ?: note).copy(
                    title = "",
                    content = "",
                    images = emptyList(),
                    audioUri = null,
                    audioDuration = 0L,
                    deleted = true,
                    purged = true,
                    deletedAt = delAt,
                    updatedAt = upAt,
                    isLocal = false
                )
                return@forEach
            }
            when {
                existing == null -> byId[note.id] = note
                note.updatedAt > existing.updatedAt -> {
                    val preservedArchived = if (!note.archived && existing.archived) existing.archived else note.archived
                    val isDeleted = note.deleted || existing.deleted
                    val delAt = if (isDeleted) maxOf(existing.deletedAt, note.deletedAt, if (existing.deleted) existing.updatedAt else note.updatedAt) else 0L
                    byId[note.id] = note.copy(
                        archived = preservedArchived,
                        deleted = isDeleted,
                        deletedAt = delAt,
                        isLocal = false
                    )
                }
                note.updatedAt == existing.updatedAt -> {
                    val isDeleted = existing.deleted || note.deleted
                    val delAt = if (isDeleted) maxOf(existing.deletedAt, note.deletedAt) else 0L
                    byId[note.id] = note.copy(
                        archived = existing.archived || note.archived,
                        pinned = existing.pinned || note.pinned,
                        deleted = isDeleted,
                        deletedAt = delAt,
                        isLocal = false
                    )
                }
                else -> {
                    // Local is strictly newer: preserve local note, but honor deletion if remote was deleted!
                    if (note.deleted && !existing.deleted) {
                        byId[note.id] = existing.copy(
                            deleted = true,
                            deletedAt = note.deletedAt,
                            updatedAt = note.updatedAt
                        )
                    }
                }
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private fun readNotes(): List<Note> {
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
                if (needsResave) {
                    val array = JSONArray()
                    fixed.forEach { array.put(serializeNote(it)) }
                    notesFile.writeText(array.toString())
                }
                fixed
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun deserializeNotes(json: String): List<Note> {
        val notes = mutableListOf<Note>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            notes.add(deserializeNote(array.getJSONObject(i)))
        }
        return notes
    }

    private fun deserializeNote(obj: JSONObject): Note {
        val encrypted = obj.optBoolean("enc", false)
        fun field(name: String): String {
            val raw = obj.getString(name)
            return if (encrypted) {
                try {
                    cipher.decrypt(raw)
                } catch (e: Exception) {
                    ""
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
                    if (encrypted) try { cipher.decrypt(raw) } catch (e: Exception) { "" } else raw
                }
            } ?: emptyList(),
            reminderAt = obj.optLong("reminderAt", 0),
            archived = obj.optBoolean("archived", false),
            isLocal = obj.optBoolean("isLocal", false),
            images = imagesList,
            audioUri = audioUriStr,
            audioDuration = obj.optLong("audioDuration", 0L),
            purged = obj.optBoolean("purged", false)
        )
    }

    private fun writeNotes(notes: List<Note>) {
        synchronized(cacheLock) {
            memoryCache = notes.toMutableList()
            try {
                val array = JSONArray()
                notes.forEach { array.put(serializeNote(it)) }
                notesFile.writeText(array.toString())
            } catch (e: Exception) {
                Log.w("AANO", "Suppressed writeNotes exception: ${e.message}", e)
            }
        }
    }

    private fun serializeNote(note: Note): JSONObject = JSONObject().apply {
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
        put("enc", true)
    }
}