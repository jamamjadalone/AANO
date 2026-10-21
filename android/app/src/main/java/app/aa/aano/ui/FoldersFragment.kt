// Dedicated Folders screen for AANO
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.R
import app.aa.aano.data.NoteRepository
import app.aa.aano.databinding.FragmentFoldersBinding
import kotlinx.coroutines.launch

class FoldersFragment : Fragment() {

    private var _binding: FragmentFoldersBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private val folderList = mutableListOf<Pair<String, Int>>()
    private lateinit var adapter: FoldersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NoteRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.foldersList.layoutManager = LinearLayoutManager(requireContext())
        adapter = FoldersAdapter(
            items = folderList,
            onClick = { folderName ->
                (activity as? HomeActivity)?.openNotesInFolder(folderName)
            },
            onOptionsClick = { folderName ->
                showFolderOptions(folderName)
            }
        )
        binding.foldersList.adapter = adapter

        binding.fabAddFolder.setOnClickListener {
            showCreateFolderDialog()
        }

        loadFolders()
    }

    override fun onResume() {
        super.onResume()
        loadFolders()
    }

    private fun loadFolders() {
        lifecycleScope.launch {
            val counts = repository.getFolderCounts()
            folderList.clear()
            counts.forEach { (name, count) ->
                folderList.add(Pair(name, count))
            }
            folderList.sortBy { it.first }
            adapter.notifyDataSetChanged()

            binding.emptyFoldersView.visibility =
                if (folderList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showFolderOptions(folderName: String) {
        val items = if (folderName.equals("General", ignoreCase = true)) {
            arrayOf("➕ New Note in this Folder", "📁 View Notes")
        } else {
            arrayOf("✏️ Rename Folder", "➕ New Note in this Folder", "🗑️ Delete Folder")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Folder: $folderName")
            .setItems(items) { _, which ->
                if (folderName.equals("General", ignoreCase = true)) {
                    when (which) {
                        0 -> createNoteInFolder("")
                        1 -> (activity as? HomeActivity)?.openNotesInFolder(folderName)
                    }
                } else {
                    when (which) {
                        0 -> showRenameFolderDialog(folderName)
                        1 -> createNoteInFolder(folderName)
                        2 -> showDeleteFolderDialog(folderName)
                    }
                }
            }
            .show()
    }

    private fun createNoteInFolder(folderName: String) {
        val folder = if (folderName.equals("General", ignoreCase = true)) "" else folderName
        startActivity(
            android.content.Intent(requireContext(), EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_FOLDER, folder)
        )
    }

    private fun showRenameFolderDialog(oldName: String) {
        val input = EditText(requireContext()).apply {
            hint = "New folder name"
            setText(oldName)
            setSelection(oldName.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Folder")
            .setView(container)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank() && newName != oldName) {
                    lifecycleScope.launch {
                        repository.renameFolder(oldName, newName)
                        loadFolders()
                        Toast.makeText(requireContext(), "Folder renamed to \"$newName\"", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteFolderDialog(folderName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Folder")
            .setMessage("Do you want to delete folder \"$folderName\"? Notes inside will be moved to General.")
            .setPositiveButton("Delete Folder") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteFolder(folderName, deleteNotes = false)
                    loadFolders()
                    Toast.makeText(requireContext(), "Folder deleted (notes moved to General)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateFolderDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Folder Name (e.g. Work, Ideas)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create New Folder")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    repository.addFolder(name)
                    loadFolders()
                    Toast.makeText(requireContext(), "Folder \"$name\" created", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class FoldersAdapter(
        private val items: List<Pair<String, Int>>,
        private val onClick: (String) -> Unit,
        private val onOptionsClick: (String) -> Unit
    ) : RecyclerView.Adapter<FoldersAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.folderTitle)
            val count: TextView = itemView.findViewById(R.id.folderCountBadge)
            val menuBtn: View = itemView.findViewById(R.id.folderMenuBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_folder, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.first
            holder.count.text = item.second.toString()
            holder.itemView.setOnClickListener { onClick(item.first) }
            holder.itemView.setOnLongClickListener {
                onOptionsClick(item.first)
                true
            }
            holder.menuBtn.setOnClickListener { onOptionsClick(item.first) }
        }

        override fun getItemCount(): Int = items.size
    }
}
