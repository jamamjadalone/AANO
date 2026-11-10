// SettingsAuthHelper: Handles OTP requests, verification, password reset, and sign-in operations.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.net

import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SettingsAuthHelper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val cloudSyncManager: CloudSyncManager,
    private val driveSyncManager: DriveSyncManager,
    private val onProgress: (Boolean) -> Unit,
    private val onSuccessAuth: () -> Unit
) {
    fun performSync(
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        onProgress(true)
        scope.launch {
            if (account != null) {
                val ok = driveSyncManager.sync()
                onProgress(false)
                if (ok) {
                    onSuccess("Google Drive sync complete.")
                    Toast.makeText(context, R.string.sync_success, Toast.LENGTH_SHORT).show()
                } else {
                    onSuccess("Google Drive sync finished.")
                }
            } else {
                val res = cloudSyncManager.syncNow()
                onProgress(false)
                res.onSuccess { count ->
                    onSuccess("Sync complete. Synced $count items.")
                }.onFailure { e ->
                    onError(e.message ?: "error")
                }
            }
        }
    }
    private var lastOtpRequestTime = 0L
    private var failedOtpAttempts = 0

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val OTP_COOLDOWN_MS = 60_000L
    }

    fun requestOtp(
        email: String,
        purpose: String = "register",
        onSuccess: () -> Unit,
        onAccountExists: (() -> Unit)? = null
    ) {
        val elapsed = System.currentTimeMillis() - lastOtpRequestTime
        if (elapsed < OTP_COOLDOWN_MS) {
            val remainingSec = ((OTP_COOLDOWN_MS - elapsed) / 1000).coerceAtLeast(1)
            Toast.makeText(context, "Please wait ${remainingSec}s before requesting a new OTP.", Toast.LENGTH_SHORT).show()
            return
        }

        onProgress(true)
        scope.launch {
            val result = cloudSyncManager.requestOtp(email, purpose = purpose)
            onProgress(false)
            result.onSuccess {
                lastOtpRequestTime = System.currentTimeMillis()
                failedOtpAttempts = 0
                Toast.makeText(context, "Code sent to $email. Check your inbox!", Toast.LENGTH_LONG).show()
                onSuccess()
            }.onFailure { e ->
                val errorMsg = e.message ?: "Failed to send OTP"
                if (errorMsg.contains("already exists", ignoreCase = true) && onAccountExists != null) {
                    onAccountExists()
                } else {
                    Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun verifyAndRegister(email: String, otp: String, pass: String) {
        onProgress(true)
        scope.launch {
            val res = cloudSyncManager.verifyAndRegister(email, otp, pass)
            onProgress(false)
            res.onSuccess {
                failedOtpAttempts = 0
                Toast.makeText(context, R.string.google_connected, Toast.LENGTH_SHORT).show()
                onSuccessAuth()
            }.onFailure { e ->
                handleOtpFailure("Verification failed", e.message)
            }
        }
    }

    fun resetPassword(email: String, otp: String, pass: String) {
        onProgress(true)
        scope.launch {
            val res = cloudSyncManager.resetPassword(email, otp, pass)
            onProgress(false)
            res.onSuccess {
                failedOtpAttempts = 0
                Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                onSuccessAuth()
            }.onFailure { e ->
                handleOtpFailure("Reset failed", e.message)
            }
        }
    }

    fun login(email: String, pass: String) {
        onProgress(true)
        scope.launch {
            val res = cloudSyncManager.login(email, pass)
            onProgress(false)
            res.onSuccess {
                Toast.makeText(context, R.string.google_connected, Toast.LENGTH_SHORT).show()
                onSuccessAuth()
            }.onFailure { e ->
                Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleOtpFailure(actionName: String, errMsg: String?) {
        failedOtpAttempts++
        if (failedOtpAttempts >= MAX_FAILED_ATTEMPTS) {
            failedOtpAttempts = 0
            MaterialAlertDialogBuilder(context)
                .setTitle("Too Many Failed Attempts")
                .setMessage("You entered an incorrect code 5 times. For your security, this code is now locked. Please wait 60 seconds and request a fresh code.")
                .setPositiveButton("OK", null)
                .show()
        } else {
            val remaining = MAX_FAILED_ATTEMPTS - failedOtpAttempts
            Toast.makeText(
                context,
                "$actionName (${errMsg ?: "error"}). $remaining attempts remaining.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
