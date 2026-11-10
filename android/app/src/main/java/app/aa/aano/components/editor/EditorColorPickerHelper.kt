// EditorColorPickerHelper: Color row building, rename dialog, and custom hex color picker
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.R
import app.aa.aano.components.views.NotesChipBarHelper
import app.aa.aano.data.Prefs

object EditorColorPickerHelper {

    val NOTE_COLORS = listOf(
        R.color.note_color_default,
        R.color.note_color_teal,
        R.color.note_color_yellow,
        R.color.note_color_green,
        R.color.note_color_blue,
        R.color.note_color_purple,
        R.color.note_color_pink,
        R.color.note_color_orange,
        R.color.note_color_red,
        R.color.note_color_sand,
        R.color.note_color_slate,
        R.color.note_color_charcoal
    )

    fun resolveNoteColor(context: Context, colorValue: Int): Int {
        return when {
            colorValue in 1 until NOTE_COLORS.size -> ContextCompat.getColor(context, NOTE_COLORS[colorValue])
            colorValue == 0 -> ContextCompat.getColor(context, R.color.card_surface)
            else -> colorValue
        }
    }

    fun buildColorRow(
        activity: Activity,
        colorRow: LinearLayout,
        prefs: Prefs,
        selectedColor: Int,
        onColorSelected: (color: Int) -> Unit
    ) {
        val density = activity.resources.displayMetrics.density
        colorRow.removeAllViews()
        NOTE_COLORS.forEachIndexed { index, colorRes ->
            val defaultLabel = NotesChipBarHelper.COLOR_LABELS.getOrNull(index) ?: "Color $index"
            val label = prefs.getColorLabel(index, defaultLabel)
            val dot = View(activity).apply {
                val size = (34 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins((5 * density).toInt(), 0, (5 * density).toInt(), 0)
                }
                val dotColor = if (index == 0) {
                    ContextCompat.getColor(activity, R.color.surface)
                } else {
                    ContextCompat.getColor(activity, colorRes)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(dotColor)
                    setStroke((1 * density).toInt(), 0x33000000.toInt())
                }
                isClickable = true
                tag = index
                contentDescription = "$label (Long-press to rename)"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tooltipText = "$label (Long-press to rename)"
                }
                setOnClickListener {
                    onColorSelected(index)
                    highlightSelectedColor(colorRow, activity, index)
                }
                setOnLongClickListener {
                    showRenameColorDialog(activity, prefs, index, defaultLabel) {
                        buildColorRow(activity, colorRow, prefs, selectedColor, onColorSelected)
                    }
                    true
                }
            }
            colorRow.addView(dot)
        }

        val customBtn = ImageView(activity).apply {
            val size = (34 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins((5 * density).toInt(), 0, (8 * density).toInt(), 0)
            }
            setImageResource(R.drawable.ic_palette)
            setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFF1F5F9.toInt())
                setStroke((1.5f * density).toInt(), ContextCompat.getColor(activity, R.color.brand))
            }
            isClickable = true
            tag = -999
            contentDescription = "Custom Color"
            setOnClickListener {
                showCustomColorPickerDialog(activity, selectedColor) { newColor ->
                    onColorSelected(newColor)
                    highlightSelectedColor(colorRow, activity, newColor)
                }
            }
        }
        colorRow.addView(customBtn)

        highlightSelectedColor(colorRow, activity, selectedColor)
    }

    fun highlightSelectedColor(colorRow: LinearLayout, context: Context, selectedColor: Int) {
        val density = context.resources.displayMetrics.density
        for (i in 0 until colorRow.childCount) {
            val child = colorRow.getChildAt(i)
            val tag = child.tag as? Int ?: continue
            val bg = child.background as? GradientDrawable ?: continue

            val isSelected = (tag == selectedColor) || (tag == -999 && selectedColor !in NOTE_COLORS.indices)
            if (isSelected) {
                bg.setStroke((3 * density).toInt(), ContextCompat.getColor(context, R.color.brand))
            } else {
                val strokeColor = if (tag == -999) ContextCompat.getColor(context, R.color.brand) else 0x33000000.toInt()
                val strokeWidth = if (tag == -999) (1.5f * density).toInt() else (1 * density).toInt()
                bg.setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun showRenameColorDialog(context: Context, prefs: Prefs, colorIndex: Int, defaultLabel: String, onUpdated: () -> Unit) {
        val currentLabel = prefs.getColorLabel(colorIndex, defaultLabel)
        val input = EditText(context).apply {
            setText(currentLabel)
            setSelection(text.length)
            hint = defaultLabel
            setSingleLine(true)
        }
        val density = context.resources.displayMetrics.density
        val container = FrameLayout(context).apply {
            val pad = (20 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Rename Color Label")
            .setMessage("Assign a custom category or name to this color:")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = input.text.toString().trim()
                prefs.setColorLabel(colorIndex, newLabel)
                onUpdated()
                Toast.makeText(context, "Label saved: ${newLabel.ifBlank { defaultLabel }}", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Reset to Default") { _, _ ->
                prefs.setColorLabel(colorIndex, "")
                onUpdated()
                Toast.makeText(context, "Label reset to $defaultLabel", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showCustomColorPickerDialog(activity: Activity, currentColorVal: Int, onColorApplied: (color: Int) -> Unit) {
        app.aa.aano.components.dialogs.CustomColorPickerDialogHelper.show(activity, currentColorVal, onColorApplied)
    }

    fun applyNoteColorTheme(
        context: Context,
        rootView: View,
        titleView: EditText,
        contentView: EditText,
        colorValue: Int
    ) {
        val bgColor = resolveNoteColor(context, colorValue)
        rootView.setBackgroundColor(bgColor)
        val isDark = androidx.core.graphics.ColorUtils.calculateLuminance(bgColor) < 0.35
        val textColor = if (isDark) 0xFFFFFFFF.toInt() else ContextCompat.getColor(context, R.color.on_surface)
        val hintColor = if (isDark) 0xAAFFFFFF.toInt() else ContextCompat.getColor(context, R.color.text_secondary)
        titleView.setTextColor(textColor)
        titleView.setHintTextColor(hintColor)
        contentView.setTextColor(textColor)
        contentView.setHintTextColor(hintColor)
        val linkColor = if (isDark) 0xFF80D8FF.toInt() else ContextCompat.getColor(context, R.color.brand)
        contentView.setLinkTextColor(linkColor)
        if (contentView is app.aa.aano.components.views.LinedNoteEditText) {
            val lineColor = if (isDark) 0x33FFFFFF.toInt() else 0x1A000000.toInt()
            contentView.setLineColor(lineColor)
        }
        if (context is Activity) {
            applySystemBarTheming(context, bgColor, isDark)
        }
    }

    private fun applySystemBarTheming(activity: Activity, bgColor: Int, isDark: Boolean) {
        val window = activity.window
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }
}

val NOTE_COLORS: List<Int> get() = EditorColorPickerHelper.NOTE_COLORS
fun presetColors(): List<Int> = EditorColorPickerHelper.NOTE_COLORS
fun resolveNoteColor(context: Context, colorValue: Int): Int =
    EditorColorPickerHelper.resolveNoteColor(context, colorValue)

