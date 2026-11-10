// Dedicated Local (On-Device) screen for AANO multimedia notes
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import app.aa.aano.components.adapters.*
import app.aa.aano.components.views.*

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

import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class LocalFragment : Fragment() {

    private var _binding: FragmentLocalBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private lateinit var prefs: Prefs
    private lateinit var adapter: NotesAdapter
    private var loadJob: Job? = null

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (!scanned.isNullOrBlank()) {
            handleScannedDropQr(scanned)
        }
    }

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
                        performBatchDrop(target, prefs.lastDropPin, notes)
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

    private var pendingNotesToDrop: List<Note>? = null

    private fun launchBuiltInQrScanner() {
        val options = ScanOptions().apply {
            setPrompt("⚡ Point camera at other Phone or PC QR Code")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setCameraId(0)
        }
        qrScannerLauncher.launch(options)
    }

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
                    updatePeerStatus()
                    Toast.makeText(requireContext(), "⚡ AANO Drop: Paired with ${peer.name} ($fullIp)!", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(requireContext(), "⚡ AANO Drop: Paired with $fullIp!", Toast.LENGTH_SHORT).show()
                }

                val notes = pendingNotesToDrop
                pendingNotesToDrop = null
                if (notes != null && notes.isNotEmpty()) {
                    performBatchDrop(fullIp, pin ?: prefs.lastDropPin, notes)
                } else {
                    showDropHubDialog(null)
                }
            }
        } else {
            Toast.makeText(requireContext(), "Invalid QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDropHubDialog(notesToDrop: List<Note>? = null) {
        if (notesToDrop != null) {
            pendingNotesToDrop = notesToDrop
        }
        val dropMgr = AanoDropManager.getInstance(requireContext())
        val netState = dropMgr.getNetworkState()
        val myIp = netState.ip
        val myPin = dropMgr.pin
        val myUri = "aano://drop?ip=$myIp&port=8766&pin=$myPin"

        val dm = resources.displayMetrics
        val density = dm.density
        val padH = (16 * density).toInt()
        val padV = (12 * density).toInt()

        val inputLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        var dialogRef: AlertDialog? = null

        if (netState.mode == AanoDropManager.DropNetworkMode.OFFLINE) {
            // Offline Direct Hotspot Card
            val offlineCard = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#1E293B"))
                    cornerRadius = 14 * density
                    setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#F59E0B"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (12 * density).toInt()) }
            }

            val tvOfflineTitle = TextView(requireContext()).apply {
                text = "📡 Wi-Fi Router Not Connected"
                setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            }
            offlineCard.addView(tvOfflineTitle)

            val tvOfflineDesc = TextView(requireContext()).apply {
                text = "Direct Hotspot Mode (Offline Transfer):\n1. Is phone ka Hotspot on karein.\n2. Doosra phone is Hotspot se connect karein.\n3. Notes direct bina internet transfer karein!"
                setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
            }
            offlineCard.addView(tvOfflineDesc)

            val btnStartHotspot = com.google.android.material.button.MaterialButton(requireContext()).apply {
                text = "🔥 Turn On Hotspot (Offline Mode)"
                setBackgroundColor(android.graphics.Color.parseColor("#D97706"))
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (8 * density).toInt()) }
                setOnClickListener {
                    dropMgr.openHotspotSettings(requireContext())
                }
            }
            offlineCard.addView(btnStartHotspot)

            val btnRefreshNet = com.google.android.material.button.MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "🔄 Refresh Network Status"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    dialogRef?.dismiss()
                    showDropHubDialog(notesToDrop)
                }
            }
            offlineCard.addView(btnRefreshNet)
            inputLayout.addView(offlineCard)
        } else {
            // High-Contrast Direct QR Code
            val qrTargetPx = (230 * density).toInt().coerceAtMost((dm.widthPixels * 0.72).toInt())
            val qrBmp = dropMgr.generateQrBitmap(myUri, 512)

            if (qrBmp != null) {
                val qrCard = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.WHITE)
                        cornerRadius = 16 * density
                        setStroke((2 * density).toInt(), android.graphics.Color.parseColor("#E2E8F0"))
                    }
                    val padBox = (12 * density).toInt()
                    setPadding(padBox, padBox, padBox, padBox)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, (10 * density).toInt()) }
                }

                val ivQr = ImageView(requireContext()).apply {
                    setImageBitmap(qrBmp)
                    layoutParams = LinearLayout.LayoutParams(qrTargetPx, qrTargetPx)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                qrCard.addView(ivQr)
                inputLayout.addView(qrCard)
            }
        }

        val badgeText = if (netState.mode == AanoDropManager.DropNetworkMode.HOTSPOT_ACTIVE) {
            "🔥 Direct Hotspot Active: $myIp:8766\nPIN: $myPin • Connect other device to this Hotspot"
        } else {
            "📡 Wi-Fi Connected: $myIp:8766\nPIN: $myPin • Scan with another phone or PC"
        }
        val tvInfo = TextView(requireContext()).apply {
            text = badgeText
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val btnScanCamera = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "📷 Scan PC / Phone QR (Built-in Scanner)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (6 * density).toInt()) }
            setOnClickListener {
                dialogRef?.dismiss()
                launchBuiltInQrScanner()
            }
        }
        inputLayout.addView(btnScanCamera)

        if (netState.mode == AanoDropManager.DropNetworkMode.WIFI_CONNECTED) {
            val btnHotspotOption = com.google.android.material.button.MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "🔥 Router Problem? Switch to Direct Hotspot"
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (6 * density).toInt()) }
                setOnClickListener {
                    dropMgr.openHotspotSettings(requireContext())
                }
            }
            inputLayout.addView(btnHotspotOption)
        }

        val btnRadar = com.google.android.material.button.MaterialButton(requireContext()).apply {
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
                                        updatePeerStatus()
                                        Toast.makeText(requireContext(), "Paired with ${selectedPeer.name}!", Toast.LENGTH_SHORT).show()
                                        if (notesToDrop != null) {
                                            performBatchDrop(selectedPeer.ip, "", notesToDrop)
                                        }
                                    }.onFailure {
                                        prefs.lastDropIp = selectedPeer.ip
                                        updatePeerStatus()
                                        Toast.makeText(requireContext(), "Selected ${selectedPeer.ip}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .show()
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
        inputLayout.addView(btnRadar)
        inputLayout.addView(btnScanCamera)
        inputLayout.addView(etIp)
        inputLayout.addView(etPin)

        val dialogTitle = if (notesToDrop != null) "⚡ Drop ${notesToDrop.size} Note(s)" else "⚡ AANO Drop & Wi-Fi Pairing"
        val positiveBtnText = if (notesToDrop != null) "Send Now" else "Save & Connect"

        val scrollView = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(inputLayout)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setMessage("Direct Wi-Fi P2P connection (Zero internet data).")
            .setView(scrollView)
            .setPositiveButton(positiveBtnText) { _, _ ->
                val ip = etIp.text.toString().trim()
                val pin = etPin.text.toString().trim()
                if (ip.isNotBlank()) {
                    prefs.lastDropIp = ip
                    prefs.lastDropPin = pin
                    updatePeerStatus()
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
                updatePeerStatus()
                adapter.clearSelection()
                binding.cardSelectionBar.visibility = View.GONE
                binding.fabAddLocal.show()
            }.onFailure { e ->
                val dropMgr = AanoDropManager.getInstance(requireContext())
                val isLikelyHotspot = targetIp.contains("192.168.43.") || targetIp.contains("192.168.44.") || dropMgr.getNetworkState().mode == AanoDropManager.DropNetworkMode.OFFLINE
                if (isLikelyHotspot) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("⚡ Direct Hotspot Connection")
                        .setMessage("Target device ($targetIp) se connect nahi ho saka.\n\nAgar samne wale phone ka Hotspot on hai, toh pehle apne Wi-Fi Settings mein ja kar us phone ka Hotspot connect karein, phir dobara Drop karein.")
                        .setPositiveButton("📶 Open Wi-Fi Settings") { _, _ -> dropMgr.openWifiSettings(requireContext()) }
                        .setNegativeButton("Close", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "Drop failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
