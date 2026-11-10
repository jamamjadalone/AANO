// Dedicated Local (On-Device) screen for AANO multimedia notes
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.pages

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.components.adapters.NotesAdapter
import app.aa.aano.components.dialogs.DropHubDialogHelper
import app.aa.aano.components.dialogs.LocalDropCoordinator
import app.aa.aano.components.dialogs.LocalNoteActionDialogHelper
import app.aa.aano.components.dialogs.LocalVaultDialogHelper
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.databinding.FragmentLocalBinding
import app.aa.aano.drop.AanoDropManager

class LocalFragment : Fragment() {

    private var _binding: FragmentLocalBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private lateinit var prefs: Prefs
    private lateinit var adapter: NotesAdapter
    private lateinit var dropCoordinator: LocalDropCoordinator
    private var loadJob: Job? = null
    private var pendingNotesToDrop: List<Note>? = null

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (!scanned.isNullOrBlank()) {
            val notes = pendingNotesToDrop
            pendingNotesToDrop = null
            dropCoordinator.handleScannedQr(
                contents = scanned,
                pendingNotes = notes,
                onStatusUpdated = { updatePeerStatus() },
                onShowHubNeeded = { showDropHubDialog(null) }
            )
        }
    }

    private val restoreVaultLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            LocalVaultDialogHelper.restoreLocalVault(
                context = requireContext(),
                scope = lifecycleScope,
                uri = uri,
                repository = repository,
                onSuccess = { loadLocalNotes() }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NoteRepository(requireContext())
        prefs = Prefs(requireContext())
        dropCoordinator = LocalDropCoordinator(requireContext(), lifecycleScope, prefs)
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

        binding.fabAddLocal.setOnClickListener { openEditor(null) }
        binding.btnExportVault.setOnClickListener {
            LocalVaultDialogHelper.exportLocalVault(
                context = requireContext(),
                scope = lifecycleScope,
                repository = repository,
                onShareVault = { startActivity(it) }
            )
        }

        binding.btnRestoreVault.setOnClickListener {
            restoreVaultLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
        }

        binding.toolbar.inflateMenu(R.menu.menu_local)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_drop_hub) {
                showDropHubDialog()
                true
            } else false
        }

        adapter.onSelectionChanged = { count ->
            if (count > 0) {
                binding.cardSelectionBar.visibility = View.VISIBLE
                binding.tvSelectionCount.text = "$count selected"
                binding.fabAddLocal.hide()
            } else {
                binding.cardSelectionBar.visibility = View.GONE
                binding.fabAddLocal.show()
            }
        }

        binding.btnCancelSelection.setOnClickListener {
            adapter.clearSelection()
            binding.cardSelectionBar.visibility = View.GONE
            binding.fabAddLocal.show()
        }

        binding.btnDisconnectPeer.setOnClickListener {
            prefs.lastDropIp = ""
            prefs.lastDropPeerName = ""
            prefs.lastDropPin = ""
            updatePeerStatus()
            Toast.makeText(requireContext(), "Disconnected from peer", Toast.LENGTH_SHORT).show()
        }

        binding.btnDropSelected.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                val notes = repository.getLocalNotes().filter { it.id in selectedIds }
                if (notes.isNotEmpty()) {
                    val target = prefs.lastDropIp
                    if (target.isBlank()) {
                        showDropHubDialog(notesToDrop = notes)
                    } else {
                        DropHubDialogHelper.performBatchDrop(
                            context = requireContext(),
                            scope = lifecycleScope,
                            prefs = prefs,
                            targetIp = target,
                            pin = prefs.lastDropPin,
                            notesToSend = notes,
                            onSuccess = {
                                updatePeerStatus()
                                adapter.clearSelection()
                                binding.cardSelectionBar.visibility = View.GONE
                                binding.fabAddLocal.show()
                            }
                        )
                    }
                }
            }
        }

        updatePeerStatus()
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
        updatePeerStatus()
        AanoDropManager.getInstance(requireContext()).onNotesReceivedListener = { _, _ ->
            lifecycleScope.launch {
                loadLocalNotes()
                updatePeerStatus()
            }
        }
    }

    private fun updatePeerStatus() {
        if (_binding == null) return
        val peerIp = prefs.lastDropIp
        val peerName = prefs.lastDropPeerName.ifBlank { "Desktop PC" }
        if (peerIp.isNotBlank()) {
            binding.cardDropPeer.visibility = View.VISIBLE
            binding.tvPeerName.text = "Connected: $peerName"
            binding.tvPeerEndpoint.text = peerIp
        } else {
            binding.cardDropPeer.visibility = View.GONE
        }
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
        LocalNoteActionDialogHelper.showLocalNoteActions(
            context = requireContext(),
            scope = lifecycleScope,
            note = note,
            repository = repository,
            prefs = prefs,
            onDropRequested = { targetDevice ->
                if (targetDevice.isNotBlank()) {
                    DropHubDialogHelper.performBatchDrop(
                        context = requireContext(),
                        scope = lifecycleScope,
                        prefs = prefs,
                        targetIp = targetDevice,
                        pin = "",
                        notesToSend = listOf(note)
                    )
                } else {
                    showDropHubDialog(notesToDrop = listOf(note))
                }
            },
            onMultiSelectRequested = {
                adapter.isSelectionMode = true
                adapter.toggleSelection(note.id)
            },
            onRefreshNeeded = { loadLocalNotes() },
            onShareRequested = { n -> startActivity(LocalNoteActionDialogHelper.buildShareIntent(requireContext(), n)) }
        )
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

    private fun launchBuiltInQrScanner() {
        dropCoordinator.launchQrScanner(qrScannerLauncher)
    }

    private fun showDropHubDialog(notesToDrop: List<Note>? = null) {
        if (notesToDrop != null) {
            pendingNotesToDrop = notesToDrop
        }
        DropHubDialogHelper.showDropHubDialog(
            context = requireContext(),
            scope = lifecycleScope,
            prefs = prefs,
            notesToDrop = notesToDrop,
            onScanQrRequested = { launchBuiltInQrScanner() },
            onStatusUpdated = { updatePeerStatus() }
        )
    }
}
