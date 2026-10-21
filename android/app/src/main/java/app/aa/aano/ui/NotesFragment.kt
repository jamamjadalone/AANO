// Notes tab: list, search, sort, pin, trash and Google sync.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.SortBy
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.FragmentNotesBinding
import app.aa.aano.net.CloudSyncManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var adapter: NotesAdapter
    private lateinit var prefs: Prefs

    private var loadJob: Job? = null
    private var query: String = ""
    private var currentFolder: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        repository = NoteRepository(requireContext())
        cloudSyncManager = CloudSyncManager(
            requireContext(),
            repository,
            TaskRepository(requireContext())
        )
        prefs = Prefs(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)

        val pendingFolder = (activity as? HomeActivity)?.consumePendingFolder()
        if (pendingFolder != null) {
            currentFolder = pendingFolder
        }

        applyLayoutManager()

        adapter = NotesAdapter(
            onClick = { openEditor(it.id) },
            onLongClick = { showNoteActions(it) }
        )
        binding.notesList.adapter = adapter

        binding.fabAdd.setOnClickListener { openEditor(null, currentFolder) }
        binding.clearFilterBtn.setOnClickListener { filterByFolder(null) }
        binding.emptyView.visibility = View.GONE

        updateFilterHeader()
        buildFolderChips()
        loadNotes()
    }

    private fun updateFilterHeader() {
        if (currentFolder != null) {
            binding.filterHeaderBar.visibility = View.VISIBLE
            binding.filterStatusText.text = "📁 Showing notes in: $currentFolder"
        } else {
            binding.filterHeaderBar.visibility = View.GONE
        }
    }

    private fun buildFolderChips() {
        lifecycleScope.launch {
            val counts = repository.getFolderCounts()
            val totalNotes = counts.values.sum()
            binding.folderChips.removeAllViews()

            val all = com.google.android.material.chip.Chip(requireContext()).apply {
                text = "All Notes ($totalNotes)"
                isCheckable = true
                isChecked = currentFolder == null
                id = View.generateViewId()
                setOnClickListener {
                    binding.folderChips.check(id)
                    filterByFolder(null)
                }
            }
            binding.folderChips.addView(all)

            counts.keys.sorted().forEach { folder ->
                val count = counts[folder] ?: 0
                val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                    text = "$folder ($count)"
                    isCheckable = true
                    isChecked = folder.equals(currentFolder, ignoreCase = true)
                    id = View.generateViewId()
                    setOnClickListener {
                        binding.folderChips.check(id)
                        filterByFolder(folder)
                    }
                }
                binding.folderChips.addView(chip)
            }
        }
    }

    private fun applyLayoutManager() {
        if (prefs.isGridView) {
            binding.notesList.layoutManager = androidx.recyclerview.widget.StaggeredGridLayoutManager(
                2,
                androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL
            ).apply {
                gapStrategy = androidx.recyclerview.widget.StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        } else {
            binding.notesList.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        val viewModeItem = menu.findItem(R.id.action_view_mode)
        if (prefs.isGridView) {
            viewModeItem?.setIcon(R.drawable.ic_view_list)
            viewModeItem?.title = "List View"
        } else {
            viewModeItem?.setIcon(R.drawable.ic_view_grid)
            viewModeItem?.title = "Grid View"
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_mode -> {
                prefs.isGridView = !prefs.isGridView
                applyLayoutManager()
                activity?.invalidateOptionsMenu()
                true
            }
            R.id.action_sort -> { showSortDialog(); true }
            R.id.action_sync -> { startSyncFlow(); true }
            R.id.action_settings -> {
                startActivity(Intent(requireContext(), SettingsActivity::class.java)); true
            }
            R.id.action_trash -> {
                startActivity(Intent(requireContext(), TrashActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        val pendingFolder = (activity as? HomeActivity)?.consumePendingFolder()
        if (pendingFolder != null) {
            currentFolder = pendingFolder
        }
        updateFilterHeader()
        loadNotes()
        buildFolderChips()
        lifecycleScope.launch { repository.purgeExpiredTrash(TRASH_RETENTION_MS) }
        if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
            launchSync(silent = true)
        }
    }

    private fun showSortDialog() {
        val current = prefs.sortBy
        val options = arrayOf(
            getString(R.string.sort_updated),
            getString(R.string.sort_created),
            getString(R.string.sort_title),
            getString(R.string.sort_pinned)
        )
        val selected = when (current) {
            SortBy.UPDATED_DESC -> 0
            SortBy.CREATED_DESC -> 1
            SortBy.TITLE_ASC -> 2
            SortBy.PINNED_FIRST -> 3
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, selected) { dialog, which ->
                prefs.sortBy = when (which) {
                    0 -> SortBy.UPDATED_DESC
                    1 -> SortBy.CREATED_DESC
                    2 -> SortBy.TITLE_ASC
                    else -> SortBy.PINNED_FIRST
                }
                dialog.dismiss()
                loadNotes()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNoteActions(note: Note) {
        val items = arrayOf(
            if (note.pinned) "📌 Unpin Note" else "📌 Pin to Top",
            "📁 Move to Folder",
            "🎨 Change Color",
            getString(R.string.share_note),
            getString(R.string.move_to_trash)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(note.title.ifBlank { "(No title)" })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> lifecycleScope.launch { repository.togglePin(note.id); loadNotes() }
                    1 -> showMoveFolderDialog(note)
                    2 -> showChangeColorDialog(note)
                    3 -> shareNote(note)
                    4 -> lifecycleScope.launch { repository.delete(note.id); loadNotes() }
                }
            }
            .show()
    }

    private fun showMoveFolderDialog(note: Note) {
        lifecycleScope.launch {
            val folders = repository.folders().toMutableList()
            if (!folders.contains("Work")) folders.add("Work")
            if (!folders.contains("Personal")) folders.add("Personal")
            if (!folders.contains("Ideas")) folders.add("Ideas")

            val options = (listOf("General (None)") + folders + listOf("+ Create New Folder")).toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Move to Folder")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> updateNoteFolder(note, "")
                        options.size - 1 -> showCreateFolderForNoteDialog(note)
                        else -> updateNoteFolder(note, options[which])
                    }
                }
                .show()
        }
    }

    private fun showCreateFolderForNoteDialog(note: Note) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Folder Name"
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    updateNoteFolder(note, name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateNoteFolder(note: Note, newFolder: String) {
        lifecycleScope.launch {
            repository.update(
                id = note.id,
                title = note.title,
                content = note.content,
                color = note.color,
                folder = newFolder,
                tags = note.tags
            )
            loadNotes()
            buildFolderChips()
        }
    }

    private fun showChangeColorDialog(note: Note) {
        val colorNames = arrayOf(
            "Default Clean",
            "Soft Red",
            "Soft Orange",
            "Soft Yellow",
            "Mint Green",
            "Sky Blue",
            "Soft Purple"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Note Color")
            .setItems(colorNames) { _, which ->
                lifecycleScope.launch {
                    repository.update(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        color = which,
                        folder = note.folder,
                        tags = note.tags
                    )
                    loadNotes()
                }
            }
            .show()
    }

    private fun shareNote(note: Note) {
        val text = buildString {
            if (note.title.isNotBlank()) append(note.title).append("\n\n")
            append(note.content)
        }
        if (text.isBlank()) {
            Toast.makeText(requireContext(), R.string.note_empty_share, Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_note)))
    }

    private fun loadNotes() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val notes = when {
                query.isNotBlank() -> repository.search(query)
                currentFolder != null -> repository.getVisibleInFolder(currentFolder)
                else -> repository.getVisible()
            }
            adapter.submitList(notes)
            binding.emptyView.visibility =
                if (notes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun startSyncFlow() {
        if (cloudSyncManager.isLoggedIn) {
            launchSync()
        } else {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("AANO Cloud Sync")
                .setMessage("Connect your Gmail account to sync your notes seamlessly across your devices.")
                .setPositiveButton("Connect") { _, _ ->
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun launchSync(silent: Boolean = false) {
        if (!silent) Toast.makeText(requireContext(), R.string.syncing, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val res = cloudSyncManager.syncNow()
            res.onSuccess { count ->
                prefs.syncPending = false
                loadNotes()
                if (!silent) {
                    Toast.makeText(requireContext(), "AANO Cloud Sync complete ($count items)", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                prefs.syncPending = true
                if (!silent) {
                    Toast.makeText(
                        requireContext(),
                        "AANO Cloud Sync failed: ${e.message ?: "network error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun filterByFolder(folder: String?) {
        currentFolder = folder?.trim()?.takeIf {
            it.isNotBlank() && !it.equals(getString(R.string.folder_all), ignoreCase = true)
        }
        updateFilterHeader()
        loadNotes()
        buildFolderChips()
    }

    private fun openEditor(noteId: String?, defaultFolder: String? = null) {
        startActivity(
            Intent(requireContext(), EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_NOTE_ID, noteId)
                .putExtra(EditorActivity.EXTRA_FOLDER, defaultFolder ?: currentFolder ?: "")
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}