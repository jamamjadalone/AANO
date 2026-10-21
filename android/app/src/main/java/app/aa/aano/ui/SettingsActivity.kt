// Settings screen for AANO: Cloudflare Edge Sync (Gmail OTP sign-up/sign-in),
// auto-sync toggle, and local JSON backup export/import.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import app.aa.aano.R
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.ActivitySettingsBinding
import app.aa.aano.net.CloudSyncManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var repository: NoteRepository
    private lateinit var tasksRepository: TaskRepository
    private lateinit var cloudSyncManager: CloudSyncManager

    private var lastOtpRequestTime = 0L
    private var failedOtpAttempts = 0

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val OTP_COOLDOWN_MS = 60_000L
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importBackup(uri)
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

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSignUp.setOnClickListener { showRegisterDialog() }
        binding.btnSignIn.setOnClickListener { showLoginDialog() }
        binding.btnSyncNow.setOnClickListener { syncNow() }
        binding.btnDisconnect.setOnClickListener {
            cloudSyncManager.logout()
            Toast.makeText(this, R.string.disconnected, Toast.LENGTH_SHORT).show()
            updateCloudUi()
        }

        binding.switchAutoSync.isChecked = prefs.autoSync
        binding.switchAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoSync = checked
        }

        binding.btnExport.setOnClickListener { exportBackup() }
        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }

        updateCloudUi()
    }

    private fun updateCloudUi() {
        val connected = cloudSyncManager.isLoggedIn
        binding.googleStatus.text = if (connected) {
            getString(R.string.connected_as, cloudSyncManager.currentEmail ?: "")
        } else {
            getString(R.string.not_connected)
        }
        binding.btnSignUp.visibility = if (connected) View.GONE else View.VISIBLE
        binding.btnSignIn.visibility = if (connected) View.GONE else View.VISIBLE
        binding.btnSyncNow.visibility = if (connected) View.VISIBLE else View.GONE
        binding.btnDisconnect.visibility = if (connected) View.VISIBLE else View.GONE
    }

    private fun showRegisterDialog() {
        val inputEmail = EditText(this).apply {
            hint = "yourname@gmail.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputEmail)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sign_up_google)
            .setMessage("Enter your personal @gmail.com address. We will send a 6-digit security code to your inbox.")
            .setView(container)
            .setPositiveButton("Send OTP") { _, _ ->
                val email = inputEmail.text.toString().trim()
                if (email.isBlank() || !email.endsWith("@gmail.com")) {
                    Toast.makeText(this, "Valid @gmail.com address required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                requestOtpAndPromptVerification(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestOtpAndPromptVerification(email: String) {
        val elapsed = System.currentTimeMillis() - lastOtpRequestTime
        if (elapsed < OTP_COOLDOWN_MS) {
            val remainingSec = ((OTP_COOLDOWN_MS - elapsed) / 1000).coerceAtLeast(1)
            Toast.makeText(this, "Please wait ${remainingSec}s before requesting a new OTP.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.syncProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = cloudSyncManager.requestOtp(email)
            binding.syncProgress.visibility = View.GONE
            result.onSuccess {
                lastOtpRequestTime = System.currentTimeMillis()
                failedOtpAttempts = 0
                Toast.makeText(this@SettingsActivity, "OTP sent to $email. Check your inbox!", Toast.LENGTH_LONG).show()
                showVerifyDialog(email)
            }.onFailure { e ->
                val errorMsg = e.message ?: "Failed to send OTP"
                if (errorMsg.contains("already exists", ignoreCase = true)) {
                    MaterialAlertDialogBuilder(this@SettingsActivity)
                        .setTitle("Account Already Exists")
                        .setMessage("An account with $email already exists. Would you like to Sign In or Reset your Password?")
                        .setPositiveButton("Sign In") { _, _ ->
                            showLoginDialog()
                        }
                        .setNeutralButton("Reset Password") { _, _ ->
                            showForgotPasswordDialog(email)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    Toast.makeText(this@SettingsActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showVerifyDialog(email: String) {
        val inputOtp = EditText(this).apply {
            hint = "6-digit OTP code"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val inputPass = EditText(this).apply {
            hint = "Set Password (min 6 chars)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputOtp)
            addView(inputPass)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Verify & Register")
            .setMessage("Enter the 6-digit code sent to $email and create a password for your account.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Complete Registration") { _, _ ->
                val otp = inputOtp.text.toString().trim()
                val pass = inputPass.text.toString().trim()
                if (otp.length != 6) {
                    Toast.makeText(this, "6-digit OTP required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pass.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                verifyRegistration(email, otp, pass)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyRegistration(email: String, otp: String, pass: String) {
        binding.syncProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val res = cloudSyncManager.verifyAndRegister(email, otp, pass)
            binding.syncProgress.visibility = View.GONE
            res.onSuccess {
                failedOtpAttempts = 0
                Toast.makeText(this@SettingsActivity, R.string.google_connected, Toast.LENGTH_SHORT).show()
                updateCloudUi()
                syncNow()
            }.onFailure { e ->
                failedOtpAttempts++
                if (failedOtpAttempts >= MAX_FAILED_ATTEMPTS) {
                    failedOtpAttempts = 0
                    MaterialAlertDialogBuilder(this@SettingsActivity)
                        .setTitle("Too Many Failed Attempts")
                        .setMessage("You entered an incorrect OTP 5 times. For your security, this code is now locked. Please wait 60 seconds and request a fresh code.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val remaining = MAX_FAILED_ATTEMPTS - failedOtpAttempts
                    Toast.makeText(
                        this@SettingsActivity,
                        "Verification failed (${e.message}). $remaining attempts remaining.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showLoginDialog() {
        val inputEmail = EditText(this).apply {
            hint = "yourname@gmail.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        val inputPass = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputEmail)
            addView(inputPass)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sign_in_google)
            .setView(container)
            .setPositiveButton("Sign In") { _, _ ->
                val email = inputEmail.text.toString().trim()
                val pass = inputPass.text.toString().trim()
                if (email.isBlank() || pass.isBlank()) {
                    Toast.makeText(this, "Email and Password required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                performLogin(email, pass)
            }
            .setNeutralButton("Forgot Password?") { _, _ ->
                val email = inputEmail.text.toString().trim()
                showForgotPasswordDialog(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showForgotPasswordDialog(defaultEmail: String = "") {
        val inputEmail = EditText(this).apply {
            hint = "yourname@gmail.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
            if (defaultEmail.isNotBlank()) {
                setText(defaultEmail)
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputEmail)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setMessage("Enter your registered @gmail.com address. We will send a 6-digit reset code to your inbox.")
            .setView(container)
            .setPositiveButton("Send Reset Code") { _, _ ->
                val email = inputEmail.text.toString().trim()
                if (email.isBlank() || !email.endsWith("@gmail.com")) {
                    Toast.makeText(this, "Valid @gmail.com address required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                requestOtpAndPromptReset(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestOtpAndPromptReset(email: String) {
        val elapsed = System.currentTimeMillis() - lastOtpRequestTime
        if (elapsed < OTP_COOLDOWN_MS) {
            val remainingSec = ((OTP_COOLDOWN_MS - elapsed) / 1000).coerceAtLeast(1)
            Toast.makeText(this, "Please wait ${remainingSec}s before requesting a new OTP.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.syncProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = cloudSyncManager.requestOtp(email, purpose = "reset")
            binding.syncProgress.visibility = View.GONE
            result.onSuccess {
                lastOtpRequestTime = System.currentTimeMillis()
                failedOtpAttempts = 0
                Toast.makeText(this@SettingsActivity, "Reset code sent to $email. Check your inbox!", Toast.LENGTH_LONG).show()
                showResetPasswordDialog(email)
            }.onFailure { e ->
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showResetPasswordDialog(email: String) {
        val inputOtp = EditText(this).apply {
            hint = "6-digit OTP code"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val inputNewPass = EditText(this).apply {
            hint = "New Password (min 6 chars)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputOtp)
            addView(inputNewPass)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Set New Password")
            .setMessage("Enter the 6-digit code sent to $email and enter your new password.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Update Password") { _, _ ->
                val otp = inputOtp.text.toString().trim()
                val pass = inputNewPass.text.toString().trim()
                if (otp.length != 6) {
                    Toast.makeText(this, "6-digit OTP required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pass.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                performPasswordReset(email, otp, pass)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performPasswordReset(email: String, otp: String, pass: String) {
        binding.syncProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val res = cloudSyncManager.resetPassword(email, otp, pass)
            binding.syncProgress.visibility = View.GONE
            res.onSuccess {
                failedOtpAttempts = 0
                Toast.makeText(this@SettingsActivity, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                updateCloudUi()
                syncNow()
            }.onFailure { e ->
                failedOtpAttempts++
                if (failedOtpAttempts >= MAX_FAILED_ATTEMPTS) {
                    failedOtpAttempts = 0
                    MaterialAlertDialogBuilder(this@SettingsActivity)
                        .setTitle("Too Many Failed Attempts")
                        .setMessage("You entered an incorrect code 5 times. Please wait 60 seconds and request a fresh reset code.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val remaining = MAX_FAILED_ATTEMPTS - failedOtpAttempts
                    Toast.makeText(
                        this@SettingsActivity,
                        "Reset failed (${e.message}). $remaining attempts remaining.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun performLogin(email: String, pass: String) {
        binding.syncProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val res = cloudSyncManager.login(email, pass)
            binding.syncProgress.visibility = View.GONE
            res.onSuccess {
                Toast.makeText(this@SettingsActivity, R.string.google_connected, Toast.LENGTH_SHORT).show()
                updateCloudUi()
                syncNow()
            }.onFailure { e ->
                Toast.makeText(this@SettingsActivity, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun syncNow() {
        binding.syncProgress.visibility = View.VISIBLE
        binding.syncResult.visibility = View.GONE
        lifecycleScope.launch {
            val res = cloudSyncManager.syncNow()
            binding.syncProgress.visibility = View.GONE
            binding.syncResult.visibility = View.VISIBLE
            res.onSuccess { count ->
                binding.syncResult.text = "Sync complete. Synced $count items."
            }.onFailure { e ->
                binding.syncResult.text = getString(R.string.sync_failed, e.message ?: "error")
            }
        }
    }

    private fun exportBackup() {
        lifecycleScope.launch {
            try {
                val bundle = JSONObject()
                    .put("version", 1)
                    .put("notes", notesJson())
                    .put("tasks", tasksJson())
                saveToDownloads("aano-backup.json", bundle.toString())
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

    private suspend fun notesJson(): org.json.JSONArray = org.json.JSONArray(repository.exportJson())

    private suspend fun tasksJson(): org.json.JSONArray = org.json.JSONArray(tasksRepository.exportJson())

    private fun saveToDownloads(fileName: String, content: String) {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AANO")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val uri: Uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create file")
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Could not open output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun importBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8)
                    ?: throw IllegalStateException("Could not read file")
                val bundle = JSONObject(text)
                if (bundle.has("notes") && bundle.has("tasks")) {
                    repository.importJson(bundle.getString("notes"))
                    tasksRepository.importJson(bundle.getString("tasks"))
                } else {
                    // Legacy single-array backup (notes only).
                    repository.importJson(text)
                }
                binding.backupResult.text = getString(R.string.backup_imported)
                binding.backupResult.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.backupResult.text =
                    getString(R.string.backup_import_failed, e.message ?: "error")
                binding.backupResult.visibility = View.VISIBLE
            }
        }
    }
}