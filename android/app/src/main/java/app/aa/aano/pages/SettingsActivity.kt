// Settings screen for AANO: Cloudflare Edge Sync (Gmail OTP sign-up/sign-in),
// auto-sync toggle, and local JSON backup export/import.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.pages

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.aa.aano.R
import app.aa.aano.components.dialogs.SettingsAuthCoordinator
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.SettingsBackupDelegate
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.ActivitySettingsBinding
import app.aa.aano.net.CloudSyncManager
import app.aa.aano.net.DriveSyncManager
import app.aa.aano.net.SettingsAuthHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var repository: NoteRepository
    private lateinit var tasksRepository: TaskRepository
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var driveSyncManager: DriveSyncManager
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var authHelper: SettingsAuthHelper
    private lateinit var authCoordinator: SettingsAuthCoordinator

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }

    private fun handleGoogleSignInResult(data: android.content.Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
                updateCloudUi()
                lifecycleScope.launch {
                    try {
                        driveSyncManager.sync()
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsActivity", "Initial Google Drive sync notice: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            val msg = if (e is ApiException) {
                when (e.statusCode) {
                    7 -> "Network error (Code 7): Check internet or disable Private DNS/VPN on phone."
                    10 -> "Setup error (Code 10): SHA-1 fingerprint needs to be registered in Google Cloud Console."
                    12501 -> "Sign-in cancelled."
                    else -> "Google error (${e.statusCode}): ${e.localizedMessage}"
                }
            } else {
                e.localizedMessage ?: "Sign-in cancelled"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importBackup(uri)
    }

    private val restoreVaultLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) restoreLocalVault(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = Prefs(this)
        repository = NoteRepository(this)
        tasksRepository = TaskRepository(this)
        cloudSyncManager = CloudSyncManager(this, repository, tasksRepository)
        driveSyncManager = DriveSyncManager(this, repository, tasksRepository)
        googleSignInClient = GoogleSignIn.getClient(this, driveSyncManager.signInOptions())

        authHelper = SettingsAuthHelper(
            context = this,
            scope = lifecycleScope,
            cloudSyncManager = cloudSyncManager,
            driveSyncManager = driveSyncManager,
            onProgress = { show -> binding.syncProgress.visibility = if (show) View.VISIBLE else View.GONE },
            onSuccessAuth = {
                updateCloudUi()
                syncNow()
            }
        )
        authCoordinator = SettingsAuthCoordinator(this, authHelper)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSignUp.setOnClickListener { googleSignInLauncher.launch(googleSignInClient.signInIntent) }
        binding.btnSignIn.setOnClickListener { googleSignInLauncher.launch(googleSignInClient.signInIntent) }
        binding.btnSyncNow.setOnClickListener { syncNow() }
        binding.btnDisconnect.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                cloudSyncManager.logout()
                Toast.makeText(this, R.string.disconnected, Toast.LENGTH_SHORT).show()
                updateCloudUi()
            }
        }

        setupThemeAndSyncSwitches()
        setupBackupButtons()
        updateCloudUi()
    }

    private fun setupThemeAndSyncSwitches() {
        when (prefs.appTheme) {
            "light" -> binding.toggleThemeGroup.check(R.id.btnThemeLight)
            "dark" -> binding.toggleThemeGroup.check(R.id.btnThemeDark)
            else -> binding.toggleThemeGroup.check(R.id.btnThemeSystem)
        }

        binding.toggleThemeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newTheme = when (checkedId) {
                    R.id.btnThemeLight -> "light"
                    R.id.btnThemeDark -> "dark"
                    else -> "system"
                }
                if (prefs.appTheme != newTheme) {
                    prefs.appTheme = newTheme
                    app.aa.aano.AanoApp.applyTheme(newTheme)
                }
            }
        }

        binding.switchAutoSync.isChecked = prefs.autoSync
        binding.switchAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoSync = checked
        }
    }

    private fun setupBackupButtons() {
        binding.btnExport.setOnClickListener { exportBackup() }
        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }
        binding.btnExportVault.setOnClickListener { exportLocalVault() }
        binding.btnRestoreVault.setOnClickListener {
            restoreVaultLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
        }
    }

    private fun updateCloudUi() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val hasScope = account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
        val connected = account != null && hasScope

        if (connected) {
            binding.googleStatus.text = getString(R.string.connected_as, account?.email ?: "")
            binding.btnSignUp.visibility = View.GONE
            binding.btnSignIn.visibility = View.GONE
            binding.btnSyncNow.visibility = View.VISIBLE
            binding.btnDisconnect.visibility = View.VISIBLE
        } else if (cloudSyncManager.isLoggedIn) {
            binding.googleStatus.text = getString(R.string.connected_as, cloudSyncManager.currentEmail ?: "")
            binding.btnSignUp.visibility = View.GONE
            binding.btnSignIn.visibility = View.GONE
            binding.btnSyncNow.visibility = View.VISIBLE
            binding.btnDisconnect.visibility = View.VISIBLE
        } else {
            binding.googleStatus.text = getString(R.string.not_connected)
            binding.btnSignUp.visibility = View.VISIBLE
            binding.btnSignIn.visibility = View.GONE
            binding.btnSyncNow.visibility = View.GONE
            binding.btnDisconnect.visibility = View.GONE
        }
    }

    private fun syncNow() {
        binding.syncResult.visibility = View.GONE
        val account = GoogleSignIn.getLastSignedInAccount(this)
        authHelper.performSync(
            account = account,
            onSuccess = { msg ->
                binding.syncResult.visibility = View.VISIBLE
                binding.syncResult.text = msg
            },
            onError = { errMsg ->
                binding.syncResult.visibility = View.VISIBLE
                binding.syncResult.text = getString(R.string.sync_failed, errMsg)
            }
        )
    }

    private fun exportBackup() {
        lifecycleScope.launch {
            try {
                val json = SettingsBackupDelegate.createBackupJson(repository, tasksRepository)
                SettingsBackupDelegate.saveToDownloads(this@SettingsActivity, "aano-backup.json", json)
                binding.backupResult.text = getString(R.string.backup_exported)
                binding.backupResult.visibility = View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.backup_export_failed, e.message ?: "error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                SettingsBackupDelegate.restoreBackupJson(this@SettingsActivity, uri, repository, tasksRepository)
                binding.backupResult.text = getString(R.string.backup_imported)
                binding.backupResult.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.backupResult.text =
                    getString(R.string.backup_import_failed, e.message ?: "error")
                binding.backupResult.visibility = View.VISIBLE
            }
        }
    }

    private fun exportLocalVault() {
        lifecycleScope.launch {
            try {
                val summary = SettingsBackupDelegate.exportVault(this@SettingsActivity, repository)
                binding.backupResult.text = "Media Vault exported (${summary.notesCount} notes, ${summary.imagesCount} photos, ${summary.fileSizeFormatted})"
                binding.backupResult.visibility = View.VISIBLE
                Toast.makeText(this@SettingsActivity, "Vault saved to Downloads/AANO/${summary.fileName}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.backupResult.text = "Vault export failed: ${e.message}"
                binding.backupResult.visibility = View.VISIBLE
            }
        }
    }

    private fun restoreLocalVault(uri: Uri) {
        lifecycleScope.launch {
            try {
                val summary = SettingsBackupDelegate.restoreVault(this@SettingsActivity, uri, repository)
                binding.backupResult.text = "Restored ${summary.notesRestored} local notes & ${summary.imagesRestored} photos"
                binding.backupResult.visibility = View.VISIBLE
                Toast.makeText(this@SettingsActivity, "Media Vault restored successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.backupResult.text = "Vault restore failed: ${e.message}"
                binding.backupResult.visibility = View.VISIBLE
            }
        }
    }
}
