// EditorChecklistHelper: Checklist conversion, toggling, sorting and batch operations
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object EditorChecklistHelper {

    fun convertToOrToggleChecklist(editText: EditText, onSaveNeeded: () -> Unit) {
        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(0)
        val editable = editText.text ?: return
        val textStr = editable.toString()

        if (start != end) {
            val selStart = minOf(start, end)
            val selEnd = maxOf(start, end)
            val lineStart = textStr.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
            val lineEnd = textStr.indexOf('\n', selEnd).let { if (it == -1) textStr.length else it }
            val sub = textStr.substring(lineStart, lineEnd)
            val lines = sub.split("\n")
            val allCheck = lines.filter { it.isNotBlank() }.all { 
                val t = it.trimStart()
                t.startsWith("☐ ") || t.startsWith("☑ ") || t.startsWith("[ ] ") || t.startsWith("[x] ") 
            }
            val converted = lines.joinToString("\n") { line ->
                val trimmed = line.trimStart()
                if (allCheck) {
                    line.replaceFirst(Regex("^(?:\\s*)(?:☐ |☑ |\\[ \\] |\\[x\\] )"), "")
                } else if (!trimmed.startsWith("☐ ") && !trimmed.startsWith("☑ ") && !trimmed.startsWith("[ ] ") && !trimmed.startsWith("[x] ")) {
                    if (trimmed.startsWith("• ")) line.replaceFirst("• ", "☐ ") else "☐ $line"
                } else {
                    line
                }
            }
            editable.replace(lineStart, lineEnd, converted)
        } else {
            val lineStart = textStr.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
            val lineEnd = textStr.indexOf('\n', start).let { if (it == -1) textStr.length else it }
            val currentLine = if (lineEnd > lineStart) textStr.substring(lineStart, lineEnd) else ""
            val trimmed = currentLine.trimStart()

            if (trimmed.startsWith("☐ ")) {
                val relIdx = currentLine.indexOf("☐ ")
                editable.replace(lineStart + relIdx, lineStart + relIdx + 2, "☑ ")
            } else if (trimmed.startsWith("☑ ")) {
                val relIdx = currentLine.indexOf("☑ ")
                editable.replace(lineStart + relIdx, lineStart + relIdx + 2, "☐ ")
            } else if (trimmed.startsWith("[ ] ")) {
                val relIdx = currentLine.indexOf("[ ] ")
                editable.replace(lineStart + relIdx, lineStart + relIdx + 4, "[x] ")
            } else if (trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ")) {
                val relIdx = currentLine.indexOf(trimmed.substring(0, 4))
                editable.replace(lineStart + relIdx, lineStart + relIdx + 4, "[ ] ")
            } else if (trimmed.startsWith("• ")) {
                val relIdx = currentLine.indexOf("• ")
                editable.replace(lineStart + relIdx, lineStart + relIdx + 2, "☐ ")
            } else {
                editable.insert(lineStart, "☐ ")
            }
        }
        onSaveNeeded()
    }

    fun showChecklistOptionsMenu(context: Context, editText: EditText, onSaveNeeded: () -> Unit) {
        val options = arrayOf(
            "Convert Note to Checklist",
            "Sort Completed to Bottom",
            "Mark All Completed",
            "Uncheck All Items",
            "Remove All Checkboxes"
        )
        MaterialAlertDialogBuilder(context)
            .setTitle("Checklist Actions")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> convertAllLinesToChecklist(editText, onSaveNeeded)
                    1 -> autoSinkCompletedChecklistItems(context, editText, onSaveNeeded)
                    2 -> setAllChecklistItemsState(editText, true, onSaveNeeded)
                    3 -> setAllChecklistItemsState(editText, false, onSaveNeeded)
                    4 -> removeAllCheckboxes(editText, onSaveNeeded)
                }
            }
            .show()
    }

    fun convertAllLinesToChecklist(editText: EditText, onSaveNeeded: () -> Unit) {
        val editable = editText.text ?: return
        val lines = editable.toString().split("\n")
        val converted = lines.joinToString("\n") { line ->
            val trimmed = line.trimStart()
            if (trimmed.isNotBlank() && !trimmed.startsWith("- [ ] ") && !trimmed.startsWith("- [x] ") &&
                !trimmed.startsWith("☐ ") && !trimmed.startsWith("☑ ") && !trimmed.startsWith("[ ] ") && !trimmed.startsWith("[x] ")
            ) {
                if (trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    line.replaceFirst(Regex("^(?:\\s*)(?:• |- |\\* )"), "- [ ] ")
                } else {
                    "- [ ] $line"
                }
            } else {
                line
            }
        }
        editText.setText(converted)
        onSaveNeeded()
    }

    private fun autoSinkCompletedChecklistItems(context: Context, editText: EditText, onSaveNeeded: () -> Unit) {
        val editable = editText.text ?: return
        val lines = editable.toString().split("\n")
        val active = mutableListOf<String>()
        val completed = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") ||
                trimmed.startsWith("☑") || trimmed.startsWith("[x]") || trimmed.startsWith("[X]")
            ) {
                completed.add(line)
            } else {
                active.add(line)
            }
        }

        val reordered = (active + completed).joinToString("\n")
        if (reordered != editable.toString()) {
            editText.setText(reordered)
            onSaveNeeded()
            Toast.makeText(context, "Completed items sorted to bottom", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAllChecklistItemsState(editText: EditText, completed: Boolean, onSaveNeeded: () -> Unit) {
        val editable = editText.text ?: return
        val lines = editable.toString().split("\n")
        val updated = lines.joinToString("\n") { line ->
            val trimmed = line.trimStart()
            if (completed) {
                if (trimmed.startsWith("- [ ] ")) line.replaceFirst("- [ ] ", "- [x] ")
                else if (trimmed.startsWith("☐ ")) line.replaceFirst("☐ ", "☑ ")
                else if (trimmed.startsWith("[ ] ")) line.replaceFirst("[ ] ", "[x] ")
                else line
            } else {
                if (trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) line.replaceFirst(Regex("^-\\s*\\[[xX]\\] "), "- [ ] ")
                else if (trimmed.startsWith("☑ ")) line.replaceFirst("☑ ", "☐ ")
                else if (trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ")) line.replaceFirst(Regex("^\\[[xX]\\] "), "[ ] ")
                else line
            }
        }
        editText.setText(updated)
        onSaveNeeded()
    }

    fun removeAllCheckboxes(editText: EditText, onSaveNeeded: () -> Unit) {
        val editable = editText.text ?: return
        val lines = editable.toString().split("\n")
        val updated = lines.joinToString("\n") { line ->
            line.replaceFirst(Regex("^(?:\\s*)(?:- \\[ \\] |- \\[x\\] |- \\[X\\] |☐ |☑ |\\[ \\] |\\[x\\] )"), "")
        }
        editText.setText(updated)
        onSaveNeeded()
    }
}
