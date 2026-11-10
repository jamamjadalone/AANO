// Shows a notification when a note reminder fires.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.net

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.aa.aano.R
import app.aa.aano.pages.EditorActivity

class NoteReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val noteTitle = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "Note Reminder"
        val reminderType = intent.getIntExtra(EXTRA_REMINDER_TYPE, app.aa.aano.data.Note.REMINDER_TIME)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (action == ACTION_UNPIN) {
            manager.cancel(noteId.hashCode())
            return
        }

        if (action == ACTION_SNOOZE_15M) {
            manager.cancel(noteId.hashCode())
            val snoozeAt = System.currentTimeMillis() + 15 * 60 * 1000L
            schedule(context, noteId, noteTitle, snoozeAt, app.aa.aano.data.Note.REMINDER_TIME)
            return
        }

        ensureChannel(context)

        val openIntent = Intent(context, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            noteId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isOngoing = reminderType == app.aa.aano.data.Note.REMINDER_STATUS_PIN
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isOngoing) android.R.drawable.ic_menu_agenda else android.R.drawable.ic_popup_reminder)
            .setContentTitle(noteTitle.ifBlank { context.getString(R.string.app_name) })
            .setContentText(if (isOngoing) "📌 Pinned Note — tap to edit" else context.getString(R.string.note_reminder_text))
            .setContentIntent(pendingOpen)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isOngoing) {
            val unpinIntent = Intent(context, NoteReminderReceiver::class.java).apply {
                this.action = ACTION_UNPIN
                putExtra(EXTRA_NOTE_ID, noteId)
            }
            val pendingUnpin = PendingIntent.getBroadcast(
                context,
                (noteId + "_unpin").hashCode(),
                unpinIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Unpin", pendingUnpin)
        } else {
            val snoozeIntent = Intent(context, NoteReminderReceiver::class.java).apply {
                this.action = ACTION_SNOOZE_15M
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_NOTE_TITLE, noteTitle)
            }
            val pendingSnooze = PendingIntent.getBroadcast(
                context,
                (noteId + "_snooze").hashCode(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_popup_reminder, "Snooze 15m", pendingSnooze)
        }

        manager.notify(noteId.hashCode(), builder.build())

        if (reminderType == app.aa.aano.data.Note.REMINDER_REPEAT) {
            val nextCycle = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            schedule(context, noteId, noteTitle, nextCycle, app.aa.aano.data.Note.REMINDER_REPEAT)
        }
    }

    companion object {
        private const val CHANNEL_ID = "aano_note_reminders"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_NOTE_TITLE = "note_title"
        const val EXTRA_REMINDER_TYPE = "reminder_type"
        const val ACTION_UNPIN = "app.aa.aano.action.UNPIN_REMINDER"
        const val ACTION_SNOOZE_15M = "app.aa.aano.action.SNOOZE_15M"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_note_reminders),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "AANO Note and Status Bar Reminders"
                    enableVibration(true)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        fun schedule(context: Context, noteId: String, noteTitle: String, triggerAt: Long, reminderType: Int = app.aa.aano.data.Note.REMINDER_TIME) {
            val intent = Intent(context, NoteReminderReceiver::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_NOTE_TITLE, noteTitle)
                putExtra(EXTRA_REMINDER_TYPE, reminderType)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                noteId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }

        fun pinToStatusBar(context: Context, noteId: String, noteTitle: String) {
            val intent = Intent(context, NoteReminderReceiver::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_NOTE_TITLE, noteTitle)
                putExtra(EXTRA_REMINDER_TYPE, app.aa.aano.data.Note.REMINDER_STATUS_PIN)
            }
            context.sendBroadcast(intent)
        }

        fun cancel(context: Context, noteId: String) {
            val intent = Intent(context, NoteReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                noteId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(pending)
            pending.cancel()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(noteId.hashCode())
        }
    }
}