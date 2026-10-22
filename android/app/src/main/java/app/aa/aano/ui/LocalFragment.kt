// Dedicated Local (On-Device) screen for AANO multimedia notes
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.databinding.FragmentLocalBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LocalFragment : Fragment() {

    private var _binding: FragmentLocalBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private lateinit var prefs: Prefs
    private lateinit var adapter: NotesAdapter
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NoteRepository(requireContext())
        prefs = Prefs(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyLayoutManager()

        adapter = NotesAdapter(
            onClick = { note -> openEditor(note.id) },
            onLongClick = { note -> showLocalNoteActions(note) }
        )
        binding.localNotesList.adapter = adapter

        binding.fabAddLocal.setOnClickListener {
            openEditor(null)
        }

        loadLocalNotes()
    }

    private fun applyLayoutManager() {
        if (prefs.isGridView) {
            binding.localNotesList.layoutManager = StaggeredGridLayoutManager(
                2, StaggeredGridLayoutManager.VERTICAL
            ).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        } else {
            binding.localNotesList.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        applyLayoutManager()
        loadLocalNotes()
    }

    private fun loadLocalNotes() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val notes = repository.getLocalNotes()
            adapter.submitList(notes)
            binding.emptyView.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showLocalNoteActions(note: Note) {
        val items = arrayOf(
            if (note.pinned) "📌 Unpin Note" else "📌 Pin to Top",
            "☁️ Move to Cloud Notes",
            "🎨 Change Color",
            getString(R.string.share_note),
            getString(R.string.move_to_trash)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(note.title.ifBlank { "(No title)" })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> lifecycleScope.launch {
                        repository.togglePin(note.id)
                        loadLocalNotes()
                    }
                    1 -> lifecycleScope.launch {
                        repository.toggleLocal(note.id)
                        loadLocalNotes()
                        Toast.makeText(requireContext(), "Moved to Cloud Notes", Toast.LENGTH_SHORT).show()
                    }
                    2 -> showChangeColorDialog(note)
                    3 -> shareNote(note)
                    4 -> lifecycleScope.launch {
                        repository.delete(note.id)
                        loadLocalNotes()
                        Toast.makeText(requireContext(), "Moved to Trash", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showChangeColorDialog(note: Note) {
        val colors = EditorActivity.presetColors()
        val names = arrayOf("Default", "Coral", "Peach", "Sand", "Mint", "Sage", "Sky", "Lavender", "Blush", "Clay")
        AlertDialog.Builder(requireContext())
            .setTitle("Change Color")
            .setItems(names) { _, which ->
                val newColor = colors[which]
                lifecycleScope.launch {
                    repository.update(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        color = newColor,
                        folder = note.folder,
                        tags = note.tags
                    )
                    loadLocalNotes()
                }
            }
            .show()
    }

    private fun shareNote(note: Note) {
        val text = buildString {
            if (note.title.isNotBlank()) append(note.title).append("\n\n")
            append(note.content)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_note)))
    }

    private fun openEditor(noteId: String?) {
        startActivity(
            Intent(requireContext(), EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_NOTE_ID, noteId)
                .putExtra(EditorActivity.EXTRA_IS_LOCAL, true)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }
}
