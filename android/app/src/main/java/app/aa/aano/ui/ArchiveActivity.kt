// Archive screen: list, unarchive or delete archived notes.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.ActivityArchiveBinding
import app.aa.aano.net.CloudSyncManager
import kotlinx.coroutines.launch

class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var repository: NoteRepository
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var prefs: Prefs
    private lateinit var adapter: ArchiveAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        repository = NoteRepository(this)
        cloudSyncManager = CloudSyncManager(this, repository, TaskRepository(this))
        prefs = Prefs(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ArchiveAdapter(
            onClick = { note ->
                startActivity(
                    Intent(this, EditorActivity::class.java)
                        .putExtra(EditorActivity.EXTRA_NOTE_ID, note.id)
                )
            },
            onUnarchive = { unarchive(it) },
            onDelete = { delete(it) }
        )
        binding.archiveList.layoutManager = LinearLayoutManager(this)
        binding.archiveList.adapter = adapter

        loadArchive()
    }

    override fun onResume() {
        super.onResume()
        loadArchive()
    }

    private fun loadArchive() {
        lifecycleScope.launch {
            val archived = repository.getArchived()
            adapter.submitList(archived)
            binding.emptyView.visibility =
                if (archived.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun unarchive(note: Note) {
        lifecycleScope.launch {
            repository.unarchive(note.id)
            Toast.makeText(this@ArchiveActivity, "Note unarchived", Toast.LENGTH_SHORT).show()
            loadArchive()
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                cloudSyncManager.syncNow()
            }
        }
    }

    private fun delete(note: Note) {
        lifecycleScope.launch {
            repository.delete(note.id)
            Toast.makeText(this@ArchiveActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
            loadArchive()
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                cloudSyncManager.syncNow()
            }
        }
    }
}
