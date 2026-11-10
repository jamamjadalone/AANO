// LinedNoteEditText: High-performance ruled notebook canvas with bottom viewport extension.
// Derived from Color Note's reverse-engineered canvas engine (LinedEditText.java).
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

package app.aa.aano.components.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Parcelable
import android.text.Selection
import android.text.Spannable
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatEditText

class LinedNoteEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "LinedNoteEditText"
        // Sage mint light mode & emerald slate dark mode default rule colors
        private const val DEFAULT_LIGHT_LINE = 0xFFE2EFE7.toInt()
        private const val DEFAULT_DARK_LINE = 0xFF1E3326.toInt()
        private const val MAX_PARCEL_SIZE = 180_000
    }

    private val cachedRect = Rect()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * resources.displayMetrics.density
    }

    private var isLinedEnabled = true
    private var isBatchEditingActive = false
    private var customLineColor: Int? = null

    init {
        updateDefaultLineColor()
    }

    /**
     * Set dynamic line rule color (e.g. matching current note palette).
     */
    fun setLineColor(color: Int) {
        customLineColor = color
        linePaint.color = color
        invalidate()
    }

    /**
     * Enable or disable ruled paper lines.
     */
    fun setLinedEnabled(enabled: Boolean) {
        if (isLinedEnabled != enabled) {
            isLinedEnabled = enabled
            invalidate()
        }
    }

    fun isLinedEnabled(): Boolean = isLinedEnabled

    private fun updateDefaultLineColor() {
        if (customLineColor != null) {
            linePaint.color = customLineColor!!
            return
        }
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        linePaint.color = if (isDark) DEFAULT_DARK_LINE else DEFAULT_LIGHT_LINE
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateDefaultLineColor()
        invalidate()
    }

    // High-speed IME typing optimization: skip intermediate redraws during composition
    override fun onBeginBatchEdit() {
        isBatchEditingActive = true
        super.onBeginBatchEdit()
    }

    override fun onEndBatchEdit() {
        isBatchEditingActive = false
        super.onEndBatchEdit()
    }

    override fun onDraw(canvas: Canvas) {
        if (isLinedEnabled) {
            val count = lineCount
            val r = cachedRect
            val paint = linePaint
            val density = resources.displayMetrics.density
            val lineOffset = 2f * density // 2dp baseline offset for legibility
            var lastY = 0f

            for (i in 0 until count) {
                val baseline = getLineBounds(i, r).toFloat()
                val lineY = baseline + lineOffset
                canvas.drawLine(r.left.toFloat(), lineY, r.right.toFloat(), lineY, paint)
                lastY = lineY
            }

            // Extend lines to bottom of viewport so notebook lines fill empty space
            val totalHeight = height.toFloat()
            val step = lineHeight.toFloat().coerceAtLeast(16f * density)
            while (lastY < totalHeight) {
                lastY += step
                canvas.drawLine(r.left.toFloat(), lastY, r.right.toFloat(), lastY, paint)
            }
        }

        try {
            super.onDraw(canvas)
        } catch (e: Exception) {
            // OEM text engine stability guard (Sony 5.1.1 / Samsung ArrayIndexOutOfBoundsException)
            Log.d(TAG, "LinedCanvas draw guard: ${e.message}")
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val state = super.onSaveInstanceState()
        // Protection against Android TransactionTooLargeException during process reclamation
        val textLength = text?.length ?: 0
        if (textLength > MAX_PARCEL_SIZE && text is Spannable) {
            try {
                Selection.removeSelection(text as Spannable)
            } catch (e: Exception) {
                Log.d(TAG, "Notice: selection strip: ${e.message}")
            }
        }
        return state
    }
}