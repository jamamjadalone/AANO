// SettingsAuthCoordinator: Coordinates Cloudflare OTP dialog flows (Rule 9 standalone auth preservation).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.app.Activity
import app.aa.aano.net.SettingsAuthHelper

class SettingsAuthCoordinator(
    private val activity: Activity,
    private val authHelper: SettingsAuthHelper
) {

    fun showRegisterDialog() {
        SettingsOtpDialogDelegate.showRegisterDialog(activity) { email ->
            authHelper.requestOtp(
                email = email,
                purpose = "register",
                onSuccess = { showVerifyDialog(email) },
                onAccountExists = {
                    SettingsOtpDialogDelegate.showAccountExistsDialog(
                        context = activity,
                        email = email,
                        onSignIn = { showLoginDialog() },
                        onResetPassword = { showForgotPasswordDialog(email) }
                    )
                }
            )
        }
    }

    fun showVerifyDialog(email: String) {
        SettingsOtpDialogDelegate.showVerifyDialog(activity, email) { otp, pass ->
            authHelper.verifyAndRegister(email, otp, pass)
        }
    }

    fun showLoginDialog() {
        SettingsOtpDialogDelegate.showLoginDialog(
            activity,
            onLogin = { email, pass -> authHelper.login(email, pass) },
            onForgotPassword = { email -> showForgotPasswordDialog(email) }
        )
    }

    fun showForgotPasswordDialog(defaultEmail: String = "") {
        SettingsOtpDialogDelegate.showForgotPasswordDialog(
            activity,
            defaultEmail
        ) { email ->
            authHelper.requestOtp(
                email = email,
                purpose = "reset",
                onSuccess = { showResetPasswordDialog(email) }
            )
        }
    }

    fun showResetPasswordDialog(email: String) {
        SettingsOtpDialogDelegate.showResetPasswordDialog(activity, email) { otp, pass ->
            authHelper.resetPassword(email, otp, pass)
        }
    }
}
