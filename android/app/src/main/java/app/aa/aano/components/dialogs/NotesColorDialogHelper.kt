// NotesColorDialogHelper: Note color picker, custom hex picker and rename dialogs
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.net.CloudSyncManager
import app.aa.aano.pages.EditorActivity

object NotesColorDialogHelper {

    fun showChangeColorDialog(
        context: Context,
        scope: CoroutineScope,
        note: Note,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onRefreshNeeded: () -> Unit,
        onSyncNeeded: () -> Unit
    ) {
        val colorNames = arrayOf(
            "Default Card", "Pastel Lemon", "Pastel Mint", "Pastel Teal",
            "Pastel Sky", "Pastel Indigo", "Pastel Lavender", "Pastel Rose",
            "Pastel Peach", "Pastel Coral", "Muted Sand", "Muted Slate",
            "Charcoal Dark", "🎨 Custom Color..."
        )
        AlertDialog.Builder(context)
            .setTitle("Note Color")
            .setItems(colorNames) { _, which ->
                if (which == colorNames.lastIndex) {
                    showCustomColorPickerForNote(context, scope, note, repository, prefs, cloudSyncManager, onRefreshNeeded, onSyncNeeded)
                } else {
                    scope.launch {
                        repository.update(
                            id = note.id,
                            title = note.title,
                            content = note.content,
                            color = which,
                            folder = note.folder,
                            tags = note.tags
                        )
                        onRefreshNeeded()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                            onSyncNeeded()
                        }
                    }
                }
            }
            .show()
    }

    fun showCustomColorPickerForNote(
        context: Context,
        scope: CoroutineScope,
        note: Note,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onRefreshNeeded: () -> Unit,
        onSyncNeeded: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val swatchesGrid = dialogView.findViewById<GridLayout>(R.id.swatchesGrid)
        val livePreview = dialogView.findViewById<View>(R.id.liveColorPreview)
        val hexInput = dialogView.findViewById<EditText>(R.id.hexInput)

        val swatches = listOf(
            "#FFFFFF", "#FFF9C4", "#FFF59D", "#FFE082", "#FFCC80",
            "#FFAB91", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9",
            "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB",
            "#C8E6C9", "#DCEDC8", "#EFEBE9", "#CFD8DC", "#37474F"
        )

        var currentColor = EditorActivity.resolveNoteColor(context, note.color)
        val density = context.resources.displayMetrics.density

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
            val dot = View(context).apply {
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
                        Log.d("NotesColorDialogHelper", "Incomplete or invalid hex color: ${e.message}")
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        MaterialAlertDialogBuilder(context)
            .setTitle("Custom Note Color")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val inputHex = hexInput.text.toString().trim()
                val finalColor = runCatching {
                    Color.parseColor(if (inputHex.startsWith("#")) inputHex else "#$inputHex")
                }.getOrDefault(currentColor)
                scope.launch {
                    repository.update(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        color = finalColor,
                        folder = note.folder,
                        tags = note.tags
                    )
                    onRefreshNeeded()
                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                        onSyncNeeded()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showRenameColorDialog(
        context: Context,
        prefs: Prefs,
        colorIndex: Int,
        currentLabel: String,
        defaultLabel: String,
        onUpdated: () -> Unit
    ) {
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
            }
            .setNeutralButton("Reset to Default") { _, _ ->
                prefs.setColorLabel(colorIndex, "")
                onUpdated()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
