// EditorFormatBarHelper: Rich text spans, styling, checklists, and block moving
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.R

object EditorFormatBarHelper {

    private fun getSelectionRange(editText: EditText): Pair<Int, Int> {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        return if (start < end) Pair(start, end) else Pair(end, start)
    }

    fun toggleStyleSpan(editText: EditText, context: Context, style: Int, onSaveNeeded: () -> Unit) {
        val (start, end) = getSelectionRange(editText)
        val editable = editText.text ?: return
        if (start == end) {
            Toast.makeText(context, "Select text to format", Toast.LENGTH_SHORT).show()
            return
        }
        val spans = editable.getSpans(start, end, StyleSpan::class.java)
        val existing = spans.firstOrNull { it.style == style }
        if (existing != null) {
            editable.removeSpan(existing)
        } else {
            editable.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        onSaveNeeded()
    }

    fun toggleUnderlineSpan(editText: EditText, context: Context, onSaveNeeded: () -> Unit) {
        val (start, end) = getSelectionRange(editText)
        val editable = editText.text ?: return
        if (start == end) {
            Toast.makeText(context, "Select text to underline", Toast.LENGTH_SHORT).show()
            return
        }
        val spans = editable.getSpans(start, end, UnderlineSpan::class.java)
        if (spans.isNotEmpty()) {
            spans.forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        onSaveNeeded()
    }

    fun toggleStrikethroughSpan(editText: EditText, context: Context, onSaveNeeded: () -> Unit) {
        val (start, end) = getSelectionRange(editText)
        val editable = editText.text ?: return
        if (start == end) {
            Toast.makeText(context, "Select text to strikethrough", Toast.LENGTH_SHORT).show()
            return
        }
        val spans = editable.getSpans(start, end, StrikethroughSpan::class.java)
        if (spans.isNotEmpty()) {
            spans.forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        onSaveNeeded()
    }

    fun showHighlightColorDialog(editText: EditText, context: Context, onSaveNeeded: () -> Unit) {
        val (start, end) = getSelectionRange(editText)
        val editable = editText.text ?: return
        if (start == end) {
            Toast.makeText(context, "Select text to highlight", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf("💛 Yellow", "💚 Mint Green", "💖 Soft Pink", "💙 Sky Blue", "🧡 Peach Orange", "❌ Remove Highlight")
        val colors = intArrayOf(
            ContextCompat.getColor(context, R.color.highlight_yellow),
            ContextCompat.getColor(context, R.color.highlight_green),
            ContextCompat.getColor(context, R.color.highlight_pink),
            ContextCompat.getColor(context, R.color.highlight_blue),
            ContextCompat.getColor(context, R.color.highlight_orange)
        )
        MaterialAlertDialogBuilder(context)
            .setTitle("Highlight Color")
            .setItems(options) { _, which ->
                val spans = editable.getSpans(start, end, BackgroundColorSpan::class.java)
                spans.forEach { editable.removeSpan(it) }
                if (which in colors.indices) {
                    editable.setSpan(BackgroundColorSpan(colors[which]), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                onSaveNeeded()
            }
            .show()
    }

    fun showTextColorDialog(editText: EditText, context: Context, onSaveNeeded: () -> Unit) {
        val (start, end) = getSelectionRange(editText)
        val editable = editText.text ?: return
        if (start == end) {
            Toast.makeText(context, "Select text to change color", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf("🖤 Default Text", "🟢 Emerald Green", "🔵 Royal Blue", "🔴 Crimson Red", "🟣 Deep Purple")
        val colors = intArrayOf(
            ContextCompat.getColor(context, R.color.on_surface),
            ContextCompat.getColor(context, R.color.text_color_emerald),
            ContextCompat.getColor(context, R.color.text_color_blue),
            ContextCompat.getColor(context, R.color.text_color_red),
            ContextCompat.getColor(context, R.color.text_color_purple)
        )
        MaterialAlertDialogBuilder(context)
            .setTitle("Text Color")
            .setItems(options) { _, which ->
                val spans = editable.getSpans(start, end, ForegroundColorSpan::class.java)
                spans.forEach { editable.removeSpan(it) }
                if (which in 1 until colors.size) {
                    editable.setSpan(ForegroundColorSpan(colors[which]), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                onSaveNeeded()
            }
            .show()
    }

    fun toggleHeading(editText: EditText, scale: Float, onSaveNeeded: () -> Unit) {
        val (start, end) = getSelectionRange(editText)
        val editable = editText.text ?: return
        if (start == end) {
            val textStr = editable.toString()
            val lineStart = textStr.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
            val lineEnd = textStr.indexOf('\n', start).let { if (it == -1) textStr.length else it }
            if (lineEnd > lineStart) {
                val spans = editable.getSpans(lineStart, lineEnd, RelativeSizeSpan::class.java)
                if (spans.isNotEmpty()) {
                    spans.forEach { editable.removeSpan(it) }
                } else {
                    editable.setSpan(RelativeSizeSpan(scale), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    editable.setSpan(StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } else {
            val spans = editable.getSpans(start, end, RelativeSizeSpan::class.java)
            if (spans.isNotEmpty()) {
                spans.forEach { editable.removeSpan(it) }
            } else {
                editable.setSpan(RelativeSizeSpan(scale), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        onSaveNeeded()
    }

    fun insertBulletPoint(editText: EditText, onSaveNeeded: () -> Unit) {
        val start = editText.selectionStart
        val editable = editText.text ?: return
        val textStr = editable.toString()
        val lineStart = textStr.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        editable.insert(lineStart, "• ")
        onSaveNeeded()
    }



    fun moveSelectedBlock(editText: EditText, direction: Int, onSaveNeeded: () -> Unit) {
        val editable = editText.text ?: return
        val text = editable.toString()
        val lines = text.lines().toMutableList()
        if (lines.size <= 1) return

        val selStart = editText.selectionStart.coerceAtLeast(0)
        var charCount = 0
        var activeLineIdx = 0
        for (i in lines.indices) {
            charCount += lines[i].length + 1
            if (selStart <= charCount) {
                activeLineIdx = i
                break
            }
        }

        val targetLineIdx = activeLineIdx + direction
        if (targetLineIdx in lines.indices) {
            val temp = lines[activeLineIdx]
            lines[activeLineIdx] = lines[targetLineIdx]
            lines[targetLineIdx] = temp
            val newContent = lines.joinToString("\n")
            editText.setText(newContent)
            var newCursor = 0
            for (i in 0 until targetLineIdx) {
                newCursor += lines[i].length + 1
            }
            editText.setSelection(newCursor.coerceAtMost(newContent.length))
            onSaveNeeded()
        }
    }

    fun setupToolbar(
        activity: android.app.Activity,
        binding: app.aa.aano.databinding.ActivityEditorBinding,
        undoRedoManager: EditorUndoRedoManager,
        onSaveNeeded: () -> Unit
    ) {
        binding.btnUndo.setOnClickListener { undoRedoManager.undo(activity, binding.noteContent, onSaveNeeded) }
        binding.btnRedo.setOnClickListener { undoRedoManager.redo(activity, binding.noteContent, onSaveNeeded) }
        binding.btnH1.setOnClickListener { toggleHeading(binding.noteContent, 1.4f, onSaveNeeded) }
        binding.btnH2.setOnClickListener { toggleHeading(binding.noteContent, 1.2f, onSaveNeeded) }
        binding.btnBold.setOnClickListener { toggleStyleSpan(binding.noteContent, activity, Typeface.BOLD, onSaveNeeded) }
        binding.btnItalic.setOnClickListener { toggleStyleSpan(binding.noteContent, activity, Typeface.ITALIC, onSaveNeeded) }
        binding.btnUnderline.setOnClickListener { toggleUnderlineSpan(binding.noteContent, activity, onSaveNeeded) }
        binding.btnStrikethrough.setOnClickListener { toggleStrikethroughSpan(binding.noteContent, activity, onSaveNeeded) }
        binding.btnHighlight.setOnClickListener { showHighlightColorDialog(binding.noteContent, activity, onSaveNeeded) }
        binding.btnTextColor.setOnClickListener { showTextColorDialog(binding.noteContent, activity, onSaveNeeded) }
        binding.btnBullet.setOnClickListener { insertBulletPoint(binding.noteContent, onSaveNeeded) }
        binding.btnChecklist.setOnClickListener { EditorChecklistHelper.convertToOrToggleChecklist(binding.noteContent, onSaveNeeded) }
        binding.btnChecklist.setOnLongClickListener {
            EditorChecklistHelper.showChecklistOptionsMenu(activity, binding.noteContent, onSaveNeeded)
            true
        }
        attachAutoBulletWatcher(binding.noteContent, onSaveNeeded)
    }

    private fun attachAutoBulletWatcher(editText: EditText, onSaveNeeded: () -> Unit) {
        editText.addTextChangedListener(object : android.text.TextWatcher {
            private var isModifying = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isModifying || s == null) return
                if (before == 0 && count == 1 && start < s.length && s[start] == '\n') {
                    val textBefore = s.subSequence(0, start).toString()
                    val lineStart = textBefore.lastIndexOf('\n') + 1
                    val prevLine = textBefore.substring(lineStart)
                    val bullet = listOf("- [ ] ", "- [x] ", "- ", "* ", "• ").firstOrNull { prevLine.startsWith(it) }
                    val numMatch = Regex("^(\\d+)\\.\\s+").find(prevLine)

                    if (bullet != null) {
                        if (prevLine.trim() == bullet.trim()) {
                            isModifying = true; editText.text?.delete(lineStart, start + 1); isModifying = false
                            onSaveNeeded(); return
                        }
                        isModifying = true
                        val insert = if (bullet.startsWith("- [")) "- [ ] " else bullet
                        editText.text?.insert(start + 1, insert)
                        isModifying = false; onSaveNeeded()
                    } else if (numMatch != null) {
                        val num = numMatch.groupValues[1].toIntOrNull() ?: 1
                        if (prevLine.trim() == "$num.") {
                            isModifying = true; editText.text?.delete(lineStart, start + 1); isModifying = false
                            onSaveNeeded(); return
                        }
                        isModifying = true; editText.text?.insert(start + 1, "${num + 1}. ")
                        isModifying = false; onSaveNeeded()
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
}
