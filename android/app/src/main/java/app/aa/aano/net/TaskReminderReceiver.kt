// Shows a notification when a task reminder fires.
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
import app.aa.aano.data.TaskRepository
import app.aa.aano.ui.TasksFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.task_reminder_title, taskTitle))
            .setContentText(taskTitle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(taskId.hashCode(), notification)
        // Create the next occurrence for recurring tasks.
        CoroutineScope(Dispatchers.IO).launch {
            val repo = TaskRepository(context)
            val task = repo.getById(taskId) ?: return@launch
            if (task.recurrence == app.aa.aano.data.Recurrence.NONE) return@launch
            scheduleNext(context, taskId, taskTitle)
        }
    }

    companion object {
        private const val CHANNEL_ID = "aano_task_reminders"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.task_reminder_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        fun schedule(context: Context, taskId: String, taskTitle: String, triggerAt: Long) {
            val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }

        fun cancel(context: Context, taskId: String) {
            val intent = Intent(context, TaskReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pending.cancel()
        }

        private const val MILLIS_DAY = 24L * 60 * 60 * 1000
    }

    private suspend fun scheduleNext(context: Context, taskId: String, taskTitle: String) {
        val repo = TaskRepository(context)
        val task = repo.getById(taskId) ?: return
        val base = task.dueAt ?: System.currentTimeMillis()
        val next = when (task.recurrence) {
            app.aa.aano.data.Recurrence.DAILY -> base + MILLIS_DAY
            app.aa.aano.data.Recurrence.WEEKLY -> base + MILLIS_DAY * 7
            app.aa.aano.data.Recurrence.MONTHLY -> base + MILLIS_DAY * 30
            app.aa.aano.data.Recurrence.NONE -> return
        }
        schedule(context, taskId, taskTitle, next)
    }
}