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
import app.aa.aano.drop.AanoDropManager
import app.aa.aano.drop.QrCode
import app.aa.aano.data.LocalVaultManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
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

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (!scanned.isNullOrBlank()) {
            handleScannedDropQr(scanned)
        }
    }

    private val restoreVaultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            restoreLocalVault(uri)
        }
    }

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
                if (adapter.isSelectionMode) {
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

        binding.btnCloseSearch.setOnClickListener {
            hideSearchBar()
        }

        updateFilterHeader()
        buildFolderChips()
        loadNotes()
    }

    private fun updateFilterHeader() {
        if (currentFolder == "__LOCAL__") {
            binding.filterHeaderBar.visibility = View.VISIBLE
            binding.filterStatusText.text = "⚡ Showing: On-Device Notes (Local Vault)"
        } else if (currentFolder != null) {
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
            val localCount = repository.getLocalNotes().size
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

            if (localCount > 0) {
                val localChip = com.google.android.material.chip.Chip(requireContext()).apply {
                    text = "⚡ On-Device ($localCount)"
                    isCheckable = true
                    isChecked = currentFolder == "__LOCAL__"
                    id = View.generateViewId()
                    setOnClickListener {
                        binding.folderChips.check(id)
                        filterByFolder("__LOCAL__")
                    }
                }
                binding.folderChips.addView(localChip)
            }

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
            R.id.action_search -> {
                if (binding.searchCard.visibility == View.VISIBLE) {
                    hideSearchBar()
                } else {
                    showSearchBar()
                }
                true
            }
            R.id.action_view_mode -> {
                prefs.isGridView = !prefs.isGridView
                applyLayoutManager()
                activity?.invalidateOptionsMenu()
                true
            }
            R.id.action_drop -> {
                showDropHubDialog()
                true
            }
            R.id.action_export_vault -> {
                exportLocalVault()
                true
            }
            R.id.action_restore_vault -> {
                restoreVaultLauncher.launch("application/zip")
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

    private fun showSearchBar() {
        binding.searchCard.visibility = View.VISIBLE
        binding.searchInput.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(binding.searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearchBar() {
        binding.searchCard.visibility = View.GONE
        binding.searchInput.text?.clear()
        query = ""
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        loadNotes()
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
        val pairedIp = prefs.lastDropIp
        val peerName = prefs.lastDropPeerName.ifBlank { "Connected Device" }
        val dropLabel = if (pairedIp.isNotBlank()) "⚡ Send to $peerName ($pairedIp)" else "⚡ AANO Drop (Send to PC/Phone)"

        val items = arrayOf(
            dropLabel,
            "☑️ Multi-Select Mode",
            if (note.pinned) "📌 Unpin Note" else "📌 Pin to Top",
            if (note.isLocal) "☁️ Move to Cloud Notes" else "⚡ Move to On-Device (Local Vault)",
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
                    0 -> {
                        if (pairedIp.isNotBlank()) {
                            val dropMgr = AanoDropManager.getInstance(requireContext())
                            Toast.makeText(requireContext(), "⚡ Dropping to $peerName...", Toast.LENGTH_SHORT).show()
                            lifecycleScope.launch {
                                val res = dropMgr.sendDropToDevice(
                                    targetIp = pairedIp,
                                    targetPort = 8765,
                                    notes = listOf(note),
                                    targetPin = prefs.lastDropPin
                                )
                                res.onSuccess {
                                    Toast.makeText(requireContext(), "⚡ Dropped successfully to $peerName!", Toast.LENGTH_LONG).show()
                                }.onFailure { e ->
                                    Toast.makeText(requireContext(), "Drop failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    showDropHubDialog(prefilledIp = pairedIp, prefilledPin = prefs.lastDropPin, notesToDrop = listOf(note))
                                }
                            }
                        } else {
                            showDropHubDialog(notesToDrop = listOf(note))
                        }
                    }
                    1 -> {
                        adapter.isSelectionMode = true
                        adapter.toggleSelection(note.id)
                    }
                    2 -> lifecycleScope.launch {
                        repository.togglePin(note.id)
                        loadNotes()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) launchSync(silent = true)
                    }
                    3 -> lifecycleScope.launch {
                        repository.toggleLocal(note.id)
                        loadNotes()
                        buildFolderChips()
                        Toast.makeText(requireContext(), if (note.isLocal) "Moved to Cloud Notes" else "Moved to On-Device Vault", Toast.LENGTH_SHORT).show()
                    }
                    4 -> lifecycleScope.launch {
                        repository.archive(note.id)
                        loadNotes()
                        buildFolderChips()
                        Toast.makeText(requireContext(), "Note archived", Toast.LENGTH_SHORT).show()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) launchSync(silent = true)
                    }
                    5 -> showMoveFolderDialog(note)
                    6 -> showChangeColorDialog(note)
                    7 -> shareNote(note)
                    8 -> lifecycleScope.launch {
                        repository.delete(note.id)
                        loadNotes()
                        buildFolderChips()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) launchSync(silent = true)
                    }
                }
            }
            .show()
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
                loadNotes()
                buildFolderChips()
                AlertDialog.Builder(requireContext())
                    .setTitle("Media Vault Restored")
                    .setMessage(
                        "Vault restore complete!\n\n" +
                        "• Notes Restored: ${summary.notesRestored}\n" +
                        "• Photos Extracted: ${summary.imagesRestored}\n" +
                        "• Audio Memos: ${summary.audioRestored}\n\n" +
                        "Your multimedia notes are now ready."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
                currentFolder == "__LOCAL__" -> repository.getLocalNotes()
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

    private fun launchBuiltInQrScanner() {
        val options = ScanOptions().apply {
            setPrompt("Point camera at PC screen or phone QR Code")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        }
        qrScannerLauncher.launch(options)
    }

    private var pendingNotesToDrop: List<Note>? = null

    private fun handleScannedDropQr(contents: String) {
        val uri = try { Uri.parse(contents) } catch (_: Exception) { null }
        val ip: String?
        val port: Int
        val pin: String?
        if (uri != null && uri.scheme == "aano" && uri.host == "drop") {
            ip = uri.getQueryParameter("ip")
            port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8765
            pin = uri.getQueryParameter("pin")
        } else if (contents.contains(":")) {
            val parts = contents.split(":")
            ip = parts[0].trim()
            port = parts.getOrNull(1)?.toIntOrNull() ?: 8765
            pin = parts.getOrNull(2)?.trim()
        } else {
            ip = contents.trim()
            port = 8765
            pin = null
        }

        if (!ip.isNullOrBlank()) {
            val fullIp = if (port == 8765) ip else "$ip:$port"
            prefs.lastDropIp = fullIp
            if (!pin.isNullOrBlank()) prefs.lastDropPin = pin
            lifecycleScope.launch {
                val dropMgr = AanoDropManager.getInstance(requireContext())
                val pairRes = dropMgr.pairWithPeer(ip, port, pin)
                pairRes.onSuccess { peer ->
                    prefs.lastDropPeerName = peer.name
                    Toast.makeText(requireContext(), "⚡ AANO Drop: Paired with ${peer.name} ($fullIp)!", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(requireContext(), "⚡ AANO Drop: Paired with $fullIp!", Toast.LENGTH_SHORT).show()
                }

                val notes = pendingNotesToDrop
                pendingNotesToDrop = null
                if (notes != null && notes.isNotEmpty()) {
                    Toast.makeText(requireContext(), "⚡ Dropping ${notes.size} note(s) to $fullIp...", Toast.LENGTH_SHORT).show()
                    val dropRes = dropMgr.sendDropToDevice(fullIp, notes = notes, targetPin = pin ?: prefs.lastDropPin)
                    dropRes.onSuccess {
                        Toast.makeText(requireContext(), "⚡ Dropped successfully!", Toast.LENGTH_LONG).show()
                        if (adapter.isSelectionMode) adapter.clearSelection()
                    }.onFailure { err ->
                        Toast.makeText(requireContext(), "Drop failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    showDropHubDialog(prefilledIp = fullIp, prefilledPin = pin ?: prefs.lastDropPin)
                }
            }
        } else {
            Toast.makeText(requireContext(), "Invalid QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDropHubDialog(
        prefilledIp: String? = null,
        prefilledPin: String? = null,
        notesToDrop: List<Note>? = null
    ) {
        if (notesToDrop != null) {
            pendingNotesToDrop = notesToDrop
        }
        val dropMgr = AanoDropManager.getInstance(requireContext())
        val myIp = dropMgr.getLocalWifiIp()
        val myPin = dropMgr.pin
        val myUri = "aano://drop?ip=$myIp&port=8766&pin=$myPin"

        val dm = resources.displayMetrics
        val density = dm.density
        val padH = (20 * density).toInt()
        val padV = (12 * density).toInt()

        val inputLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        // Direct 1-Tap Send Button if peer is already paired
        var dialogRef: androidx.appcompat.app.AlertDialog? = null
        val pairedIp = prefs.lastDropIp
        val pairedPeerName = prefs.lastDropPeerName.ifBlank { "Desktop PC" }
        val selectedIds = adapter.getSelectedIds()
        val isBatch = selectedIds.isNotEmpty()

        if (pairedIp.isNotBlank()) {
            val btnDirect = MaterialButton(requireContext()).apply {
                text = "⚡ Instant Drop to $pairedPeerName ($pairedIp)"
                setBackgroundColor(android.graphics.Color.parseColor("#0284C7"))
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (10 * density).toInt()) }
                setOnClickListener {
                    dialogRef?.dismiss()
                    lifecycleScope.launch {
                        val targetList = notesToDrop ?: (if (isBatch) repository.getAll().filter { selectedIds.contains(it.id) } else repository.getVisible())
                        if (targetList.isEmpty()) {
                            Toast.makeText(requireContext(), "No notes to drop!", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        Toast.makeText(requireContext(), "⚡ Dropping ${targetList.size} notes to $pairedPeerName...", Toast.LENGTH_SHORT).show()
                        val res = dropMgr.sendDropToDevice(pairedIp, notes = targetList, targetPin = prefs.lastDropPin)
                        res.onSuccess {
                            Toast.makeText(requireContext(), "⚡ Dropped successfully to $pairedPeerName!", Toast.LENGTH_LONG).show()
                            if (isBatch) adapter.clearSelection()
                        }.onFailure { e ->
                            Toast.makeText(requireContext(), "Drop failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            inputLayout.addView(btnDirect)
        }

        // Responsive QR size: ~170dp, capped at 50% screen width to guarantee fit on compact/720p screens
        val qrTargetPx = (170 * density).toInt().coerceAtMost((dm.widthPixels * 0.50).toInt())
        val qrBmp = try {
            QrCode.encodeText(myUri, QrCode.Ecc.MEDIUM).toBitmap(qrTargetPx)
        } catch (_: Exception) { null }

        if (qrBmp != null) {
            val ivQr = ImageView(requireContext()).apply {
                setImageBitmap(qrBmp)
                layoutParams = LinearLayout.LayoutParams(qrTargetPx, qrTargetPx).apply {
                    setMargins(0, 0, 0, (10 * density).toInt())
                }
                setBackgroundColor(android.graphics.Color.WHITE)
                val padQr = (8 * density).toInt()
                setPadding(padQr, padQr, padQr, padQr)
            }
            inputLayout.addView(ivQr)
        }

        val tvInfo = TextView(requireContext()).apply {
            text = "📷 Scan with another phone or PC\n📡 This Device: $myIp:8766 | PIN: $myPin"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        inputLayout.addView(tvInfo)

        val btnScanCamera = MaterialButton(requireContext()).apply {
            text = "📷 Scan PC / Phone QR (Built-in Scanner)"
            setOnClickListener {
                dialogRef?.dismiss()
                launchBuiltInQrScanner()
            }
        }
        inputLayout.addView(btnScanCamera)

        val btnRadar = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "🔍 Wi-Fi Radar (Auto-Find PC)"
            setOnClickListener {
                text = "Scanning Subnet..."
                isEnabled = false
                lifecycleScope.launch {
                    val peers = dropMgr.discoverNearbyPeers()
                    isEnabled = true
                    text = "🔍 Wi-Fi Radar (Auto-Find PC)"
                    if (peers.isEmpty()) {
                        Toast.makeText(requireContext(), "No nearby AANO devices detected on Wi-Fi", Toast.LENGTH_SHORT).show()
                    } else {
                        val peerItems = peers.map { "${it.name} (${it.ip}:${it.port})" }.toTypedArray()
                        AlertDialog.Builder(requireContext())
                            .setTitle("Select Nearby Device")
                            .setItems(peerItems) { _, which ->
                                val selectedPeer = peers[which]
                                lifecycleScope.launch {
                                    val pairRes = dropMgr.pairWithPeer(selectedPeer.ip, selectedPeer.port)
                                    pairRes.onSuccess {
                                        prefs.lastDropIp = if (selectedPeer.port == 8765) selectedPeer.ip else "${selectedPeer.ip}:${selectedPeer.port}"
                                        prefs.lastDropPeerName = selectedPeer.name
                                        Toast.makeText(requireContext(), "Paired with ${selectedPeer.name}!", Toast.LENGTH_SHORT).show()
                                        dialogRef?.dismiss()
                                        showDropHubDialog(prefilledIp = prefs.lastDropIp, prefilledPin = prefs.lastDropPin, notesToDrop = notesToDrop)
                                    }
                                }
                            }
                            .show()
                    }
                }
            }
        }
        inputLayout.addView(btnRadar)

        val etIp = EditText(requireContext()).apply {
            hint = "Target IP (e.g. 192.168.1.5:8765)"
            setText(prefilledIp ?: prefs.lastDropIp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 0) }
        }
        val etPin = EditText(requireContext()).apply {
            hint = "Target 6-Digit PIN (Optional)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefilledPin ?: prefs.lastDropPin)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        inputLayout.addView(etIp)
        inputLayout.addView(etPin)

        val btnActionText = if (notesToDrop != null) "Drop ${notesToDrop.size} Note(s)" else if (isBatch) "Drop ${selectedIds.size} Notes" else "Drop All Active Notes"

        val scrollView = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(inputLayout)
        }

        dialogRef = MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚡ AANO Drop (Wi-Fi P2P)")
            .setMessage("Instant local transfer with zero cloud data.")
            .setView(scrollView)
            .setPositiveButton(btnActionText) { _, _ ->
                val ip = etIp.text.toString().trim()
                val pin = etPin.text.toString().trim()
                if (ip.isNotBlank()) {
                    prefs.lastDropIp = ip
                    if (pin.isNotBlank()) prefs.lastDropPin = pin
                    lifecycleScope.launch {
                        val targetList = notesToDrop ?: (if (isBatch) {
                            repository.getAll().filter { selectedIds.contains(it.id) }
                        } else {
                            repository.getVisible()
                        })
                        if (targetList.isEmpty()) {
                            Toast.makeText(requireContext(), "No notes to drop!", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        Toast.makeText(requireContext(), "Sending ${targetList.size} notes...", Toast.LENGTH_SHORT).show()
                        val res = dropMgr.sendDropToDevice(ip, notes = targetList, targetPin = pin.ifBlank { prefs.lastDropPin.ifBlank { null } })
                        res.onSuccess {
                            Toast.makeText(requireContext(), "⚡ Successfully dropped ${targetList.size} notes to $ip!", Toast.LENGTH_LONG).show()
                            if (isBatch) adapter.clearSelection()
                        }.onFailure { err ->
                            Toast.makeText(requireContext(), "Drop failed: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Please enter or scan target IP", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialogRef?.show()
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