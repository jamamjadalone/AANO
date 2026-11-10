// EditorDialogHelper: Dialogs for folder selection, note deletion, reminders, sharing & archiving
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import app.aa.aano.R
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.net.CloudSyncManager
import app.aa.aano.net.NoteReminderReceiver

object EditorDialogHelper {

    fun showFolderPickerDialog(
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        onFolderSelected: (String) -> Unit,
        onCreateFolderRequested: () -> Unit
    ) {
        scope.launch {
            val folders = repository.folders().toMutableList()
            if (!folders.contains("General")) folders.add(0, "General")
            if (!folders.contains("Work")) folders.add("Work")
            if (!folders.contains("Personal")) folders.add("Personal")

            val options = (listOf("General (None)") + folders.filter { it != "General" } + listOf("+ Create New Folder")).toTypedArray()

            MaterialAlertDialogBuilder(context)
                .setTitle("Select Folder")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onFolderSelected("")
                        options.size - 1 -> onCreateFolderRequested()
                        else -> onFolderSelected(options[which])
                    }
                }
                .show()
        }
    }

    fun showCreateFolderDialog(
        context: Context,
        onFolderCreated: (String) -> Unit
    ) {
        val input = EditText(context).apply {
            hint = "Folder name"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    onFolderCreated(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun archiveNote(
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        noteId: String?,
        onFinish: () -> Unit
    ) {
        val currentId = noteId ?: return
        scope.launch {
            repository.archive(currentId)
            Toast.makeText(context, "Note archived", Toast.LENGTH_SHORT).show()
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        cloudSyncManager.syncNow()
                    } catch (e: Exception) {
                        Log.w("AANO", "Background sync error: ${e.message}")
                    }
                }
            }
            onFinish()
        }
    }

    fun confirmDeleteNote(
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        noteId: String?,
        isLocalNote: Boolean,
        onFinish: () -> Unit
    ) {
        val currentId = noteId
        if (currentId == null) {
            onFinish()
            return
        }
        if (isLocalNote) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Delete Local Note")
                .setMessage("Permanently delete this local note and attached photos/voice memos from this device? This action cannot be undone.")
                .setPositiveButton("Delete Permanently") { _, _ ->
                    scope.launch {
                        repository.deleteLocalPermanently(currentId)
                        onFinish()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_note)
            .setMessage("Are you sure you want to delete this note? It will be removed across all synced devices.")
            .setPositiveButton(R.string.delete_note) { _, _ ->
                scope.launch {
                    repository.delete(currentId)
                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                cloudSyncManager.syncNow()
                            } catch (e: Exception) {
                                Log.w("AANO", "Background sync error: ${e.message}")
                            }
                        }
                    }
                    onFinish()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun pickReminder(
        context: Context,
        onTimeSelected: (Long) -> Unit
    ) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                TimePickerDialog(
                    context,
                    { _, h, min ->
                        val target = Calendar.getInstance()
                        target.set(y, m, d, h, min, 0)
                        val at = target.timeInMillis
                        if (at < System.currentTimeMillis()) {
                            Toast.makeText(context, R.string.reminder_past, Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }
                        onTimeSelected(at)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun show4TierReminderDialog(
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        noteId: String,
        title: String,
        onReminderUpdated: (Long, Int) -> Unit
    ) {
        val options = arrayOf(
            "📅 Today (9:00 AM)",
            "⏰ Exact Date & Time",
            "🔁 Repeat (Daily)",
            "📌 Pin to Status Bar",
            "❌ Clear Reminder"
        )
        MaterialAlertDialogBuilder(context)
            .setTitle("Schedule Reminder")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        val at = cal.timeInMillis
                        scheduleNoteReminder(context, scope, repository, noteId, title, at, app.aa.aano.data.Note.REMINDER_ALL_DAY)
                        onReminderUpdated(at, app.aa.aano.data.Note.REMINDER_ALL_DAY)
                    }
                    1 -> pickReminder(context) { at ->
                        scheduleNoteReminder(context, scope, repository, noteId, title, at, app.aa.aano.data.Note.REMINDER_TIME)
                        onReminderUpdated(at, app.aa.aano.data.Note.REMINDER_TIME)
                    }
                    2 -> {
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                        val at = cal.timeInMillis
                        scheduleNoteReminder(context, scope, repository, noteId, title, at, app.aa.aano.data.Note.REMINDER_REPEAT)
                        onReminderUpdated(at, app.aa.aano.data.Note.REMINDER_REPEAT)
                    }
                    3 -> {
                        NoteReminderReceiver.pinToStatusBar(context, noteId, title)
                        scope.launch { repository.setReminder(noteId, System.currentTimeMillis()) }
                        onReminderUpdated(System.currentTimeMillis(), app.aa.aano.data.Note.REMINDER_STATUS_PIN)
                        Toast.makeText(context, "Note pinned to status bar", Toast.LENGTH_SHORT).show()
                    }
                    4 -> {
                        NoteReminderReceiver.cancel(context, noteId)
                        scope.launch { repository.setReminder(noteId, 0L) }
                        onReminderUpdated(0L, app.aa.aano.data.Note.REMINDER_NONE)
                        Toast.makeText(context, "Reminder cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    fun scheduleNoteReminder(
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        noteId: String,
        title: String,
        at: Long,
        type: Int = app.aa.aano.data.Note.REMINDER_TIME
    ) {
        scope.launch {
            repository.setReminder(noteId, at)
            NoteReminderReceiver.schedule(context, noteId, title, at, type)
            Toast.makeText(context, context.getString(R.string.reminder_set), Toast.LENGTH_SHORT).show()
        }
    }

    fun shareNote(context: Context, title: String, content: String) {
        val text = buildString {
            if (title.isNotBlank()) append(title).append("\n\n")
            append(content)
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
