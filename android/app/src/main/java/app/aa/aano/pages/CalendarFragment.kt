// CalendarFragment: Interactive full month grid calendar with day sticky-note pills and monthly stats
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.aa.aano.R
import app.aa.aano.components.dialogs.CalendarDayDetailBottomSheet
import app.aa.aano.components.views.CalendarMonthView
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarFragment : Fragment() {

    private lateinit var repository: NoteRepository
    private lateinit var calendarView: CalendarMonthView
    private lateinit var tvMonthYear: TextView
    private lateinit var tvStats: TextView

    private val cal = Calendar.getInstance()
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private var allNotes: List<Note> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        repository = NoteRepository(requireContext())

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface))
        }

        // Header Toolbar
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 20, 24, 20)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.brand))
        }

        val btnPrev = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(android.graphics.Color.WHITE)
            setOnClickListener { navigateMonth(-1) }
        }
        header.addView(btnPrev)

        tvMonthYear = TextView(requireContext()).apply {
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(tvMonthYear)

        val btnNext = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(android.graphics.Color.WHITE)
            setOnClickListener { navigateMonth(1) }
        }
        header.addView(btnNext)

        val btnToday = TextView(requireContext()).apply {
            text = "Today"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(16, 8, 16, 8)
            setOnClickListener { jumpToToday() }
        }
        header.addView(btnToday)

        root.addView(header)

        // Month Grid View
        calendarView = CalendarMonthView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            onDateSelectedListener = { y, m, d ->
                val dayNotes = getNotesForDay(y, m, d)
                CalendarDayDetailBottomSheet.show(requireContext(), y, m, d, dayNotes) { loadNotes() }
            }
        }
        root.addView(calendarView)

        // Monthly Summary Footer
        tvStats = TextView(requireContext()).apply {
            setPadding(32, 20, 32, 28)
            textSize = 13f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            gravity = android.view.Gravity.CENTER
        }
        root.addView(tvStats)

        updateHeader()
        return root
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    private fun navigateMonth(delta: Int) {
        cal.add(Calendar.MONTH, delta)
        calendarView.setMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        updateHeader()
        mapNotesToMonth()
    }

    private fun jumpToToday() {
        cal.timeInMillis = System.currentTimeMillis()
        calendarView.setMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        updateHeader()
        mapNotesToMonth()
    }

    private fun updateHeader() {
        tvMonthYear.text = monthFormat.format(cal.time)
    }

    private fun loadNotes() {
        lifecycleScope.launch {
            allNotes = repository.getAll().filter { !it.deleted && !it.archived }
            mapNotesToMonth()
        }
    }

    private fun mapNotesToMonth() {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val dayMap = mutableMapOf<Int, MutableList<Note>>()
        var monthTotal = 0
        var reminderTotal = 0

        val checkCal = Calendar.getInstance()
        for (note in allNotes) {
            val dateToCheck = if (note.reminderDate > 0) note.reminderDate else if (note.reminderAt > 0) note.reminderAt else note.createdAt
            checkCal.timeInMillis = dateToCheck
            if (checkCal.get(Calendar.YEAR) == y && checkCal.get(Calendar.MONTH) == m) {
                val d = checkCal.get(Calendar.DAY_OF_MONTH)
                dayMap.getOrPut(d) { mutableListOf() }.add(note)
                monthTotal++
                if (note.reminderType != Note.REMINDER_NONE) reminderTotal++
            }
        }

        calendarView.setDayNotes(dayMap)
        tvStats.text = "$monthTotal note(s) • $reminderTotal reminder(s) this month"
    }

    private fun getNotesForDay(y: Int, m: Int, d: Int): List<Note> {
        val checkCal = Calendar.getInstance()
        return allNotes.filter { note ->
            val dateToCheck = if (note.reminderDate > 0) note.reminderDate else if (note.reminderAt > 0) note.reminderAt else note.createdAt
            checkCal.timeInMillis = dateToCheck
            checkCal.get(Calendar.YEAR) == y && checkCal.get(Calendar.MONTH) == m && checkCal.get(Calendar.DAY_OF_MONTH) == d
        }
    }
}
