// NoteActionDialogHelper: Note item actions, folder moving, sorting and sharing dialogs
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.SortBy
import app.aa.aano.net.CloudSyncManager

object NoteActionDialogHelper {

    fun showNoteActions(
        context: Context,
        scope: CoroutineScope,
        note: Note,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onDropRequested: (note: Note) -> Unit,
        onMultiSelectRequested: (note: Note) -> Unit,
        onChangeColorRequested: (note: Note) -> Unit,
        onRefreshNeeded: () -> Unit,
        onSyncNeeded: () -> Unit
    ) {
        val pairedIp = prefs.lastDropIp
        val peerName = prefs.lastDropPeerName.ifBlank { "Connected Device" }
        val dropLabel = if (pairedIp.isNotBlank()) "⚡ Send to $peerName ($pairedIp)" else "⚡ AANO Drop (Send to PC/Phone)"

        val items = arrayOf(
            dropLabel,
            "☑️ Multi-Select Mode",
            if (note.pinned) "📌 Unpin Note" else "📌 Pin to Top",
            if (note.isLocal) "☁️ Move to Cloud Notes" else "⚡ Move to On-Device (Local Vault)",
            "📦 Archive Note",
            "📁 Move to Folder",
            "🎨 Change Color",
            context.getString(R.string.share_note),
            context.getString(R.string.move_to_trash)
        )

        AlertDialog.Builder(context)
            .setTitle(note.title.ifBlank { "(No title)" })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> onDropRequested(note)
                    1 -> onMultiSelectRequested(note)
                    2 -> scope.launch {
                        repository.togglePin(note.id)
                        onRefreshNeeded()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) onSyncNeeded()
                    }
                    3 -> scope.launch {
                        repository.toggleLocal(note.id)
                        onRefreshNeeded()
                        Toast.makeText(context, if (note.isLocal) "Moved to Cloud Notes" else "Moved to On-Device Vault", Toast.LENGTH_SHORT).show()
                    }
                    4 -> scope.launch {
                        repository.archive(note.id)
                        onRefreshNeeded()
                        Toast.makeText(context, "Note archived", Toast.LENGTH_SHORT).show()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) onSyncNeeded()
                    }
                    5 -> showMoveFolderDialog(context, scope, note, repository, prefs, cloudSyncManager, onRefreshNeeded, onSyncNeeded)
                    6 -> onChangeColorRequested(note)
                    7 -> shareNote(context, note)
                    8 -> scope.launch {
                        repository.delete(note.id)
                        onRefreshNeeded()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) onSyncNeeded()
                    }
                }
            }
            .show()
    }

    fun showMoveFolderDialog(
        context: Context,
        scope: CoroutineScope,
        note: Note,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onRefreshNeeded: () -> Unit,
        onSyncNeeded: () -> Unit
    ) {
        scope.launch {
            val folders = repository.folders().toMutableList()
            if (!folders.contains("Work")) folders.add("Work")
            if (!folders.contains("Personal")) folders.add("Personal")
            if (!folders.contains("Ideas")) folders.add("Ideas")

            val options = (listOf("General (None)") + folders + listOf("+ Create New Folder")).toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Move to Folder")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> updateNoteFolder(scope, note, "", repository, prefs, cloudSyncManager, onRefreshNeeded, onSyncNeeded)
                        options.size - 1 -> showCreateFolderDialog(context, scope, note, repository, prefs, cloudSyncManager, onRefreshNeeded, onSyncNeeded)
                        else -> updateNoteFolder(scope, note, options[which], repository, prefs, cloudSyncManager, onRefreshNeeded, onSyncNeeded)
                    }
                }
                .show()
        }
    }

    private fun showCreateFolderDialog(
        context: Context,
        scope: CoroutineScope,
        note: Note,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onRefreshNeeded: () -> Unit,
        onSyncNeeded: () -> Unit
    ) {
        val input = EditText(context).apply {
            hint = "Folder Name"
            setSingleLine()
        }
        AlertDialog.Builder(context)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    updateNoteFolder(scope, note, name, repository, prefs, cloudSyncManager, onRefreshNeeded, onSyncNeeded)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateNoteFolder(
        scope: CoroutineScope,
        note: Note,
        newFolder: String,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onRefreshNeeded: () -> Unit,
        onSyncNeeded: () -> Unit
    ) {
        scope.launch {
            repository.update(
                id = note.id,
                title = note.title,
                content = note.content,
                color = note.color,
                folder = newFolder,
                tags = note.tags
            )
            onRefreshNeeded()
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                onSyncNeeded()
            }
        }
    }

    fun showSortDialog(context: Context, prefs: Prefs, onSortChanged: () -> Unit) {
        val current = prefs.sortBy
        val options = arrayOf(
            context.getString(R.string.sort_updated),
            context.getString(R.string.sort_created),
            context.getString(R.string.sort_title),
            context.getString(R.string.sort_pinned)
        )
        val selected = when (current) {
            SortBy.UPDATED_DESC -> 0
            SortBy.CREATED_DESC -> 1
            SortBy.TITLE_ASC -> 2
            SortBy.PINNED_FIRST -> 3
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, selected) { dialog, which ->
                prefs.sortBy = when (which) {
                    0 -> SortBy.UPDATED_DESC
                    1 -> SortBy.CREATED_DESC
                    2 -> SortBy.TITLE_ASC
                    else -> SortBy.PINNED_FIRST
                }
                dialog.dismiss()
                onSortChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun shareNote(context: Context, note: Note) {
        val text = buildString {
            if (note.title.isNotBlank()) append(note.title).append("\n\n")
            append(note.content)
        }
        if (text.isBlank()) {
            Toast.makeText(context, R.string.note_empty_share, Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_note)))
    }
}
