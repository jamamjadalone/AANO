// NoteWidgetProvider: Resizable sticky-note widget for Android home screen.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.data.NoteRepository
import app.aa.aano.pages.EditorActivity
import app.aa.aano.pages.HomeActivity

class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                updateWidgets(context, manager, ids)
            }
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            CoroutineScope(Dispatchers.IO).launch {
                val repo = NoteRepository(context)
                val visible = repo.getVisible()
                val latest = visible.firstOrNull { it.pinned } ?: visible.firstOrNull()

                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_note)
                    if (latest != null) {
                        views.setTextViewText(R.id.widgetTitle, latest.title.ifBlank { "(No title)" })
                        val plain = if (latest.content.contains("<") && latest.content.contains(">")) {
                            android.text.Html.fromHtml(latest.content, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
                        } else latest.content.trim()
                        views.setTextViewText(R.id.widgetContent, plain.ifBlank { "(Empty note)" })

                        val intent = Intent(context, EditorActivity::class.java).apply {
                            putExtra(EditorActivity.EXTRA_NOTE_ID, latest.id)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        val pending = PendingIntent.getActivity(
                            context, id, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widgetCard, pending)
                    } else {
                        views.setTextViewText(R.id.widgetTitle, "AANO Notes")
                        views.setTextViewText(R.id.widgetContent, "Tap to create your first note")
                        val intent = Intent(context, HomeActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        val pending = PendingIntent.getActivity(
                            context, id, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widgetCard, pending)
                    }
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }
}
