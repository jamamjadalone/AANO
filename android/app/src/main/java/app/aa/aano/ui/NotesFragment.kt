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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var adapter: NotesAdapter
    private lateinit var prefs: Prefs

    private var loadJob: Job? = null
    private var autoSyncJob: Job? = null
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

        val backCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (adapter.isSelectionMode) {
                    adapter.clearSelection()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        adapter = NotesAdapter(
            onClick = { openEditor(it.id) },
            onLongClick = { note ->
                if (!adapter.isSelectionMode) {
                    adapter.isSelectionMode = true
                    adapter.toggleSelection(note.id)
                } else {
                    showNoteActions(note)
                }
            },
            onSelectionChanged = { count ->
                backCallback.isEnabled = count > 0
                if (count > 0) {
                    binding.selectionToolbar.visibility = View.VISIBLE
                    binding.toolbar.visibility = View.GONE
                    binding.selectionTitle.text = "$count selected"
                } else {
                    binding.selectionToolbar.visibility = View.GONE
                    binding.toolbar.visibility = View.VISIBLE
                }
            }
        )
        binding.notesList.adapter = adapter

        binding.btnCancelSelection.setOnClickListener {
            adapter.clearSelection()
        }
        binding.btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }
        binding.btnBatchArchive.setOnClickListener {
            val ids = adapter.getSelectedIds()
            if (ids.isNotEmpty()) {
                lifecycleScope.launch {
                    repository.archiveMultiple(ids)
                    adapter.clearSelection()
                    loadNotes()
                    buildFolderChips()
                    Toast.makeText(requireContext(), "${ids.size} notes archived", Toast.LENGTH_SHORT).show()
                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) launchSync(silent = true)
                }
            }
        }
        binding.btnBatchDelete.setOnClickListener {
            val ids = adapter.getSelectedIds()
            if (ids.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Move to Trash")
                    .setMessage("Move ${ids.size} selected notes to Trash?")
                    .setPositiveButton("Move to Trash") { _, _ ->
                        lifecycleScope.launch {
                            repository.deleteMultiple(ids)
                            adapter.clearSelection()
                            loadNotes()
                            buildFolderChips()
                            Toast.makeText(requireContext(), "${ids.size} notes moved to Trash", Toast.LENGTH_SHORT).show()
                            if (prefs.autoSync && cloudSyncManager.isLoggedIn) launchSync(silent = true)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        binding.fabAdd.setOnClickListener { openEditor(null, currentFolder) }
        binding.clearFilterBtn.setOnClickListener { filterByFolder(null) }
        binding.emptyView.visibility = View.GONE

        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                loadNotes()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.searchInput.text?.clear()
        }

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
            R.id.action_archive -> {
                startActivity(Intent(requireContext(), ArchiveActivity::class.java)); true
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
        startForegroundAutoSync()
    }

    override fun onPause() {
        super.onPause()
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    private fun startForegroundAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = viewLifecycleOwner.lifecycleScope.launch {
            // Immediate sync on entry
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                runSilentSyncCheck()
            }
            while (isActive) {
                kotlinx.coroutines.delay(20_000L) // Responsive heartbeat (20s)
                if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                    runSilentSyncCheck()
                }
            }
        }
    }

    private suspend fun runSilentSyncCheck() {
        val res = cloudSyncManager.syncNow()
        if (res.isSuccess) {
            loadNotes()
            buildFolderChips()
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
            "📦 Archive Note",
            "📁 Move to Folder",
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
                        loadNotes()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) launchSync(silent = true)
                    }
                    1 -> lifecycleScope.launch {
                        repository.archive(note.id)
                        loadNotes()
                        buildFolderChips()
                        Toast.makeText(requireContext(), "Note archived", Toast.LENGTH_SHORT).show()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) launchSync(silent = true)
                    }
                    2 -> showMoveFolderDialog(note)
                    3 -> showChangeColorDialog(note)
                    4 -> shareNote(note)
                    5 -> lifecycleScope.launch {
                        repository.delete(note.id)
                        loadNotes()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) launchSync(silent = true)
                    }
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
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                launchSync(silent = true)
            }
        }
    }

    private fun showChangeColorDialog(note: Note) {
        val colorNames = arrayOf(
            "Default Card",
            "Pastel Lemon",
            "Pastel Mint",
            "Pastel Teal",
            "Pastel Sky",
            "Pastel Indigo",
            "Pastel Lavender",
            "Pastel Rose",
            "Pastel Peach",
            "Pastel Coral",
            "Muted Sand",
            "Muted Slate",
            "Charcoal Dark",
            "🎨 Custom Color..."
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Note Color")
            .setItems(colorNames) { _, which ->
                if (which == colorNames.lastIndex) {
                    showCustomColorPickerForNote(note)
                } else {
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
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                            launchSync(silent = true)
                        }
                    }
                }
            }
            .show()
    }

    private fun showCustomColorPickerForNote(note: Note) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val swatchesGrid = dialogView.findViewById<android.widget.GridLayout>(R.id.swatchesGrid)
        val livePreview = dialogView.findViewById<View>(R.id.liveColorPreview)
        val hexInput = dialogView.findViewById<android.widget.EditText>(R.id.hexInput)

        val swatches = listOf(
            "#FFFFFF", "#FFF9C4", "#FFF59D", "#FFE082", "#FFCC80",
            "#FFAB91", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9",
            "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB",
            "#C8E6C9", "#DCEDC8", "#EFEBE9", "#CFD8DC", "#37474F"
        )

        var currentColor = EditorActivity.resolveNoteColor(requireContext(), note.color)
        val density = resources.displayMetrics.density

        fun updatePreview(color: Int) {
            currentColor = color
            livePreview.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
                setStroke((1 * density).toInt(), 0x33000000.toInt())
            }
        }

        updatePreview(currentColor)
        val hexStr = String.format("#%06X", 0xFFFFFF and currentColor)
        hexInput.setText(hexStr)

        swatches.forEach { hex ->
            val swatchColor = android.graphics.Color.parseColor(hex)
            val dot = View(requireContext()).apply {
                val size = (34 * density).toInt()
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                }
                layoutParams = params
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(swatchColor)
                    setStroke((1 * density).toInt(), 0x33000000.toInt())
                }
                isClickable = true
                setOnClickListener {
                    updatePreview(swatchColor)
                    hexInput.setText(hex)
                }
            }
            swatchesGrid.addView(dot)
        }

        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                val text = s?.toString()?.trim() ?: ""
                if (text.startsWith("#") && (text.length == 7 || text.length == 9)) {
                    try {
                        val parsed = android.graphics.Color.parseColor(text)
                        updatePreview(parsed)
                    } catch (_: Exception) {}
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Custom Note Color")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val inputHex = hexInput.text.toString().trim()
                val finalColor = runCatching {
                    android.graphics.Color.parseColor(if (inputHex.startsWith("#")) inputHex else "#$inputHex")
                }.getOrDefault(currentColor)
                lifecycleScope.launch {
                    repository.update(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        color = finalColor,
                        folder = note.folder,
                        tags = note.tags
                    )
                    loadNotes()
                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                        launchSync(silent = true)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
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
                buildFolderChips()
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
        autoSyncJob?.cancel()
        autoSyncJob = null
        _binding = null
    }

    companion object {
        private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}