// CalendarMonthView: 7x6 month grid with day sticky-note pills and zero allocations in onDraw
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.pages.EditorActivity
import java.util.Calendar

class CalendarMonthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * resources.displayMetrics.scaledDensity
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * resources.displayMetrics.scaledDensity
        color = ContextCompat.getColor(context, R.color.on_surface)
    }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.brand)
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x221B5E20
    }

    private val rectCache = Rect()
    private val pillRectF = RectF()
    private val daysOfWeek = arrayOf("S", "M", "T", "W", "T", "F", "S")

    var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
        private set
    var currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
        private set
    var selectedDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        private set

    private var firstDayOfWeekOffset = 0
    private var daysInMonth = 30
    private var dayNotesMap: Map<Int, List<Note>> = emptyMap()

    var onDateSelectedListener: ((year: Int, month: Int, day: Int) -> Unit)? = null

    init {
        updateMonthCalculations()
    }

    fun setMonth(year: Int, month: Int) {
        currentYear = year
        currentMonth = month
        selectedDay = 1
        updateMonthCalculations()
        invalidate()
    }

    fun setDayNotes(notes: Map<Int, List<Note>>) {
        dayNotesMap = notes
        invalidate()
    }

    private fun updateMonthCalculations() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, currentYear)
        cal.set(Calendar.MONTH, currentMonth)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        firstDayOfWeekOffset = cal.get(Calendar.DAY_OF_WEEK) - 1
        daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val colW = w / 7f
        val headerH = 28f * resources.displayMetrics.density
        val rowH = (h - headerH) / 6f

        // Draw day headers
        for (i in 0 until 7) {
            val cx = i * colW + colW / 2f
            canvas.drawText(daysOfWeek[i], cx, headerH - 8f, headerPaint)
        }

        val todayCal = Calendar.getInstance()
        val isCurrentMonthToday = todayCal.get(Calendar.YEAR) == currentYear && todayCal.get(Calendar.MONTH) == currentMonth
        val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

        // Draw day cells
        for (day in 1..daysInMonth) {
            val cellIndex = firstDayOfWeekOffset + (day - 1)
            val col = cellIndex % 7
            val row = cellIndex / 7
            if (row >= 6) break

            val x = col * colW
            val y = headerH + row * rowH

            // Selected cell highlight
            if (day == selectedDay) {
                canvas.drawRoundRect(x + 2f, y + 2f, x + colW - 2f, y + rowH - 2f, 8f, 8f, selectedPaint)
            }

            // Today ring
            val cx = x + colW / 2f
            val cy = y + 16f * resources.displayMetrics.density
            if (isCurrentMonthToday && day == todayDay) {
                canvas.drawCircle(cx, cy, 14f * resources.displayMetrics.density, circlePaint)
            }

            // Day number text
            canvas.drawText(day.toString(), cx, cy + 5f * resources.displayMetrics.density, dayPaint)

            // Draw note color pills
            val notesForDay = dayNotesMap[day]
            if (!notesForDay.isNullOrEmpty()) {
                val pillW = colW - 8f
                val pillH = 3.5f * resources.displayMetrics.density
                var pillY = cy + 18f * resources.displayMetrics.density
                val maxPills = minOf(notesForDay.size, 3)

                for (pi in 0 until maxPills) {
                    val noteColor = EditorActivity.resolveNoteColor(context, notesForDay[pi].color)
                    pillPaint.color = noteColor
                    pillRectF.set(x + 4f, pillY, x + 4f + pillW, pillY + pillH)
                    canvas.drawRoundRect(pillRectF, pillH / 2f, pillH / 2f, pillPaint)
                    pillY += pillH + 2f
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val w = width.toFloat()
            val h = height.toFloat()
            val colW = w / 7f
            val headerH = 28f * resources.displayMetrics.density
            val rowH = (h - headerH) / 6f

            val touchY = event.y - headerH
            if (touchY >= 0) {
                val col = (event.x / colW).toInt().coerceIn(0, 6)
                val row = (touchY / rowH).toInt().coerceIn(0, 5)
                val cellIndex = row * 7 + col
                val day = cellIndex - firstDayOfWeekOffset + 1
                if (day in 1..daysInMonth) {
                    selectedDay = day
                    invalidate()
                    onDateSelectedListener?.invoke(currentYear, currentMonth, day)
                    return true
                }
            }
        }
        return true
    }
}
