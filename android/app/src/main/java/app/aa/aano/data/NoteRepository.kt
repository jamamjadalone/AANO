// Local note storage for AANO (JSON file in app-private storage).
// All note titles and contents are encrypted with AES-256-GCM using a key
// held in the Android Keystore (see NoteCipher).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import app.aa.aano.net.NoteReminderReceiver

enum class SortBy { UPDATED_DESC, CREATED_DESC, TITLE_ASC, PINNED_FIRST }

class NoteRepository(private val context: Context) {

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
        readNotes().flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()
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

    suspend fun getTrashed(): List<Note> = withContext(Dispatchers.IO) {
        readNotes().filter { it.deleted }.sortedByDescending { it.deletedAt }
    }

    suspend fun create(
        title: String,
        content: String,
        color: Int = Note.COLOR_DEFAULT,
        folder: String = "",
        tags: List<String> = emptyList()
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
            tags = tags
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
        tags: List<String> = emptyList()
    ) {
        withContext(Dispatchers.IO) {
            val notes = readNotes().toMutableList()
            val index = notes.indexOfFirst { it.id == id }
            if (index >= 0) {
                notes[index] = notes[index].copy(
                    title = title,
                    content = content,
                    color = color,
                    folder = folder,
                    tags = tags,
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

    suspend fun purge(id: String) {
        withContext(Dispatchers.IO) {
            writeNotes(readNotes().filterNot { it.id == id })
            NoteReminderReceiver.cancel(context, id)
        }
    }

    suspend fun purgeExpiredTrash(maxAgeMs: Long) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            val all = readNotes()
            all.filter { it.deleted && it.deletedAt > 0 && it.deletedAt < cutoff }
                .forEach { NoteReminderReceiver.cancel(context, it.id) }
            writeNotes(all.filterNot { it.deleted && it.deletedAt > 0 && it.deletedAt < cutoff })
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
            readNotes().forEach { put(serializeNoteForCloud(it)) }
        }.toString()
    }

    suspend fun importJson(json: String) {
        withContext(Dispatchers.IO) {
            val remote = deserializeNotes(json)
            writeNotes(merge(readNotes(), remote))
        }
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
            when {
                existing == null -> byId[note.id] = note
                note.updatedAt > existing.updatedAt -> {
                    val preservedArchived = if (!note.archived && existing.archived) existing.archived else note.archived
                    byId[note.id] = note.copy(archived = preservedArchived)
                }
                note.updatedAt == existing.updatedAt -> {
                    byId[note.id] = note.copy(
                        archived = existing.archived || note.archived,
                        pinned = existing.pinned || note.pinned
                    )
                }
                else -> {
                    // Local is strictly newer: preserve local note!
                }
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private fun readNotes(): List<Note> {
        if (!notesFile.exists()) return emptyList()
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
            if (needsResave) {
                writeNotes(fixed)
            }
            fixed
        } catch (e: Exception) {
            emptyList()
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
            archived = obj.optBoolean("archived", false)
        )
    }

    private fun writeNotes(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { array.put(serializeNote(it)) }
        notesFile.writeText(array.toString())
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
        put("enc", true)
    }
}