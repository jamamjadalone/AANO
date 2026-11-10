// EditorDropHelper: Coordinates QR scanning and P2P Wi-Fi Drop for EditorActivity
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.content.Context
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.aa.aano.components.dialogs.DropHubDialogHelper
import app.aa.aano.data.Note
import app.aa.aano.data.Prefs
import app.aa.aano.drop.AanoDropManager

object EditorDropHelper {

    fun launchQrScanner(launcher: ActivityResultLauncher<ScanOptions>) {
        val options = ScanOptions().apply {
            setPrompt("⚡ Point camera at other Phone or PC QR Code")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setCameraId(0)
        }
        launcher.launch(options)
    }

    fun handleScannedQr(
        context: Context,
        scope: CoroutineScope,
        prefs: Prefs,
        contents: String,
        noteToSend: Note
    ) {
        val (ip, port, pin) = DropHubDialogHelper.parseScannedQr(contents)
        if (!ip.isNullOrBlank()) {
            val fullIp = if (port == 8765) ip else "$ip:$port"
            prefs.lastDropIp = fullIp
            if (!pin.isNullOrBlank()) prefs.lastDropPin = pin
            scope.launch {
                val dropMgr = AanoDropManager.getInstance(context)
                val pairRes = dropMgr.pairWithPeer(ip, port, pin)
                pairRes.onSuccess { peer ->
                    prefs.lastDropPeerName = peer.name
                    Toast.makeText(context, "⚡ Paired with ${peer.name} ($fullIp)!", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "⚡ Paired with $fullIp!", Toast.LENGTH_SHORT).show()
                }

                DropHubDialogHelper.performBatchDrop(
                    context = context,
                    scope = scope,
                    prefs = prefs,
                    targetIp = fullIp,
                    pin = pin ?: prefs.lastDropPin,
                    notesToSend = listOf(noteToSend)
                )
            }
        } else {
            Toast.makeText(context, "Invalid QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    fun showDropDialog(
        context: Context,
        scope: CoroutineScope,
        prefs: Prefs,
        noteSupplier: () -> Note,
        onScanQrRequested: () -> Unit
    ) {
        DropHubDialogHelper.showDropHubDialog(
            context = context,
            scope = scope,
            prefs = prefs,
            notesToDrop = listOf(noteSupplier()),
            onScanQrRequested = onScanQrRequested,
            onStatusUpdated = {},
            onDropSuccess = {
                Toast.makeText(context, "⚡ Dropped successfully!", Toast.LENGTH_LONG).show()
            }
        )
    }
}
