// EditorTouchHelper: Seamless touch, link navigation, cursor placement and view/edit mode UI transitions
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.app.Activity
import android.content.Context
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import app.aa.aano.databinding.ActivityEditorBinding

object EditorTouchHelper {

    class SmartTouchMovementMethod(
        private val isEditModeSupplier: () -> Boolean,
        private val onNonLinkTapped: (offset: Int) -> Unit
    ) : LinkMovementMethod() {
        override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
            if (isEditModeSupplier()) return false
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                val layout = widget.layout
                if (layout != null) {
                    val line = layout.getLineForVertical(y)
                    val off = layout.getOffsetForHorizontal(line, x.toFloat())
                    val links = buffer.getSpans(off, off, URLSpan::class.java)
                    if (links.isNotEmpty()) {
                        return super.onTouchEvent(widget, buffer, event)
                    } else {
                        onNonLinkTapped(off)
                        return true
                    }
                }
            }
            return super.onTouchEvent(widget, buffer, event)
        }
    }

    fun setupSeamlessTouch(
        binding: ActivityEditorBinding,
        isEditModeSupplier: () -> Boolean,
        onSwitchToEditMode: (focusTitle: Boolean, tapOffset: Int?) -> Unit
    ) {
        binding.noteContent.movementMethod = SmartTouchMovementMethod(isEditModeSupplier) { off ->
            onSwitchToEditMode(false, off)
        }
        binding.noteTitle.setOnClickListener {
            if (!isEditModeSupplier()) onSwitchToEditMode(true, null)
        }
        binding.root.setOnClickListener {
            if (!isEditModeSupplier()) onSwitchToEditMode(false, null)
        }
    }

    fun switchToEditMode(
        activity: Activity,
        binding: ActivityEditorBinding,
        focusTitle: Boolean,
        tapOffset: Int?,
        onModeChanged: () -> Unit
    ) {
        onModeChanged()
        applyModeUi(activity, binding, true) { ft, off ->
            switchToEditMode(activity, binding, ft, off, onModeChanged)
        }
        val target = if (focusTitle) binding.noteTitle else binding.noteContent
        target.post {
            target.requestFocus()
            if (!focusTitle && tapOffset != null && tapOffset in 0..(binding.noteContent.text?.length ?: 0)) {
                binding.noteContent.setSelection(tapOffset)
            }
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun applyModeUi(
        activity: Activity,
        binding: ActivityEditorBinding,
        isEditMode: Boolean,
        onSwitchToEditMode: (focusTitle: Boolean, tapOffset: Int?) -> Unit
    ) {
        try {
            Linkify.addLinks(binding.noteContent, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
        } catch (e: Exception) {
            Log.d("EditorTouchHelper", "Linkify error: ${e.message}")
        }

        binding.noteTitle.isFocusable = isEditMode
        binding.noteTitle.isFocusableInTouchMode = isEditMode
        binding.noteTitle.isCursorVisible = isEditMode

        binding.noteContent.isFocusable = isEditMode
        binding.noteContent.isFocusableInTouchMode = isEditMode
        binding.noteContent.isCursorVisible = isEditMode
        binding.formattingToolbar.visibility = if (isEditMode) View.VISIBLE else View.GONE

        if (isEditMode) {
            binding.noteContent.movementMethod = ArrowKeyMovementMethod.getInstance()
        } else {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
            binding.noteContent.movementMethod = SmartTouchMovementMethod({ false }) { off ->
                onSwitchToEditMode(false, off)
            }
        }
    }
}
