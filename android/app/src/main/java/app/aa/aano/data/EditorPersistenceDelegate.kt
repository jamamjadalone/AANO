// EditorPersistenceDelegate: Handles note model construction, HTML span serialization and persistence
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.text.Editable
import android.text.Html
import android.text.style.CharacterStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

object EditorPersistenceDelegate {

    fun buildNote(
        noteId: String?,
        title: String,
        content: String,
        folder: String,
        color: Int,
        images: List<String>,
        audioUri: String?,
        audioDuration: Long,
        isLocalNote: Boolean
    ): Note {
        val now = System.currentTimeMillis()
        val cleanTitle = if (title.isNotBlank()) title else "Untitled Note"
        return Note(
            id = noteId ?: UUID.randomUUID().toString(),
            title = cleanTitle,
            content = content,
            folder = folder.ifBlank { "General" },
            color = color,
            images = images.toList(),
            audioUri = audioUri,
            audioDuration = audioDuration,
            pinned = false,
            isLocal = isLocalNote,
            createdAt = now,
            updatedAt = now
        )
    }

    suspend fun persistNote(
        repository: NoteRepository,
        noteId: String?,
        rawTitle: String?,
        editableContent: Editable?,
        folder: String,
        tags: List<String>,
        color: Int,
        isLocalNote: Boolean,
        images: List<String>,
        audioUri: String?,
        audioDuration: Long
    ): String = withContext(Dispatchers.IO + NonCancellable) {
        val title = rawTitle?.trim().orEmpty()
        val content = if (editableContent != null && editableContent.getSpans(0, editableContent.length, CharacterStyle::class.java).isNotEmpty()) {
            Html.toHtml(editableContent, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        } else {
            editableContent?.toString().orEmpty()
        }
        val cleanFolder = folder.trim()

        val savedId = if (noteId != null) {
            repository.update(
                id = noteId,
                title = title,
                content = content,
                color = color,
                folder = cleanFolder,
                tags = tags,
                isLocal = isLocalNote,
                images = images,
                audioUri = audioUri,
                audioDuration = audioDuration
            )
            noteId
        } else {
            val created = repository.create(
                title = title,
                content = content,
                color = color,
                folder = cleanFolder,
                tags = tags,
                isLocal = isLocalNote,
                images = images,
                audioUri = audioUri,
                audioDuration = audioDuration
            )
            created.id
        }
        app.aa.aano.service.NoteWidgetProvider.updateAll(repository.context)
        savedId
    }

    fun isNoteEmpty(
        title: CharSequence?,
        content: CharSequence?,
        images: List<String>,
        audioUri: String?
    ): Boolean =
        title.isNullOrEmpty() && content.isNullOrEmpty() && images.isEmpty() && audioUri.isNullOrEmpty()
}
