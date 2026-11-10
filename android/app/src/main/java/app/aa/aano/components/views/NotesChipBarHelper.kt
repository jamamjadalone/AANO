// NotesChipBarHelper: Dynamically builds folder & color palette chips for Notes tab
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.pages.EditorActivity

object NotesChipBarHelper {

    val COLOR_LABELS = listOf(
        "Default", "Teal", "Yellow", "Green", "Blue", "Purple",
        "Pink", "Orange", "Red", "Sand", "Slate", "Charcoal"
    )

    fun updateFilterHeader(
        headerBar: View,
        statusText: android.widget.TextView,
        currentFolder: String?,
        currentColorFilter: Int?,
        prefs: Prefs
    ) {
        val parts = mutableListOf<String>()
        if (currentFolder == "__LOCAL__") {
            parts.add("⚡ On-Device Notes (Local Vault)")
        } else if (currentFolder != null) {
            parts.add("📁 Folder: $currentFolder")
        }
        if (currentColorFilter != null) {
            val defaultLabel = COLOR_LABELS.getOrNull(currentColorFilter) ?: "Custom"
            val label = if (currentColorFilter in COLOR_LABELS.indices) prefs.getColorLabel(currentColorFilter, defaultLabel) else defaultLabel
            parts.add("🎨 Color: $label")
        }
        headerBar.visibility = if (parts.isNotEmpty()) View.VISIBLE else View.GONE
        if (parts.isNotEmpty()) statusText.text = "Showing: " + parts.joinToString(" • ")
    }

    fun buildFolderChips(
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        chipGroup: ChipGroup,
        currentFolder: String?,
        onFolderSelected: (folder: String?) -> Unit
    ) {
        scope.launch {
            val counts = repository.getFolderCounts()
            val totalNotes = counts.values.sum()
            val localCount = repository.getLocalNotes().size
            chipGroup.removeAllViews()

            val all = Chip(context).apply {
                text = "All Notes ($totalNotes)"
                isCheckable = true
                isChecked = currentFolder == null
                id = View.generateViewId()
                setOnClickListener {
                    chipGroup.check(id)
                    onFolderSelected(null)
                }
            }
            chipGroup.addView(all)

            if (localCount > 0) {
                val localChip = Chip(context).apply {
                    text = "⚡ On-Device ($localCount)"
                    isCheckable = true
                    isChecked = currentFolder == "__LOCAL__"
                    id = View.generateViewId()
                    setOnClickListener {
                        chipGroup.check(id)
                        onFolderSelected("__LOCAL__")
                    }
                }
                chipGroup.addView(localChip)
            }

            counts.keys.sorted().forEach { folder ->
                val count = counts[folder] ?: 0
                val chip = Chip(context).apply {
                    text = "$folder ($count)"
                    isCheckable = true
                    isChecked = folder.equals(currentFolder, ignoreCase = true)
                    id = View.generateViewId()
                    setOnClickListener {
                        chipGroup.check(id)
                        onFolderSelected(folder)
                    }
                }
                chipGroup.addView(chip)
            }
        }
    }

    fun buildPaletteChips(
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        prefs: Prefs,
        chipGroup: ChipGroup,
        currentColorFilter: Int?,
        colorLabels: List<String>,
        onColorSelected: (color: Int?) -> Unit,
        onRenameRequested: (colorIndex: Int, currentLabel: String, defaultLabel: String) -> Unit
    ) {
        scope.launch {
            val colorCounts = repository.getColorCounts()
            val totalNotes = colorCounts.values.sum()
            chipGroup.removeAllViews()

            val allChip = Chip(context).apply {
                text = "🎨 All ($totalNotes)"
                isCheckable = true
                isChecked = currentColorFilter == null
                id = View.generateViewId()
                setOnClickListener {
                    chipGroup.check(id)
                    onColorSelected(null)
                }
            }
            chipGroup.addView(allChip)

            val density = context.resources.displayMetrics.density
            EditorActivity.NOTE_COLORS.forEachIndexed { index, colorRes ->
                val count = colorCounts[index] ?: 0
                if (count > 0 || currentColorFilter == index) {
                    val resolvedColor = ContextCompat.getColor(context, colorRes)
                    val defaultLabel = colorLabels.getOrNull(index) ?: "Color $index"
                    val label = prefs.getColorLabel(index, defaultLabel)
                    val chip = Chip(context).apply {
                        text = "$label ($count)"
                        tooltipText = "$label ($count) • Long-press to rename"
                        isCheckable = true
                        isChecked = currentColorFilter == index
                        id = View.generateViewId()

                        chipIcon = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(resolvedColor)
                            setStroke((1 * density).toInt(), 0x33000000.toInt())
                            setSize((14 * density).toInt(), (14 * density).toInt())
                        }
                        isChipIconVisible = true

                        setOnClickListener {
                            if (currentColorFilter == index) {
                                onColorSelected(null)
                                chipGroup.check(allChip.id)
                            } else {
                                onColorSelected(index)
                                chipGroup.check(id)
                            }
                        }
                        setOnLongClickListener {
                            onRenameRequested(index, label, defaultLabel)
                            true
                        }
                    }
                    chipGroup.addView(chip)
                }
            }

            colorCounts.forEach { (colorVal, count) ->
                if (colorVal !in 0 until EditorActivity.NOTE_COLORS.size && count > 0) {
                    val chip = Chip(context).apply {
                        text = "Custom ($count)"
                        isCheckable = true
                        isChecked = currentColorFilter == colorVal
                        id = View.generateViewId()

                        chipIcon = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(colorVal)
                            setStroke((1 * density).toInt(), 0x33000000.toInt())
                            setSize((14 * density).toInt(), (14 * density).toInt())
                        }
                        isChipIconVisible = true

                        setOnClickListener {
                            if (currentColorFilter == colorVal) {
                                onColorSelected(null)
                                chipGroup.check(allChip.id)
                            } else {
                                onColorSelected(colorVal)
                                chipGroup.check(id)
                            }
                        }
                    }
                    chipGroup.addView(chip)
                }
            }
        }
    }
}
