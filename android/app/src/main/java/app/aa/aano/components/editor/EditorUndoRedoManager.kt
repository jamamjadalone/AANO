// EditorUndoRedoManager: Undo & Redo history stacks and state restoration for Editor
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.content.Context
import android.view.KeyEvent
import android.widget.EditText
import android.widget.Toast
import java.util.ArrayDeque
import kotlin.math.abs

data class TextSnapshot(
    val text: String,
    val cursor: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class EditorUndoRedoManager(private val maxCapacity: Int = 50) {

    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()
    var isUndoRedoActive = false
        private set

    fun pushState(text: String, cursor: Int = text.length) {
        if (isUndoRedoActive) return
        val now = System.currentTimeMillis()
        val top = undoStack.peek()
        if (top != null) {
            if (top.text == text) return
            val charDelta = abs(text.length - top.text.length)
            if (charDelta == 1 && (now - top.timestamp) < 800L) {
                undoStack.pop()
                undoStack.push(TextSnapshot(text, cursor, top.timestamp))
                redoStack.clear()
                return
            }
        }
        undoStack.push(TextSnapshot(text, cursor, now))
        if (undoStack.size > maxCapacity) undoStack.removeLast()
        redoStack.clear()
    }

    fun undo(context: Context, editText: EditText, onSaveNeeded: () -> Unit) {
        if (undoStack.size > 1) {
            isUndoRedoActive = true
            val current = undoStack.pop()
            redoStack.push(current)
            val prev = undoStack.peek() ?: TextSnapshot("", 0)
            applySnapshot(editText, prev)
            isUndoRedoActive = false
            onSaveNeeded()
        } else {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
        }
    }

    fun redo(context: Context, editText: EditText, onSaveNeeded: () -> Unit) {
        if (redoStack.isNotEmpty()) {
            isUndoRedoActive = true
            val next = redoStack.pop()
            undoStack.push(next)
            applySnapshot(editText, next)
            isUndoRedoActive = false
            onSaveNeeded()
        } else {
            Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySnapshot(editText: EditText, snapshot: TextSnapshot) {
        editText.setText(snapshot.text)
        val validCursor = snapshot.cursor.coerceIn(0, editText.text?.length ?: 0)
        editText.setSelection(validCursor)
    }

    fun handleKeyEvent(event: KeyEvent, context: Context, editText: EditText, onSaveNeeded: () -> Unit): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || !event.isCtrlPressed) return false
        return when {
            event.keyCode == KeyEvent.KEYCODE_Z && event.isShiftPressed -> {
                redo(context, editText, onSaveNeeded); true
            }
            event.keyCode == KeyEvent.KEYCODE_Z -> {
                undo(context, editText, onSaveNeeded); true
            }
            event.keyCode == KeyEvent.KEYCODE_Y -> {
                redo(context, editText, onSaveNeeded); true
            }
            else -> false
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        isUndoRedoActive = false
    }
}
