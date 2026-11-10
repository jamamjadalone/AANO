// NotesBatchActionHelper: Multi-select batch operations (Archive, Delete, Drop)
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.net.CloudSyncManager

object NotesBatchActionHelper {

    fun setupSelectionBar(
        binding: app.aa.aano.databinding.FragmentNotesBinding,
        adapter: app.aa.aano.components.adapters.NotesAdapter,
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onRefreshAll: () -> Unit
    ) {
        binding.btnCancelSelection.setOnClickListener { adapter.clearSelection() }
        binding.btnSelectAll.setOnClickListener { adapter.selectAll() }
        binding.btnBatchArchive.setOnClickListener {
            handleBatchArchive(context, scope, adapter.getSelectedIds(), repository, prefs, cloudSyncManager) {
                adapter.clearSelection()
                onRefreshAll()
            }
        }
        binding.btnBatchDelete.setOnClickListener {
            handleBatchDelete(context, scope, adapter.getSelectedIds(), repository, prefs, cloudSyncManager) {
                adapter.clearSelection()
                onRefreshAll()
            }
        }
    }

    fun handleBatchArchive(
        context: Context,
        scope: CoroutineScope,
        selectedIds: Set<String>,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onComplete: () -> Unit
    ) {
        if (selectedIds.isEmpty()) return
        scope.launch {
            repository.archiveMultiple(selectedIds)
            onComplete()
            Toast.makeText(context, "${selectedIds.size} notes archived", Toast.LENGTH_SHORT).show()
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                cloudSyncManager.syncNow()
            }
        }
    }

    fun handleBatchDelete(
        context: Context,
        scope: CoroutineScope,
        selectedIds: Set<String>,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onComplete: () -> Unit
    ) {
        if (selectedIds.isEmpty()) return
        AlertDialog.Builder(context)
            .setTitle("Move to Trash")
            .setMessage("Move ${selectedIds.size} selected notes to Trash?")
            .setPositiveButton("Move to Trash") { _, _ ->
                scope.launch {
                    repository.deleteMultiple(selectedIds)
                    onComplete()
                    Toast.makeText(context, "${selectedIds.size} notes moved to Trash", Toast.LENGTH_SHORT).show()
                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                        cloudSyncManager.syncNow()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun handleBatchDrop(
        context: Context,
        scope: CoroutineScope,
        selectedIds: Set<String>,
        repository: NoteRepository,
        prefs: Prefs,
        onShowHubNeeded: (notes: List<Note>) -> Unit,
        onSuccess: () -> Unit
    ) {
        if (selectedIds.isEmpty()) return
        scope.launch {
            val allNotes = repository.getAll().filter { it.id in selectedIds }
            if (allNotes.isEmpty()) {
                Toast.makeText(context, "No notes selected for drop", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val targetIp = prefs.lastDropIp
            if (targetIp.isBlank()) {
                onShowHubNeeded(allNotes)
            } else {
                DropHubDialogHelper.performBatchDrop(
                    context = context,
                    scope = scope,
                    prefs = prefs,
                    targetIp = targetIp,
                    pin = prefs.lastDropPin,
                    notesToSend = allNotes,
                    onSuccess = onSuccess
                )
            }
        }
    }
}
