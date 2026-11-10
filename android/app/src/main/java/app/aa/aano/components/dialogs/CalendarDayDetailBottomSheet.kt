// CalendarDayDetailBottomSheet: Bottom sheet displaying notes and reminders scheduled on a specific date
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.pages.EditorActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CalendarDayDetailBottomSheet {

    fun show(
        context: Context,
        year: Int,
        month: Int,
        day: Int,
        notes: List<Note>,
        onNoteChanged: () -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val cal = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
        val dateTitle = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(cal.time)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 48)
        }

        val tvTitle = TextView(context).apply {
            text = dateTitle
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.on_surface))
        }
        root.addView(tvTitle)

        val tvSub = TextView(context).apply {
            text = "${notes.size} note(s) for this day"
            textSize = 13f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 4, 0, 24)
        }
        root.addView(tvSub)

        if (notes.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "No notes or reminders on this date."
                textSize = 14f
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 16, 0, 32)
            }
            root.addView(emptyTv)
        } else {
            for (note in notes) {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(24, 20, 24, 20)
                    val noteColor = EditorActivity.resolveNoteColor(context, note.color)
                    setBackgroundColor(noteColor)
                    setOnClickListener {
                        dialog.dismiss()
                        context.startActivity(
                            Intent(context, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE_ID, note.id)
                        )
                    }
                }

                val titleTv = TextView(context).apply {
                    text = note.title.ifBlank { "(Untitled Note)" }
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(0xFF1C1B1F.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                card.addView(titleTv)

                if (note.reminderType != app.aa.aano.data.Note.REMINDER_NONE) {
                    val remTv = TextView(context).apply {
                        text = "⏰"
                        textSize = 14f
                    }
                    card.addView(remTv)
                }

                val marginParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
                root.addView(card, marginParams)
            }
        }

        val btnAdd = MaterialButton(context).apply {
            text = "+ Add Note for this Date"
            setOnClickListener {
                dialog.dismiss()
                val targetMillis = cal.timeInMillis
                context.startActivity(
                    Intent(context, EditorActivity::class.java).putExtra("EXTRA_CALENDAR_DATE", targetMillis)
                )
            }
        }
        root.addView(btnAdd)

        dialog.setContentView(root)
        dialog.show()
    }
}
