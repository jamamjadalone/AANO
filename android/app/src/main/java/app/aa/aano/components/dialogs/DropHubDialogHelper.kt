// DropHubDialogHelper: Reusable AANO Drop Hub & P2P pairing dialog
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.Prefs
import app.aa.aano.drop.AanoDropManager

object DropHubDialogHelper {

    fun parseScannedQr(contents: String): Triple<String?, Int, String?> {
        val uri = try { Uri.parse(contents) } catch (_: Exception) { null }
        return if (uri != null && uri.scheme == "aano" && uri.host == "drop") {
            val ip = uri.getQueryParameter("ip")
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8765
            val pin = uri.getQueryParameter("pin")
            Triple(ip, port, pin)
        } else if (contents.contains(":")) {
            val parts = contents.split(":")
            val ip = parts[0].trim()
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 8765
            val pin = parts.getOrNull(2)?.trim()
            Triple(ip, port, pin)
        } else {
            Triple(contents.trim(), 8765, null)
        }
    }

    fun showDropHubDialog(
        context: Context,
        scope: CoroutineScope,
        prefs: Prefs,
        notesToDrop: List<Note>? = null,
        onScanQrRequested: () -> Unit,
        onStatusUpdated: () -> Unit,
        onDropSuccess: (() -> Unit)? = null
    ) {
        val dropMgr = AanoDropManager.getInstance(context)
        val netState = dropMgr.getNetworkState()
        val myIp = netState.ip
        val myPin = dropMgr.pin
        val myUri = "aano://drop?ip=$myIp&port=8766&pin=$myPin"

        val dm = context.resources.displayMetrics
        val density = dm.density
        val padH = (16 * density).toInt()
        val padV = (12 * density).toInt()

        val inputLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        var dialogRef: AlertDialog? = null

        if (netState.mode == AanoDropManager.DropNetworkMode.OFFLINE) {
            val offlineCard = app.aa.aano.components.views.DropHubViewHelper.buildOfflineCard(
                context = context,
                density = density,
                onStartHotspot = { dropMgr.openHotspotSettings(context) },
                onRefreshNetwork = {
                    dialogRef?.dismiss()
                    showDropHubDialog(context, scope, prefs, notesToDrop, onScanQrRequested, onStatusUpdated, onDropSuccess)
                }
            )
            inputLayout.addView(offlineCard)
        } else {
            val qrTargetPx = (230 * density).toInt().coerceAtMost((dm.widthPixels * 0.72).toInt())
            val qrBmp = dropMgr.generateQrBitmap(myUri, 512)
            if (qrBmp != null) {
                val qrCard = app.aa.aano.components.views.DropHubViewHelper.buildQrCard(
                    context = context,
                    density = density,
                    qrBmp = qrBmp,
                    targetPx = qrTargetPx
                )
                inputLayout.addView(qrCard)
            }
        }

        val badgeText = if (netState.mode == AanoDropManager.DropNetworkMode.HOTSPOT_ACTIVE) {
            "🔥 Direct Hotspot Active: $myIp:8766\nPIN: $myPin • Connect other device to this Hotspot"
        } else {
            "📡 Wi-Fi Connected: $myIp:8766\nPIN: $myPin • Scan with another phone or PC"
        }
        inputLayout.addView(TextView(context).apply {
            text = badgeText
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * density).toInt())
        })

        inputLayout.addView(MaterialButton(context).apply {
            text = "📷 Scan PC / Phone QR (Built-in Scanner)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (6 * density).toInt()) }
            setOnClickListener {
                dialogRef?.dismiss()
                onScanQrRequested()
            }
        })

        if (netState.mode == AanoDropManager.DropNetworkMode.WIFI_CONNECTED) {
            inputLayout.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "🔥 Router Problem? Switch to Direct Hotspot"
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (6 * density).toInt()) }
                setOnClickListener { dropMgr.openHotspotSettings(context) }
            })
        }

        val btnRadar = MaterialButton(context).apply {
            text = "🔍 Wi-Fi Radar (Auto-Find PC)"
            setOnClickListener {
                text = "Scanning Subnet..."
                isEnabled = false
                scope.launch {
                    val peers = dropMgr.discoverNearbyPeers()
                    isEnabled = true
                    text = "🔍 Wi-Fi Radar (Auto-Find PC)"
                    if (peers.isEmpty()) {
                        Toast.makeText(context, "No nearby AANO devices detected on Wi-Fi", Toast.LENGTH_SHORT).show()
                    } else {
                        val peerItems = peers.map { "${it.name} (${it.ip}:${it.port})" }.toTypedArray()
                        AlertDialog.Builder(context)
                            .setTitle("Select Nearby Device")
                            .setItems(peerItems) { _, which ->
                                val selectedPeer = peers[which]
                                scope.launch {
                                    val pairRes = dropMgr.pairWithPeer(selectedPeer.ip, selectedPeer.port)
                                    pairRes.onSuccess {
                                        prefs.lastDropIp = if (selectedPeer.port == 8765) selectedPeer.ip else "${selectedPeer.ip}:${selectedPeer.port}"
                                        prefs.lastDropPeerName = selectedPeer.name
                                        onStatusUpdated()
                                        Toast.makeText(context, "Paired with ${selectedPeer.name}!", Toast.LENGTH_SHORT).show()
                                        if (notesToDrop != null) {
                                            performBatchDrop(context, scope, prefs, selectedPeer.ip, "", notesToDrop, onDropSuccess)
                                        }
                                    }.onFailure {
                                        prefs.lastDropIp = selectedPeer.ip
                                        onStatusUpdated()
                                        Toast.makeText(context, "Selected ${selectedPeer.ip}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .show()
                    }
                }
            }
        }
        inputLayout.addView(btnRadar)

        val etIp = EditText(context).apply {
            hint = "Target IP (e.g. 192.168.1.100 or :8765)"
            setText(prefs.lastDropIp)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        inputLayout.addView(etIp)

        val etPin = EditText(context).apply {
            hint = "Target 6-Digit PIN (optional)"
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        inputLayout.addView(etPin)

        val dialogTitle = if (notesToDrop != null) "⚡ Drop ${notesToDrop.size} Note(s)" else "⚡ AANO Drop & Wi-Fi Pairing"
        val positiveBtnText = if (notesToDrop != null) "Send Now" else "Save & Connect"

        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(inputLayout)
        }

        dialogRef = MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle)
            .setMessage("Direct Wi-Fi P2P connection (Zero internet data).")
            .setView(scrollView)
            .setPositiveButton(positiveBtnText) { _, _ ->
                val ip = etIp.text.toString().trim()
                val pin = etPin.text.toString().trim()
                if (ip.isNotBlank()) {
                    prefs.lastDropIp = ip
                    prefs.lastDropPin = pin
                    onStatusUpdated()
                    if (notesToDrop != null) {
                        performBatchDrop(context, scope, prefs, ip, pin, notesToDrop, onDropSuccess)
                    } else {
                        Toast.makeText(context, "Connected to $ip", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Please enter or scan target IP", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun performBatchDrop(
        context: Context,
        scope: CoroutineScope,
        prefs: Prefs,
        targetIp: String,
        pin: String,
        notesToSend: List<Note>,
        onSuccess: (() -> Unit)? = null
    ) {
        Toast.makeText(context, "Dropping ${notesToSend.size} note(s) to $targetIp...", Toast.LENGTH_SHORT).show()
        scope.launch {
            val res = AanoDropManager.getInstance(context).sendDropToDevice(
                targetIp = targetIp,
                targetPort = 8765,
                notes = notesToSend,
                targetPin = pin.ifBlank { null }
            )
            res.onSuccess {
                Toast.makeText(context, "⚡ Dropped ${notesToSend.size} note(s) successfully!", Toast.LENGTH_LONG).show()
                onSuccess?.invoke()
            }.onFailure { e ->
                val dropMgr = AanoDropManager.getInstance(context)
                val isLikelyHotspot = targetIp.contains("192.168.43.") || targetIp.contains("192.168.44.") || dropMgr.getNetworkState().mode == AanoDropManager.DropNetworkMode.OFFLINE
                if (isLikelyHotspot) {
                    AlertDialog.Builder(context)
                        .setTitle("⚡ Direct Hotspot Connection")
                        .setMessage("Target device ($targetIp) se connect nahi ho saka.\n\nAgar samne wale phone ka Hotspot on hai, toh pehle apne Wi-Fi Settings mein ja kar us phone ka Hotspot connect karein, phir dobara Drop karein.")
                        .setPositiveButton("📶 Open Wi-Fi Settings") { _, _ -> dropMgr.openWifiSettings(context) }
                        .setNegativeButton("Close", null)
                        .show()
                } else {
                    Toast.makeText(context, "Drop failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
