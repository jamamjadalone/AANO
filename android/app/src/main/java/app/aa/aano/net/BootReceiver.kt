// Reschedules alarms after system reboot or package update.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                
                // 1. Reschedule note reminders
                val noteRepo = NoteRepository(context)
                val notes = noteRepo.getAll()
                var rescheduledNotes = 0
                for (note in notes) {
                    if (!note.deleted && !note.archived) {
                        val trigger = if (note.reminderDate > 0) note.reminderDate else note.reminderAt
                        if (note.reminderType == app.aa.aano.data.Note.REMINDER_STATUS_PIN) {
                            NoteReminderReceiver.pinToStatusBar(context, note.id, note.title)
                            rescheduledNotes++
                        } else if (trigger > now) {
                            NoteReminderReceiver.schedule(context, note.id, note.title, trigger, note.reminderType)
                            rescheduledNotes++
                        }
                    }
                }

                // 2. Reschedule task reminders
                val taskRepo = TaskRepository(context)
                val tasks = taskRepo.getAll()
                var rescheduledTasks = 0
                for (task in tasks) {
                    val remAt = task.reminderAt ?: 0L
                    if (!task.completed && remAt > now) {
                        TaskReminderReceiver.schedule(context, task.id, task.title, remAt)
                        rescheduledTasks++
                    }
                }

                Log.i(TAG, "Boot reschedule completed: $rescheduledNotes note reminders, $rescheduledTasks task reminders restored.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule reminders on boot: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AANO.BootReceiver"
    }
}
