// CRUD and folder management delegate for AANO note mutation.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import app.aa.aano.net.NoteReminderReceiver
import java.util.UUID

class NoteCrudDelegate(
    private val context: Context,
    private val storage: NoteStorageDelegate,
    private val prefs: Prefs
) {

    fun create(
        title: String,
        content: String,
        color: Int = Note.COLOR_DEFAULT,
        folder: String = "",
        tags: List<String> = emptyList(),
        isLocal: Boolean = false,
        images: List<String> = emptyList(),
        audioUri: String? = null,
        audioDuration: Long = 0L
    ): Note {
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
        val notes = storage.readNotes().toMutableList()
        notes.add(0, note)
        storage.writeNotes(notes)
        return note
    }

    fun update(
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
        val notes = storage.readNotes().toMutableList()
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
            storage.writeNotes(notes)
        }
    }

    fun toggleLocal(id: String) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            notes[index] = notes[index].copy(
                isLocal = !notes[index].isLocal,
                updatedAt = System.currentTimeMillis()
            )
            storage.writeNotes(notes)
        }
    }

    fun togglePin(id: String) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            notes[index] = notes[index].copy(
                pinned = !notes[index].pinned,
                minorModifiedDate = System.currentTimeMillis()
            )
            storage.writeNotes(notes)
        }
    }

    fun upsertNote(note: Note) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == note.id }
        if (index >= 0) {
            notes[index] = note
        } else {
            notes.add(0, note)
        }
        storage.writeNotes(notes)
    }

    fun setReminder(id: String, reminderAt: Long) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            notes[index] = notes[index].copy(
                reminderAt = reminderAt,
                minorModifiedDate = System.currentTimeMillis()
            )
            storage.writeNotes(notes)
        }
    }

    fun delete(id: String) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            val now = maxOf(System.currentTimeMillis(), notes[index].updatedAt + 1000L)
            notes[index] = notes[index].copy(
                deleted = true,
                deletedAt = now,
                updatedAt = now
            )
            storage.writeNotes(notes)
        }
        NoteReminderReceiver.cancel(context, id)
    }

    fun restore(id: String) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            val now = maxOf(System.currentTimeMillis(), notes[index].updatedAt + 1000L)
            notes[index] = notes[index].copy(deleted = false, deletedAt = 0, updatedAt = now)
            storage.writeNotes(notes)
        }
    }

    fun archive(id: String) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            val now = maxOf(System.currentTimeMillis(), notes[index].updatedAt + 1000L)
            notes[index] = notes[index].copy(archived = true, updatedAt = now)
            storage.writeNotes(notes)
        }
    }

    fun unarchive(id: String) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            val now = maxOf(System.currentTimeMillis(), notes[index].updatedAt + 1000L)
            notes[index] = notes[index].copy(archived = false, updatedAt = now)
            storage.writeNotes(notes)
        }
    }

    fun deleteMultiple(ids: Set<String>) {
        val notes = storage.readNotes().toMutableList()
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
        if (modified) storage.writeNotes(notes)
    }

    fun archiveMultiple(ids: Set<String>) {
        val notes = storage.readNotes().toMutableList()
        var modified = false
        for (i in notes.indices) {
            if (ids.contains(notes[i].id)) {
                val now = maxOf(System.currentTimeMillis(), notes[i].updatedAt + 1000L)
                notes[i] = notes[i].copy(archived = true, updatedAt = now)
                modified = true
            }
        }
        if (modified) storage.writeNotes(notes)
    }

    fun unarchiveMultiple(ids: Set<String>) {
        val notes = storage.readNotes().toMutableList()
        var modified = false
        for (i in notes.indices) {
            if (ids.contains(notes[i].id)) {
                val now = maxOf(System.currentTimeMillis(), notes[i].updatedAt + 1000L)
                notes[i] = notes[i].copy(archived = false, updatedAt = now)
                modified = true
            }
        }
        if (modified) storage.writeNotes(notes)
    }

    fun addFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            val updated = prefs.customFolders.toMutableSet()
            updated.add(trimmed)
            prefs.customFolders = updated
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        if (trimmedNew.isNotBlank() && trimmedOld != trimmedNew) {
            val updatedFolders = prefs.customFolders.toMutableSet()
            updatedFolders.remove(trimmedOld)
            updatedFolders.add(trimmedNew)
            prefs.customFolders = updatedFolders

            val notes = storage.readNotes().toMutableList()
            var modified = false
            for (i in notes.indices) {
                if (notes[i].folder.equals(trimmedOld, ignoreCase = true)) {
                    notes[i] = notes[i].copy(folder = trimmedNew, updatedAt = System.currentTimeMillis())
                    modified = true
                }
            }
            if (modified) storage.writeNotes(notes)
        }
    }

    fun deleteFolder(name: String, deleteNotes: Boolean = false) {
        val trimmed = name.trim()
        val updatedFolders = prefs.customFolders.toMutableSet()
        updatedFolders.remove(trimmed)
        prefs.customFolders = updatedFolders

        val notes = storage.readNotes().toMutableList()
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
        if (modified) storage.writeNotes(notes)
    }

    fun setColorMultiple(ids: Set<String>, color: Int, colorIndex: Int = 0) {
        val notes = storage.readNotes().toMutableList()
        var modified = false
        for (i in notes.indices) {
            if (ids.contains(notes[i].id)) {
                val now = maxOf(System.currentTimeMillis(), notes[i].updatedAt + 1000L)
                notes[i] = notes[i].copy(color = color, colorIndex = colorIndex, updatedAt = now)
                modified = true
            }
        }
        if (modified) storage.writeNotes(notes)
    }

    fun setFolderMultiple(ids: Set<String>, folder: String) {
        val notes = storage.readNotes().toMutableList()
        var modified = false
        for (i in notes.indices) {
            if (ids.contains(notes[i].id)) {
                val now = maxOf(System.currentTimeMillis(), notes[i].updatedAt + 1000L)
                notes[i] = notes[i].copy(folder = folder, updatedAt = now)
                modified = true
            }
        }
        if (modified) storage.writeNotes(notes)
    }
}
