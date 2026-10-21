// Dedicated Search screen for AANO notes
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import app.aa.aano.data.NoteRepository
import app.aa.aano.databinding.FragmentSearchBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private lateinit var adapter: NotesAdapter
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NoteRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = app.aa.aano.data.Prefs(requireContext())
        if (prefs.isGridView) {
            binding.searchResultsList.layoutManager = StaggeredGridLayoutManager(
                2, StaggeredGridLayoutManager.VERTICAL
            ).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        } else {
            binding.searchResultsList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        }

        adapter = NotesAdapter(
            onClick = { note ->
                startActivity(
                    Intent(requireContext(), EditorActivity::class.java)
                        .putExtra(EditorActivity.EXTRA_NOTE_ID, note.id)
                )
            },
            onLongClick = { /* no-op in search */ }
        )
        binding.searchResultsList.adapter = adapter

        binding.btnClearSearch.setOnClickListener {
            binding.searchInput.setText("")
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            adapter.submitList(emptyList())
            binding.searchEmptyView.text = "Type to search your notes"
            binding.searchEmptyView.visibility = View.VISIBLE
            return
        }

        searchJob = lifecycleScope.launch {
            val results = repository.search(query)
            adapter.submitList(results)
            if (results.isEmpty()) {
                binding.searchEmptyView.text = "No notes found matching \"$query\""
                binding.searchEmptyView.visibility = View.VISIBLE
            } else {
                binding.searchEmptyView.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
