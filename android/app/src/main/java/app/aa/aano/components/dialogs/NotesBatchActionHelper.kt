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
        binding.btnBatchColor.setOnClickListener {
            handleBatchColor(context, scope, adapter.getSelectedIds(), repository, prefs, cloudSyncManager) {
                adapter.clearSelection()
                onRefreshAll()
            }
        }
        binding.btnBatchFolder.setOnClickListener {
            handleBatchFolder(context, scope, adapter.getSelectedIds(), repository, prefs, cloudSyncManager) {
                adapter.clearSelection()
                onRefreshAll()
            }
        }
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

    fun handleBatchColor(
        context: Context,
        scope: CoroutineScope,
        selectedIds: Set<String>,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onComplete: () -> Unit
    ) {
        if (selectedIds.isEmpty()) return
        val colorNames = arrayOf(
            "Default Card", "Pastel Lemon", "Pastel Mint", "Pastel Teal",
            "Pastel Sky", "Pastel Indigo", "Pastel Lavender", "Pastel Rose",
            "Pastel Peach", "Pastel Coral", "Muted Sand", "Muted Slate", "Charcoal Dark"
        )
        AlertDialog.Builder(context)
            .setTitle("Set Color for ${selectedIds.size} Notes")
            .setItems(colorNames) { _, which ->
                scope.launch {
                    repository.setColorMultiple(selectedIds, which, which)
                    onComplete()
                    Toast.makeText(context, "${selectedIds.size} notes updated to ${colorNames[which]}", Toast.LENGTH_SHORT).show()
                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) cloudSyncManager.syncNow()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun handleBatchFolder(
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
            val folders = repository.folders().filter { it.isNotBlank() }
            val folderOptions = mutableListOf("None (Root)")
            folderOptions.addAll(folders)
            folderOptions.add("+ New Folder...")

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                AlertDialog.Builder(context)
                    .setTitle("Move ${selectedIds.size} Notes to Folder")
                    .setItems(folderOptions.toTypedArray()) { _, which ->
                        when (which) {
                            0 -> {
                                scope.launch {
                                    repository.setFolderMultiple(selectedIds, "")
                                    onComplete()
                                    Toast.makeText(context, "Moved to Root", Toast.LENGTH_SHORT).show()
                                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) cloudSyncManager.syncNow()
                                }
                            }
                            folderOptions.lastIndex -> {
                                promptNewFolderForBatch(context, scope, selectedIds, repository, prefs, cloudSyncManager, onComplete)
                            }
                            else -> {
                                val target = folderOptions[which]
                                scope.launch {
                                    repository.setFolderMultiple(selectedIds, target)
                                    onComplete()
                                    Toast.makeText(context, "Moved to $target", Toast.LENGTH_SHORT).show()
                                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) cloudSyncManager.syncNow()
                                }
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun promptNewFolderForBatch(
        context: Context,
        scope: CoroutineScope,
        selectedIds: Set<String>,
        repository: NoteRepository,
        prefs: Prefs,
        cloudSyncManager: CloudSyncManager,
        onComplete: () -> Unit
    ) {
        val input = android.widget.EditText(context).apply {
            hint = "Folder Name"
            setSingleLine()
        }
        val container = android.widget.FrameLayout(context).apply {
            setPadding(48, 16, 48, 16)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle("New Folder")
            .setView(container)
            .setPositiveButton("Create & Move") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    scope.launch {
                        repository.addFolder(name)
                        repository.setFolderMultiple(selectedIds, name)
                        onComplete()
                        Toast.makeText(context, "Moved to $name", Toast.LENGTH_SHORT).show()
                        if (prefs.autoSync && cloudSyncManager.isLoggedIn) cloudSyncManager.syncNow()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
