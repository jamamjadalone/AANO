// Query and filter delegate for AANO note retrieval and FTS search.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

class NoteQueryDelegate(
    private val storage: NoteStorageDelegate,
    private val prefs: Prefs
) {

    fun sortNotes(notes: List<Note>): List<Note> {
        val comparator: Comparator<Note> = when (prefs.sortBy) {
            SortBy.CREATED_DESC -> compareByDescending { it.createdAt }
            SortBy.TITLE_ASC -> compareBy({ it.title.lowercase() }, { it.updatedAt })
            SortBy.PINNED_FIRST -> compareByDescending { it.updatedAt }
            SortBy.UPDATED_DESC -> compareByDescending { it.updatedAt }
        }
        val pinned = notes.filter { it.pinned }
        val rest = notes.filterNot { it.pinned }
        return pinned.sortedWith(comparator) + rest.sortedWith(comparator)
    }

    fun getAll(): List<Note> = storage.readNotes()

    fun getVisible(): List<Note> =
        sortNotes(storage.readNotes().filter { !it.deleted && !it.archived })

    fun getArchived(): List<Note> =
        sortNotes(storage.readNotes().filter { !it.deleted && it.archived })

    fun getById(id: String): Note? =
        storage.readNotes().find { it.id == id }

    fun search(query: String): List<Note> {
        val q = query.trim()
        val all = storage.readNotes().filter { !it.deleted && !it.archived }
        if (q.isEmpty()) return sortNotes(all)

        val ftsIds = storage.ftsIndex.search(q).toSet()
        val results = if (ftsIds.isNotEmpty()) {
            all.filter { it.id in ftsIds }
        } else {
            all.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.content.contains(q, ignoreCase = true) ||
                    it.folder.contains(q, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(q, ignoreCase = true) }
            }
        }
        return sortNotes(results)
    }

    fun folders(): List<String> {
        val fromNotes = storage.readNotes().filter { !it.deleted && !it.archived }
            .map { it.folder.trim() }
            .filter { it.isNotBlank() }
        val custom = prefs.customFolders.map { it.trim() }.filter { it.isNotBlank() }
        return (fromNotes + custom).distinct().sorted()
    }

    fun getFolderCounts(): Map<String, Int> {
        val all = storage.readNotes().filter { !it.deleted && !it.archived }
        val counts = mutableMapOf<String, Int>()
        prefs.customFolders.forEach { f ->
            val clean = f.trim()
            if (clean.isNotBlank()) counts[clean] = 0
        }
        all.forEach { note ->
            val f = note.folder.trim().ifBlank { "General" }
            counts[f] = (counts[f] ?: 0) + 1
        }
        return counts
    }

    fun getColorCounts(): Map<Int, Int> {
        val all = storage.readNotes().filter { !it.deleted && !it.archived }
        val counts = mutableMapOf<Int, Int>()
        all.forEach { note ->
            counts[note.color] = (counts[note.color] ?: 0) + 1
        }
        return counts
    }

    fun tags(): List<String> =
        storage.readNotes().filter { !it.deleted && !it.archived }
            .flatMap { it.tags }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    fun getVisibleInFolder(folder: String?): List<Note> {
        val all = storage.readNotes().filter { !it.deleted && !it.archived }
        val filtered = when {
            folder.isNullOrBlank() -> all
            folder.equals("General", ignoreCase = true) ->
                all.filter { it.folder.isBlank() || it.folder.equals("General", ignoreCase = true) }
            else -> all.filter { it.folder.equals(folder, ignoreCase = true) }
        }
        return sortNotes(filtered)
    }

    fun getLocalNotes(): List<Note> {
        val filtered = storage.readNotes().filter { !it.deleted && !it.archived && it.isLocal }
        return sortNotes(filtered)
    }

    fun getTrashed(): List<Note> =
        storage.readNotes().filter { it.deleted && !it.purged }.sortedByDescending { it.deletedAt }
}
