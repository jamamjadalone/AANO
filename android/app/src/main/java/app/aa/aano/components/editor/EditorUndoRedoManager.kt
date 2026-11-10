// EditorUndoRedoManager: Undo & Redo history stacks and state restoration for Editor
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import java.util.ArrayDeque

class EditorUndoRedoManager {

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    var isUndoRedoActive = false
        private set

    fun pushState(text: String) {
        if (isUndoRedoActive) return
        if (undoStack.isEmpty() || undoStack.peek() != text) {
            undoStack.push(text)
            if (undoStack.size > 40) undoStack.removeLast()
            redoStack.clear()
        }
    }

    fun undo(context: Context, editText: EditText, onSaveNeeded: () -> Unit) {
        if (undoStack.size > 1) {
            isUndoRedoActive = true
            val current = undoStack.pop()
            redoStack.push(current)
            val prev = undoStack.peek() ?: ""
            editText.setText(prev)
            editText.setSelection(prev.length.coerceAtMost(editText.text?.length ?: 0))
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
            editText.setText(next)
            editText.setSelection(next.length.coerceAtMost(editText.text?.length ?: 0))
            isUndoRedoActive = false
            onSaveNeeded()
        } else {
            Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show()
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        isUndoRedoActive = false
    }
}
