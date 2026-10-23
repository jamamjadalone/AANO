// Dedicated Local (On-Device) screen for AANO multimedia notes
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import app.aa.aano.R
import app.aa.aano.data.LocalVaultManager
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

    private val restoreVaultLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreLocalVault(uri)
        }
    }

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

        binding.btnExportVault.setOnClickListener {
            exportLocalVault()
        }

        binding.btnRestoreVault.setOnClickListener {
            restoreVaultLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
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
            "🗑️ Delete Note (Permanently)"
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
                    4 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Delete Local Note")
                            .setMessage("Permanently delete this local note and attached photos/voice memos from this device? This action cannot be undone.")
                            .setPositiveButton("Delete Permanently") { _, _ ->
                                lifecycleScope.launch {
                                    repository.deleteLocalPermanently(note.id)
                                    loadLocalNotes()
                                    Toast.makeText(requireContext(), "Local note permanently deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
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

    private fun exportLocalVault() {
        lifecycleScope.launch {
            try {
                val summary = LocalVaultManager.exportVault(requireContext(), repository)
                AlertDialog.Builder(requireContext())
                    .setTitle("Media Vault Exported")
                    .setMessage(
                        "Local vault archived successfully!\n\n" +
                        "• Notes: ${summary.notesCount}\n" +
                        "• Photos: ${summary.imagesCount}\n" +
                        "• Audio Memos: ${summary.audioCount}\n" +
                        "• Size: ${summary.fileSizeFormatted}\n\n" +
                        "Saved to: Downloads/AANO/${summary.fileName}"
                    )
                    .setPositiveButton("Share Archive") { _, _ ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, summary.zipUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Vault Archive"))
                    }
                    .setNegativeButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun restoreLocalVault(uri: Uri) {
        lifecycleScope.launch {
            try {
                val summary = LocalVaultManager.restoreVault(requireContext(), uri, repository)
                loadLocalNotes()
                AlertDialog.Builder(requireContext())
                    .setTitle("Media Vault Restored")
                    .setMessage(
                        "Vault restore complete!\n\n" +
                        "• Local Notes: ${summary.notesRestored}\n" +
                        "• Photos Extracted: ${summary.imagesRestored}\n" +
                        "• Audio Memos: ${summary.audioRestored}\n\n" +
                        "Your local multimedia notes are now ready."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
