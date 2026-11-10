// Notes tab: list, search, sort, pin, trash and Google sync.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.pages

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.journeyapps.barcodescanner.ScanContract
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.components.adapters.NotesAdapter
import app.aa.aano.components.dialogs.DropHubDialogHelper
import app.aa.aano.components.dialogs.LocalDropCoordinator
import app.aa.aano.components.dialogs.NoteActionDialogHelper
import app.aa.aano.components.dialogs.NotesBatchActionHelper
import app.aa.aano.components.dialogs.NotesColorDialogHelper
import app.aa.aano.components.views.NotesChipBarHelper
import app.aa.aano.components.views.NotesSearchBarHelper
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.FragmentNotesBinding
import app.aa.aano.net.CloudSyncManager
import app.aa.aano.net.NotesSyncCoordinator
import app.aa.aano.ui.EditorActivity

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var adapter: NotesAdapter
    private lateinit var prefs: Prefs
    private lateinit var dropCoordinator: LocalDropCoordinator
    private lateinit var syncCoordinator: NotesSyncCoordinator

    private var loadJob: Job? = null
    private var query: String = ""
    private var currentFolder: String? = null
    private var currentColorFilter: Int? = null
    private var pendingNotesToDrop: List<Note>? = null

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (!scanned.isNullOrBlank()) {
            val notes = pendingNotesToDrop
            pendingNotesToDrop = null
            dropCoordinator.handleScannedQr(
                contents = scanned,
                pendingNotes = notes,
                onStatusUpdated = {},
                onShowHubNeeded = { showDropHubDialog(null) }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        repository = NoteRepository(requireContext())
        cloudSyncManager = CloudSyncManager(requireContext(), repository, TaskRepository(requireContext()))
        prefs = Prefs(requireContext())
        dropCoordinator = LocalDropCoordinator(requireContext(), lifecycleScope, prefs)
        syncCoordinator = NotesSyncCoordinator(requireContext(), viewLifecycleOwner.lifecycleScope, cloudSyncManager, prefs) { refreshAll() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? HomeActivity)?.consumePendingFolder()?.let { currentFolder = it }

        applyLayoutManager()

        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (adapter.isSelectionMode) adapter.clearSelection()
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
                binding.selectionToolbar.visibility = if (count > 0) View.VISIBLE else View.GONE
                binding.toolbar.visibility = if (count > 0) View.GONE else View.VISIBLE
                if (count > 0) binding.selectionTitle.text = "$count selected"
            }
        )
        binding.notesList.adapter = adapter

        NotesBatchActionHelper.setupSelectionBar(binding, adapter, requireContext(), lifecycleScope, repository, prefs, cloudSyncManager) { refreshAll() }
        setupSearchAndFilters()
        refreshAll()
    }

    private fun setupSearchAndFilters() {
        binding.fabAdd.setOnClickListener { openEditor(null, currentFolder) }
        binding.clearFilterBtn.setOnClickListener {
            currentColorFilter = null
            filterByFolder(null)
            refreshChips()
        }
        binding.searchInput.addTextChangedListener { s ->
            query = s?.toString()?.trim().orEmpty()
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            loadNotes()
        }
        binding.btnClearSearch.setOnClickListener { binding.searchInput.text?.clear() }
        binding.btnCloseSearch.setOnClickListener {
            NotesSearchBarHelper.hideSearchBar(binding, requireContext()) {
                query = ""
                loadNotes()
            }
        }
    }

    private fun refreshAll() {
        updateFilterHeader()
        refreshChips()
        loadNotes()
    }

    private fun refreshChips() {
        NotesChipBarHelper.buildFolderChips(requireContext(), lifecycleScope, repository, binding.folderChips, currentFolder) { filterByFolder(it) }
        NotesChipBarHelper.buildPaletteChips(
            requireContext(), lifecycleScope, repository, prefs, binding.paletteChips, currentColorFilter, NotesChipBarHelper.COLOR_LABELS,
            onColorSelected = {
                currentColorFilter = it
                updateFilterHeader()
                loadNotes()
            },
            onRenameRequested = { idx, curr, def ->
                NotesColorDialogHelper.showRenameColorDialog(requireContext(), prefs, idx, curr, def) {
                    refreshChips()
                    updateFilterHeader()
                }
            }
        )
    }

    private fun updateFilterHeader() {
        NotesChipBarHelper.updateFilterHeader(
            headerBar = binding.filterHeaderBar,
            statusText = binding.filterStatusText,
            currentFolder = currentFolder,
            currentColorFilter = currentColorFilter,
            prefs = prefs
        )
    }

    private fun loadNotes() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val baseNotes = when {
                query.isNotBlank() -> repository.search(query)
                currentFolder == "__LOCAL__" -> repository.getLocalNotes()
                currentFolder != null -> repository.getVisibleInFolder(currentFolder)
                else -> repository.getVisible()
            }
            val notes = if (currentColorFilter != null) baseNotes.filter { it.color == currentColorFilter } else baseNotes
            adapter.submitList(notes)
            binding.emptyView.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showNoteActions(note: Note) {
        NoteActionDialogHelper.showNoteActions(
            context = requireContext(),
            scope = lifecycleScope,
            note = note,
            repository = repository,
            prefs = prefs,
            cloudSyncManager = cloudSyncManager,
            onDropRequested = { showDropHubDialog(listOf(it)) },
            onMultiSelectRequested = {
                adapter.isSelectionMode = true
                adapter.toggleSelection(it.id)
            },
            onChangeColorRequested = {
                NotesColorDialogHelper.showChangeColorDialog(
                    requireContext(), lifecycleScope, it, repository, prefs, cloudSyncManager,
                    onRefreshNeeded = { refreshAll() },
                    onSyncNeeded = { syncCoordinator.launchSync(silent = true) }
                )
            },
            onRefreshNeeded = { refreshAll() },
            onSyncNeeded = { syncCoordinator.launchSync(silent = true) }
        )
    }

    private fun showDropHubDialog(notesToDrop: List<Note>? = null) {
        if (notesToDrop != null) pendingNotesToDrop = notesToDrop
        DropHubDialogHelper.showDropHubDialog(
            context = requireContext(),
            scope = lifecycleScope,
            prefs = prefs,
            notesToDrop = notesToDrop,
            onScanQrRequested = { dropCoordinator.launchQrScanner(qrScannerLauncher) },
            onStatusUpdated = {},
            onDropSuccess = { if (adapter.isSelectionMode) adapter.clearSelection() }
        )
    }

    fun filterByFolder(folder: String?) {
        currentFolder = folder?.trim()?.takeIf { it.isNotBlank() && !it.equals(getString(R.string.folder_all), ignoreCase = true) }
        refreshAll()
    }

    private fun openEditor(noteId: String?, defaultFolder: String? = null) {
        startActivity(
            Intent(requireContext(), EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_NOTE_ID, noteId)
                .putExtra(EditorActivity.EXTRA_FOLDER, defaultFolder ?: currentFolder ?: "")
        )
    }

    private fun applyLayoutManager() {
        app.aa.aano.components.views.NotesMenuHelper.applyLayoutManager(binding.notesList, requireContext(), prefs.isGridView)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        app.aa.aano.components.views.NotesMenuHelper.inflateMenu(menu, inflater, prefs)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val handled = app.aa.aano.components.views.NotesMenuHelper.handleItemSelected(
            item = item,
            context = requireContext(),
            prefs = prefs,
            syncCoordinator = syncCoordinator,
            onSearch = { NotesSearchBarHelper.showSearchBar(binding, requireContext()) },
            onToggleLayout = {
                applyLayoutManager()
                activity?.invalidateOptionsMenu()
            },
            onSortNotes = { loadNotes() },
            onDropHub = { showDropHubDialog() }
        )
        return if (handled) true else super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        (activity as? HomeActivity)?.consumePendingFolder()?.let { currentFolder = it }
        refreshAll()
        lifecycleScope.launch { repository.purgeExpiredTrash(TRASH_RETENTION_MS) }
        syncCoordinator.startForegroundAutoSync()
    }

    override fun onPause() {
        super.onPause()
        syncCoordinator.stopForegroundAutoSync()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        syncCoordinator.stopForegroundAutoSync()
        loadJob?.cancel()
        _binding = null
    }

    companion object {
        private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        val COLOR_LABELS get() = NotesChipBarHelper.COLOR_LABELS
    }
}
