// Trash screen: restore or permanently delete notes.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.databinding.ActivityTrashBinding
import kotlinx.coroutines.launch

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private lateinit var repository: NoteRepository
    private lateinit var adapter: TrashAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        repository = NoteRepository(this)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = TrashAdapter(
            onRestore = { restore(it) },
            onPurge = { confirmPurge(it) }
        )
        binding.trashList.layoutManager = LinearLayoutManager(this)
        binding.trashList.adapter = adapter

        binding.btnEmptyTrash.setOnClickListener { confirmEmpty() }
        loadTrash()
    }

    private fun loadTrash() {
        lifecycleScope.launch {
            val trashed = repository.getTrashed()
            adapter.submitList(trashed)
            binding.emptyView.visibility =
                if (trashed.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun restore(note: Note) {
        lifecycleScope.launch {
            repository.restore(note.id)
            loadTrash()
        }
    }

    private fun confirmPurge(note: Note) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_forever)
            .setMessage(R.string.delete_forever_confirm)
            .setPositiveButton(R.string.delete_forever) { _, _ ->
                lifecycleScope.launch {
                    repository.purge(note.id)
                    loadTrash()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmEmpty() {
        AlertDialog.Builder(this)
            .setTitle(R.string.empty_trash)
            .setMessage(R.string.empty_trash_confirm)
            .setPositiveButton(R.string.empty_trash) { _, _ ->
                lifecycleScope.launch {
                    repository.getTrashed().forEach { repository.purge(it.id) }
                    loadTrash()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}