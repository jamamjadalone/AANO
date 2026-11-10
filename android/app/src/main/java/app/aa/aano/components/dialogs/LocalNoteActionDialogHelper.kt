// LocalNoteActionDialogHelper: Handles long-click context actions for local notes
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.ui.EditorActivity

object LocalNoteActionDialogHelper {

    fun showLocalNoteActions(
        context: Context,
        scope: CoroutineScope,
        note: Note,
        repository: NoteRepository,
        prefs: Prefs,
        onDropRequested: (targetDevice: String) -> Unit,
        onMultiSelectRequested: () -> Unit,
        onRefreshNeeded: () -> Unit,
        onShareRequested: (note: Note) -> Unit
    ) {
        val targetDevice = prefs.lastDropIp
        val dropLabel = if (targetDevice.isNotBlank()) "⚡ Send to Device ($targetDevice)" else "⚡ Connect & Drop (AANO Drop)"

        val items = arrayOf(
            dropLabel,
            "☑️ Multi-Select Notes",
            if (note.pinned) "📌 Unpin Note" else "📌 Pin to Top",
            "☁️ Move to Cloud Notes",
            "🎨 Change Color",
            context.getString(R.string.share_note),
            "🗑️ Delete Note (Permanently)"
        )

        AlertDialog.Builder(context)
            .setTitle(note.title.ifBlank { "(No title)" })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> onDropRequested(targetDevice)
                    1 -> onMultiSelectRequested()
                    2 -> scope.launch {
                        repository.togglePin(note.id)
                        onRefreshNeeded()
                    }
                    3 -> scope.launch {
                        repository.toggleLocal(note.id)
                        onRefreshNeeded()
                        Toast.makeText(context, "Moved to Cloud Notes", Toast.LENGTH_SHORT).show()
                    }
                    4 -> showChangeColorDialog(context, scope, note, repository, onRefreshNeeded)
                    5 -> onShareRequested(note)
                    6 -> {
                        AlertDialog.Builder(context)
                            .setTitle("Delete Local Note")
                            .setMessage("Permanently delete this local note and attached photos/voice memos from this device? This action cannot be undone.")
                            .setPositiveButton("Delete Permanently") { _, _ ->
                                scope.launch {
                                    repository.deleteLocalPermanently(note.id)
                                    onRefreshNeeded()
                                    Toast.makeText(context, "Local note permanently deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            }
            .show()
    }

    fun showChangeColorDialog(
        context: Context,
        scope: CoroutineScope,
        note: Note,
        repository: NoteRepository,
        onSuccess: () -> Unit
    ) {
        val colors = EditorActivity.presetColors()
        val names = arrayOf("Default", "Coral", "Peach", "Sand", "Mint", "Sage", "Sky", "Lavender", "Blush", "Clay")
        AlertDialog.Builder(context)
            .setTitle("Change Color")
            .setItems(names) { _, which ->
                val newColor = colors[which]
                scope.launch {
                    repository.update(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        color = newColor,
                        folder = note.folder,
                        tags = note.tags
                    )
                    onSuccess()
                }
            }
            .show()
    }

    fun buildShareIntent(context: Context, note: Note): Intent {
        val text = buildString {
            if (note.title.isNotBlank()) append(note.title).append("\n\n")
            append(note.content)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, context.getString(R.string.share_note))
    }
}
