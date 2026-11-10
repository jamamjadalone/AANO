// Trash lifecycle, cloud sync serialization, and merge delegate for AANO notes.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import app.aa.aano.net.NoteReminderReceiver
import org.json.JSONArray
import org.json.JSONObject

class NoteSyncTrashDelegate(
    private val context: Context,
    private val storage: NoteStorageDelegate
) {

    fun deleteLocalPermanently(id: String) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            val note = notes[index]
            storage.cleanupMediaFiles(note)
            NoteReminderReceiver.cancel(context, id)
            notes.removeAt(index)
            storage.writeNotes(notes)
        }
    }

    fun purge(id: String) {
        val notes = storage.readNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            val note = notes[index]
            storage.cleanupMediaFiles(note)
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
            storage.writeNotes(notes)
        }
        NoteReminderReceiver.cancel(context, id)
    }

    fun emptyTrash() {
        val notes = storage.readNotes().toMutableList()
        val now = System.currentTimeMillis()
        val updated = mutableListOf<Note>()
        for (note in notes) {
            if (note.deleted && !note.purged) {
                storage.cleanupMediaFiles(note)
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
        storage.writeNotes(updated)
    }

    fun purgeExpiredTrash(maxAgeMs: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val all = storage.readNotes()
        val remaining = mutableListOf<Note>()
        for (note in all) {
            val isExpired = (note.deleted || note.purged) && note.deletedAt > 0 && note.deletedAt < cutoff
            if (isExpired) {
                storage.cleanupMediaFiles(note)
                NoteReminderReceiver.cancel(context, note.id)
            } else {
                remaining.add(note)
            }
        }
        storage.writeNotes(remaining)
    }

    fun clearAll() {
        storage.writeNotes(emptyList())
    }

    fun replaceAll(remote: List<Note>) {
        val local = storage.readNotes()
        val merged = merge(local, remote)
        storage.writeNotes(merged)
    }

    fun exportJson(): String = JSONArray().apply {
        storage.readNotes().filter { !it.isLocal }.forEach { put(serializeNoteForCloud(it)) }
    }.toString()

    fun importJson(json: String) {
        val remote = storage.deserializeNotes(json)
        storage.writeNotes(merge(storage.readNotes(), remote))
    }

    fun importLocalVaultNotes(restoredNotes: List<Note>) {
        val currentNotes = storage.readNotes().toMutableList()
        for (rn in restoredNotes) {
            val idx = currentNotes.indexOfFirst { it.id == rn.id }
            if (idx >= 0) {
                currentNotes[idx] = rn
            } else {
                currentNotes.add(0, rn)
            }
        }
        storage.writeNotes(currentNotes)
    }

    fun serializeNoteForCloud(note: Note): JSONObject = JSONObject().apply {
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

    fun merge(local: List<Note>, remote: List<Note>): List<Note> {
        val byId = local.associateBy { it.id }.toMutableMap()
        remote.forEach { note ->
            val existing = byId[note.id]
            if (existing != null && existing.isLocal) {
                return@forEach
            }
            val isPurged = note.purged || (existing != null && existing.purged)
            if (isPurged) {
                if (existing != null && !existing.purged) {
                    storage.cleanupMediaFiles(existing)
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
                    val isDeleted = if (note.deleted) true else (existing.deleted && existing.deletedAt > note.updatedAt)
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
                    if (note.deleted && !existing.deleted && note.deletedAt > existing.updatedAt) {
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
}
