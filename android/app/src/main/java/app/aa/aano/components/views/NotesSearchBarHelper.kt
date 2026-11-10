// NotesSearchBarHelper: Controls search card visibility and soft keyboard interactions
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.views

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import app.aa.aano.databinding.FragmentNotesBinding

object NotesSearchBarHelper {

    fun showSearchBar(binding: FragmentNotesBinding, context: Context) {
        binding.searchCard.visibility = View.VISIBLE
        binding.searchInput.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideSearchBar(binding: FragmentNotesBinding, context: Context, onCleared: () -> Unit) {
        binding.searchCard.visibility = View.GONE
        binding.searchInput.text?.clear()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        onCleared()
    }
}
