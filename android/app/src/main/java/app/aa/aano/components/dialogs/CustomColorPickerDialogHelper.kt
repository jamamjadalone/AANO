// CustomColorPickerDialogHelper: 20-swatch palette grid and dynamic hex color builder dialog
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.R
import app.aa.aano.components.editor.EditorColorPickerHelper

object CustomColorPickerDialogHelper {

    private val SWATCHES = listOf(
        "#FFFFFF", "#FFF9C4", "#FFF59D", "#FFE082", "#FFCC80",
        "#FFAB91", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9",
        "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB",
        "#C8E6C9", "#DCEDC8", "#EFEBE9", "#CFD8DC", "#37474F"
    )

    fun show(activity: Activity, currentColorVal: Int, onColorApplied: (color: Int) -> Unit) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_color_picker, null)
        val swatchesGrid = dialogView.findViewById<GridLayout>(R.id.swatchesGrid)
        val livePreview = dialogView.findViewById<View>(R.id.liveColorPreview)
        val hexInput = dialogView.findViewById<EditText>(R.id.hexInput)

        var currentColor = EditorColorPickerHelper.resolveNoteColor(activity, currentColorVal)
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

        SWATCHES.forEach { hex ->
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
                        Log.d("CustomColorPicker", "Incomplete hex: ${e.message}")
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
}
