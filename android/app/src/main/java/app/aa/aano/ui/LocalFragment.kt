// Dedicated Local (On-Device) screen for AANO multimedia notes
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.drop.AanoDropManager
import app.aa.aano.drop.QrCode


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import app.aa.aano.R
import app.aa.aano.data.LocalVaultManager
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.databinding.FragmentLocalBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LocalFragment : Fragment() {

    private var _binding: FragmentLocalBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private lateinit var prefs: Prefs
    private lateinit var adapter: NotesAdapter
    private var loadJob: Job? = null

    private val restoreVaultLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreLocalVault(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NoteRepository(requireContext())
        prefs = Prefs(requireContext())
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

        binding.fabAddLocal.setOnClickListener {
            openEditor(null)
        }

        binding.btnExportVault.setOnClickListener {
            exportLocalVault()
        }

        binding.btnRestoreVault.setOnClickListener {
            restoreVaultLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
        }

        // Toolbar AANO Drop Wi-Fi Radar icon
        binding.toolbar.inflateMenu(R.menu.menu_local)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_drop_hub) {
                showDropHubDialog()
                true
            } else false
        }

        // Multi-select listener
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
                        performBatchDrop(target, "", notes)
                    }
                }
            }
        }

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
        val targetDevice = prefs.lastDropIp
        val dropLabel = if (targetDevice.isNotBlank()) "⚡ Send to Device ($targetDevice)" else "⚡ Connect & Drop (AANO Drop)"

        val items = arrayOf(
            dropLabel,
            "☑️ Multi-Select Notes",
            if (note.pinned) "📌 Unpin Note" else "📌 Pin to Top",
            "☁️ Move to Cloud Notes",
            "🎨 Change Color",
            getString(R.string.share_note),
            "🗑️ Delete Note (Permanently)"
        )
        AlertDialog.Builder(requireContext())
            .setTitle(note.title.ifBlank { "(No title)" })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        if (targetDevice.isNotBlank()) {
                            performBatchDrop(targetDevice, "", listOf(note))
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
                        loadLocalNotes()
                    }
                    3 -> lifecycleScope.launch {
                        repository.toggleLocal(note.id)
                        loadLocalNotes()
                        Toast.makeText(requireContext(), "Moved to Cloud Notes", Toast.LENGTH_SHORT).show()
                    }
                    4 -> showChangeColorDialog(note)
                    5 -> shareNote(note)
                    6 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Delete Local Note")
                            .setMessage("Permanently delete this local note and attached photos/voice memos from this device? This action cannot be undone.")
                            .setPositiveButton("Delete Permanently") { _, _ ->
                                lifecycleScope.launch {
                                    repository.deleteLocalPermanently(note.id)
                                    loadLocalNotes()
                                    Toast.makeText(requireContext(), "Local note permanently deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showChangeColorDialog(note: Note) {
        val colors = EditorActivity.presetColors()
        val names = arrayOf("Default", "Coral", "Peach", "Sand", "Mint", "Sage", "Sky", "Lavender", "Blush", "Clay")
        AlertDialog.Builder(requireContext())
            .setTitle("Change Color")
            .setItems(names) { _, which ->
                val newColor = colors[which]
                lifecycleScope.launch {
                    repository.update(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        color = newColor,
                        folder = note.folder,
                        tags = note.tags
                    )
                    loadLocalNotes()
                }
            }
            .show()
    }

    private fun shareNote(note: Note) {
        val text = buildString {
            if (note.title.isNotBlank()) append(note.title).append("\n\n")
            append(note.content)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_note)))
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
                loadLocalNotes()
                AlertDialog.Builder(requireContext())
                    .setTitle("Media Vault Restored")
                    .setMessage(
                        "Vault restore complete!\n\n" +
                        "• Local Notes: ${summary.notesRestored}\n" +
                        "• Photos Extracted: ${summary.imagesRestored}\n" +
                        "• Audio Memos: ${summary.audioRestored}\n\n" +
                        "Your local multimedia notes are now ready."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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

    private fun showDropHubDialog(notesToDrop: List<Note>? = null) {
        val dropMgr = AanoDropManager.getInstance(requireContext())
        val myIp = dropMgr.getLocalWifiIp()
        val myPin = dropMgr.pin
        val myUri = "aano://drop?ip=$myIp&port=8766&pin=$myPin"

        val inputLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 16, 40, 10)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val qrBmp = try {
            QrCode.encodeText(myUri, QrCode.Ecc.MEDIUM).toBitmap(360)
        } catch (_: Exception) { null }

        if (qrBmp != null) {
            val ivQr = ImageView(requireContext()).apply {
                setImageBitmap(qrBmp)
                layoutParams = LinearLayout.LayoutParams(360, 360).apply {
                    setMargins(0, 0, 0, 12)
                }
                setBackgroundColor(android.graphics.Color.WHITE)
                setPadding(12, 12, 12, 12)
            }
            inputLayout.addView(ivQr)
        }

        val tvInfo = TextView(requireContext()).apply {
            text = "📷 Scan with another phone or PC\n📡 This Device: $myIp:8766 | PIN: $myPin"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 14)
        }

        val btnScanCamera = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "📷 Scan PC / Phone QR"
            setOnClickListener {
                try {
                    val lensIntent = Intent(Intent.ACTION_VIEW, Uri.parse("googlelens://v1/camera"))
                    startActivity(lensIntent)
                } catch (_: Exception) {
                    try {
                        val camIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                        startActivity(camIntent)
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Point camera at the other screen QR code!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val etIp = EditText(requireContext()).apply {
            hint = "Target IP (e.g. 192.168.1.100 or :8765)"
            setText(prefs.lastDropIp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val etPin = EditText(requireContext()).apply {
            hint = "Target 6-Digit PIN (optional)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputLayout.addView(tvInfo)
        inputLayout.addView(btnScanCamera)
        inputLayout.addView(etIp)
        inputLayout.addView(etPin)

        val dialogTitle = if (notesToDrop != null) "⚡ Drop ${notesToDrop.size} Note(s)" else "⚡ AANO Drop & Wi-Fi Pairing"
        val positiveBtnText = if (notesToDrop != null) "Send Now" else "Save & Connect"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setMessage("Direct Wi-Fi P2P connection (Zero internet data).")
            .setView(inputLayout)
            .setPositiveButton(positiveBtnText) { _, _ ->
                val ip = etIp.text.toString().trim()
                val pin = etPin.text.toString().trim()
                if (ip.isNotBlank()) {
                    prefs.lastDropIp = ip
                    if (notesToDrop != null) {
                        performBatchDrop(ip, pin, notesToDrop)
                    } else {
                        Toast.makeText(requireContext(), "Connected to $ip", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Please enter or scan target IP", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performBatchDrop(targetIp: String, pin: String, notesToSend: List<Note>) {
        Toast.makeText(requireContext(), "Dropping ${notesToSend.size} note(s) to $targetIp...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val res = AanoDropManager.getInstance(requireContext()).sendDropToDevice(
                targetIp = targetIp,
                targetPort = 8765,
                notes = notesToSend,
                targetPin = pin.ifBlank { null }
            )
            res.onSuccess {
                Toast.makeText(requireContext(), "⚡ Dropped ${notesToSend.size} note(s) successfully!", Toast.LENGTH_LONG).show()
                adapter.clearSelection()
                binding.cardSelectionBar.visibility = View.GONE
                binding.fabAddLocal.show()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Drop failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
