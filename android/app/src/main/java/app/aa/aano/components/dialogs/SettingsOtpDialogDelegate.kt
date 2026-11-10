// SettingsOtpDialogDelegate: Handles Gmail OTP dialog flows (Sign up, Sign in, Password reset).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.R

object SettingsOtpDialogDelegate {

    fun showAccountExistsDialog(
        context: Context,
        email: String,
        onSignIn: () -> Unit,
        onResetPassword: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Account Already Exists")
            .setMessage("An account with $email already exists. Would you like to Sign In or Reset your Password?")
            .setPositiveButton("Sign In") { _, _ -> onSignIn() }
            .setNeutralButton("Reset Password") { _, _ -> onResetPassword() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showRegisterDialog(
        context: Context,
        onSendOtp: (email: String) -> Unit
    ) {
        val inputEmail = EditText(context).apply {
            hint = "yourname@gmail.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputEmail)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.sign_up_google)
            .setMessage("Enter your personal @gmail.com address. We will send a 6-digit security code to your inbox.")
            .setView(container)
            .setPositiveButton("Send OTP") { _, _ ->
                val email = inputEmail.text.toString().trim()
                if (email.isBlank() || !email.endsWith("@gmail.com")) {
                    Toast.makeText(context, "Valid @gmail.com address required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onSendOtp(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showVerifyDialog(
        context: Context,
        email: String,
        onVerify: (otp: String, pass: String) -> Unit
    ) {
        val inputOtp = EditText(context).apply {
            hint = "6-digit OTP code"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val inputPass = EditText(context).apply {
            hint = "Set Password (min 6 chars)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputOtp)
            addView(inputPass)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Verify & Register")
            .setMessage("Enter the 6-digit code sent to  and create a password for your account.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Complete Registration") { _, _ ->
                val otp = inputOtp.text.toString().trim()
                val pass = inputPass.text.toString().trim()
                if (otp.length != 6) {
                    Toast.makeText(context, "6-digit OTP required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pass.length < 6) {
                    Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onVerify(otp, pass)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showLoginDialog(
        context: Context,
        onLogin: (email: String, pass: String) -> Unit,
        onForgotPassword: (email: String) -> Unit
    ) {
        val inputEmail = EditText(context).apply {
            hint = "yourname@gmail.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        val inputPass = EditText(context).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputEmail)
            addView(inputPass)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.sign_in_google)
            .setView(container)
            .setPositiveButton("Sign In") { _, _ ->
                val email = inputEmail.text.toString().trim()
                val pass = inputPass.text.toString().trim()
                if (email.isBlank() || pass.isBlank()) {
                    Toast.makeText(context, "Email and Password required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onLogin(email, pass)
            }
            .setNeutralButton("Forgot Password?") { _, _ ->
                val email = inputEmail.text.toString().trim()
                onForgotPassword(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showForgotPasswordDialog(
        context: Context,
        defaultEmail: String = "",
        onSendResetCode: (email: String) -> Unit
    ) {
        val inputEmail = EditText(context).apply {
            hint = "yourname@gmail.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
            if (defaultEmail.isNotBlank()) setText(defaultEmail)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputEmail)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Reset Password")
            .setMessage("Enter your registered @gmail.com address. We will send a 6-digit reset code to your inbox.")
            .setView(container)
            .setPositiveButton("Send Reset Code") { _, _ ->
                val email = inputEmail.text.toString().trim()
                if (email.isBlank() || !email.endsWith("@gmail.com")) {
                    Toast.makeText(context, "Valid @gmail.com address required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onSendResetCode(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showResetPasswordDialog(
        context: Context,
        email: String,
        onUpdatePassword: (otp: String, newPass: String) -> Unit
    ) {
        val inputOtp = EditText(context).apply {
            hint = "6-digit OTP code"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val inputNewPass = EditText(context).apply {
            hint = "New Password (min 6 chars)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 10)
            addView(inputOtp)
            addView(inputNewPass)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Set New Password")
            .setMessage("Enter the 6-digit code sent to  and enter your new password.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Update Password") { _, _ ->
                val otp = inputOtp.text.toString().trim()
                val pass = inputNewPass.text.toString().trim()
                if (otp.length != 6) {
                    Toast.makeText(context, "6-digit OTP required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pass.length < 6) {
                    Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onUpdatePassword(otp, pass)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
