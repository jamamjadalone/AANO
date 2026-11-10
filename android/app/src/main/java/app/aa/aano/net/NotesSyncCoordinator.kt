// NotesSyncCoordinator: Manages auto-sync heartbeat & sync dialogs for Notes tab
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.net

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.data.Prefs
import app.aa.aano.pages.SettingsActivity

class NotesSyncCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val cloudSyncManager: CloudSyncManager,
    private val prefs: Prefs,
    private val onRefreshNeeded: () -> Unit
) {

    private var autoSyncJob: Job? = null

    fun startForegroundAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = scope.launch {
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) runSilentSyncCheck()
            while (isActive) {
                delay(20_000L)
                if (prefs.autoSync && cloudSyncManager.isLoggedIn) runSilentSyncCheck()
            }
        }
    }

    fun stopForegroundAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    private suspend fun runSilentSyncCheck() {
        if (cloudSyncManager.syncNow().isSuccess) onRefreshNeeded()
    }

    fun launchSync(silent: Boolean = false) {
        if (!silent) Toast.makeText(context, R.string.syncing, Toast.LENGTH_SHORT).show()
        scope.launch {
            cloudSyncManager.syncNow().onSuccess { count ->
                prefs.syncPending = false
                onRefreshNeeded()
                if (!silent) Toast.makeText(context, "AANO Cloud Sync complete ($count items)", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                prefs.syncPending = true
                if (!silent) Toast.makeText(context, "AANO Cloud Sync failed: ${e.message ?: "network error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startSyncFlow() {
        if (cloudSyncManager.isLoggedIn) {
            launchSync()
        } else {
            AlertDialog.Builder(context)
                .setTitle("AANO Cloud Sync")
                .setMessage("Connect your Gmail account to sync your notes seamlessly across your devices.")
                .setPositiveButton("Connect") { _, _ -> context.startActivity(Intent(context, SettingsActivity::class.java)) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
