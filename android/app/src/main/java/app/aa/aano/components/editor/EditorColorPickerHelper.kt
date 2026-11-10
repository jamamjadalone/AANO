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
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_color_picker, null)
        val swatchesGrid = dialogView.findViewById<GridLayout>(R.id.swatchesGrid)
        val livePreview = dialogView.findViewById<View>(R.id.liveColorPreview)
        val hexInput = dialogView.findViewById<EditText>(R.id.hexInput)

        val swatches = listOf(
            "#FFFFFF", "#FFF9C4", "#FFF59D", "#FFE082", "#FFCC80",
            "#FFAB91", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9",
            "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB",
            "#C8E6C9", "#DCEDC8", "#EFEBE9", "#CFD8DC", "#37474F"
        )

        var currentColor = resolveNoteColor(activity, currentColorVal)
        val density = activity.resources.displayMetrics.density

        fun updatePreview(color: Int) {
            currentColor = color
            livePreview.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke((1 * density).toInt(), 0x33000000.toInt())
            }
        }

        updatePreview(currentColor)
        val hexStr = String.format("#%06X", 0xFFFFFF and currentColor)
        hexInput.setText(hexStr)

        swatches.forEach { hex ->
            val swatchColor = Color.parseColor(hex)
            val dot = View(activity).apply {
                val size = (34 * density).toInt()
                val params = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                }
                layoutParams = params
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(swatchColor)
                    setStroke((1 * density).toInt(), 0x33000000.toInt())
                }
                isClickable = true
                setOnClickListener {
                    updatePreview(swatchColor)
                    hexInput.setText(hex)
                }
            }
            swatchesGrid.addView(dot)
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                val text = s?.toString()?.trim() ?: ""
                if (text.startsWith("#") && (text.length == 7 || text.length == 9)) {
                    try {
                        val parsed = Color.parseColor(text)
                        updatePreview(parsed)
                    } catch (e: IllegalArgumentException) {
                        Log.d("EditorColorPickerHelper", "Incomplete hex: ${e.message}")
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        MaterialAlertDialogBuilder(activity)
            .setTitle("Custom Note Color")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val inputHex = hexInput.text.toString().trim()
                val finalColor = runCatching {
                    Color.parseColor(if (inputHex.startsWith("#")) inputHex else "#$inputHex")
                }.getOrDefault(currentColor)
                onColorApplied(finalColor)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
    }
}

val NOTE_COLORS: List<Int> get() = EditorColorPickerHelper.NOTE_COLORS
fun presetColors(): List<Int> = EditorColorPickerHelper.NOTE_COLORS
fun resolveNoteColor(context: Context, colorValue: Int): Int =
    EditorColorPickerHelper.resolveNoteColor(context, colorValue)

