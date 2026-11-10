// Local note storage repository for AANO (facade delegating to modular storage, query, crud, and sync delegates).
// All note titles and contents are encrypted with AES-256-GCM using Android Keystore.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class SortBy { UPDATED_DESC, CREATED_DESC, TITLE_ASC, PINNED_FIRST }

class NoteRepository(val context: Context) {

    private val storage = NoteStorageDelegate(context)
    private val prefs = Prefs(context)
    private val queryDelegate = NoteQueryDelegate(storage, prefs)
    private val crudDelegate = NoteCrudDelegate(context, storage, prefs)
    private val syncTrashDelegate = NoteSyncTrashDelegate(context, storage)

    suspend fun getAll(): List<Note> = withContext(Dispatchers.IO) {
        queryDelegate.getAll()
    }

    suspend fun getVisible(): List<Note> = withContext(Dispatchers.IO) {
        queryDelegate.getVisible()
    }

    suspend fun getArchived(): List<Note> = withContext(Dispatchers.IO) {
        queryDelegate.getArchived()
    }

    suspend fun getById(id: String): Note? = withContext(Dispatchers.IO) {
        queryDelegate.getById(id)
    }

    suspend fun search(query: String): List<Note> = withContext(Dispatchers.IO) {
        queryDelegate.search(query)
    }

    suspend fun folders(): List<String> = withContext(Dispatchers.IO) {
        queryDelegate.folders()
    }

    suspend fun addFolder(name: String) = withContext(Dispatchers.IO) {
        crudDelegate.addFolder(name)
    }

    suspend fun renameFolder(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        crudDelegate.renameFolder(oldName, newName)
    }

    suspend fun deleteFolder(name: String, deleteNotes: Boolean = false) = withContext(Dispatchers.IO) {
        crudDelegate.deleteFolder(name, deleteNotes)
    }

    suspend fun getFolderCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        queryDelegate.getFolderCounts()
    }

    suspend fun getColorCounts(): Map<Int, Int> = withContext(Dispatchers.IO) {
        queryDelegate.getColorCounts()
    }

    suspend fun tags(): List<String> = withContext(Dispatchers.IO) {
        queryDelegate.tags()
    }

    suspend fun getVisibleInFolder(folder: String?): List<Note> = withContext(Dispatchers.IO) {
        queryDelegate.getVisibleInFolder(folder)
    }

    suspend fun getLocalNotes(): List<Note> = withContext(Dispatchers.IO) {
        queryDelegate.getLocalNotes()
    }

    suspend fun getTrashed(): List<Note> = withContext(Dispatchers.IO) {
        queryDelegate.getTrashed()
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
        crudDelegate.create(title, content, color, folder, tags, isLocal, images, audioUri, audioDuration)
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
    ) = withContext(Dispatchers.IO) {
        crudDelegate.update(id, title, content, color, folder, tags, isLocal, images, audioUri, audioDuration)
    }

    suspend fun toggleLocal(id: String) = withContext(Dispatchers.IO) {
        crudDelegate.toggleLocal(id)
    }

    suspend fun togglePin(id: String) = withContext(Dispatchers.IO) {
        crudDelegate.togglePin(id)
    }

    suspend fun upsertNote(note: Note) = withContext(Dispatchers.IO) {
        crudDelegate.upsertNote(note)
    }

    fun serialize(note: Note): JSONObject = storage.serializeNote(note)
    fun deserialize(obj: JSONObject): Note = storage.deserializeNote(obj)

    suspend fun setReminder(id: String, reminderAt: Long) = withContext(Dispatchers.IO) {
        crudDelegate.setReminder(id, reminderAt)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        crudDelegate.delete(id)
    }

    suspend fun deleteLocalPermanently(id: String) = withContext(Dispatchers.IO) {
        syncTrashDelegate.deleteLocalPermanently(id)
    }

    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        crudDelegate.restore(id)
    }

    suspend fun archive(id: String) = withContext(Dispatchers.IO) {
        crudDelegate.archive(id)
    }

    suspend fun unarchive(id: String) = withContext(Dispatchers.IO) {
        crudDelegate.unarchive(id)
    }

    suspend fun deleteMultiple(ids: Set<String>) = withContext(Dispatchers.IO) {
        crudDelegate.deleteMultiple(ids)
    }

    suspend fun archiveMultiple(ids: Set<String>) = withContext(Dispatchers.IO) {
        crudDelegate.archiveMultiple(ids)
    }

    suspend fun unarchiveMultiple(ids: Set<String>) = withContext(Dispatchers.IO) {
        crudDelegate.unarchiveMultiple(ids)
    }

    suspend fun setColorMultiple(ids: Set<String>, color: Int, colorIndex: Int = 0) = withContext(Dispatchers.IO) {
        crudDelegate.setColorMultiple(ids, color, colorIndex)
    }

    suspend fun setFolderMultiple(ids: Set<String>, folder: String) = withContext(Dispatchers.IO) {
        crudDelegate.setFolderMultiple(ids, folder)
    }

    suspend fun purge(id: String) = withContext(Dispatchers.IO) {
        syncTrashDelegate.purge(id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        syncTrashDelegate.emptyTrash()
    }

    suspend fun purgeExpiredTrash(maxAgeMs: Long) = withContext(Dispatchers.IO) {
        syncTrashDelegate.purgeExpiredTrash(maxAgeMs)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        syncTrashDelegate.clearAll()
    }

    suspend fun replaceAll(remote: List<Note>) = withContext(Dispatchers.IO) {
        syncTrashDelegate.replaceAll(remote)
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        syncTrashDelegate.exportJson()
    }

    suspend fun importJson(json: String) = withContext(Dispatchers.IO) {
        syncTrashDelegate.importJson(json)
    }

    suspend fun importLocalVaultNotes(restoredNotes: List<Note>) = withContext(Dispatchers.IO) {
        syncTrashDelegate.importLocalVaultNotes(restoredNotes)
    }
}