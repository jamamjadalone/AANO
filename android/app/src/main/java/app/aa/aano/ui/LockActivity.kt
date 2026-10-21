// Full-screen app lock: PIN entry with an in-app keypad, optional
// biometric (fingerprint / face) unlock, and screenshot protection.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import app.aa.aano.R
import app.aa.aano.data.AppLock

class LockActivity : AppCompatActivity() {

    private lateinit var lock: AppLock
    private lateinit var dotsRow: LinearLayout
    private lateinit var msg: TextView
    private lateinit var pinError: TextView

    private var pinBuffer = StringBuilder()
    private var pendingConfirm: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never show app content in screenshots/recents while locked.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_lock)
        lock = AppLock(this)

        dotsRow = findViewById(R.id.dotsRow)
        msg = findViewById(R.id.lockMsg)
        pinError = findViewById(R.id.pinError)

        val mode = intent.getStringExtra(EXTRA_MODE)
        if (mode == MODE_SET_PIN) {
            msg.text = getString(R.string.enter_new_pin)
        } else if (mode == MODE_CONFIRM_PIN) {
            pendingConfirm = intent.getStringExtra(EXTRA_NEW_PIN)
            msg.text = getString(R.string.confirm_pin)
        } else if (mode == MODE_DISABLE) {
            msg.text = getString(R.string.enter_pin)
        }

        buildKeypad()
        updateDots()

        setupBiometric()
        if (mode == MODE_SIGN_OUT_ONLY) {
            msg.text = getString(R.string.enter_pin)
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentMode = intent.getStringExtra(EXTRA_MODE)
                if (currentMode == MODE_UNLOCK) {
                    finishAffinity()
                } else {
                    finish()
                }
            }
        })
    }

    private fun buildKeypad() {
        val keypad = findViewById<LinearLayout>(R.id.keypadHolder) ?: return
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "back", "0", "ok")
        keys.chunked(3).forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowKeys.forEach { label ->
                val btn = Button(this).apply {
                    text = when (label) {
                        "back" -> "◀"
                        "ok" -> "OK"
                        else -> label
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(72), dp(60)).also {
                        it.setMargins(dp(6), dp(6), dp(6), dp(6))
                    }
                    setOnClickListener { onKey(label) }
                }
                row.addView(btn)
            }
            keypad.addView(row)
        }
    }

    private fun onKey(label: String) {
        pinError.visibility = View.INVISIBLE
        when (label) {
            "back" -> if (pinBuffer.isNotEmpty()) pinBuffer.deleteCharAt(pinBuffer.length - 1)
            "ok" -> submitPin()
            else -> if (pinBuffer.length < MAX_PIN) pinBuffer.append(label)
        }
        updateDots()
    }

    private fun submitPin() {
        val pin = pinBuffer.toString()
        if (pin.length != MAX_PIN) {
            if (pin.isEmpty()) return
            pinError.text = getString(R.string.pin_wrong_setup)
            pinError.visibility = View.VISIBLE
            return
        }
        val mode = intent.getStringExtra(EXTRA_MODE)
        when (mode) {
            MODE_SET_PIN -> {
                // First entry: go to confirm screen with this PIN.
                val confirmIntent = Intent(this, LockActivity::class.java).apply {
                    putExtra(EXTRA_MODE, MODE_CONFIRM_PIN)
                    putExtra(EXTRA_NEW_PIN, pin)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(confirmIntent)
                finish()
            }
            MODE_CONFIRM_PIN -> {
                if (pin == pendingConfirm) {
                    lock.setPin(pin)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    pinError.text = getString(R.string.pins_dont_match)
                    pinError.visibility = View.VISIBLE
                    pinBuffer.clear()
                    updateDots()
                }
            }
            MODE_DISABLE -> {
                if (lock.verifyPin(pin)) {
                    lock.clearPin()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    showWrongPin()
                }
            }
            else -> {
                if (lock.verifyPin(pin)) {
                    app.aa.aano.AanoApp.isUnlockedThisSession = true
                    setResult(RESULT_OK)
                    finish()
                } else {
                    showWrongPin()
                }
            }
        }
    }

    private fun showWrongPin() {
        pinError.text = getString(R.string.wrong_pin)
        pinError.visibility = View.VISIBLE
        pinBuffer.clear()
        updateDots()
    }

    private fun updateDots() {
        dotsRow.removeAllViews()
        for (i in 0 until MAX_PIN) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(dp(14), dp(14))
            params.setMargins(dp(8), 0, dp(8), 0)
            dot.layoutParams = params
            dot.background = ContextCompat.getDrawable(
                this,
                if (i < pinBuffer.length) R.drawable.dot_filled else R.drawable.dot_empty
            )
            dotsRow.addView(dot)
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun setupBiometric() {
        val btn = findViewById<Button>(R.id.btnBiometric)
        val mode = intent.getStringExtra(EXTRA_MODE)
        val canBiometric = mode != MODE_SET_PIN &&
            mode != MODE_CONFIRM_PIN &&
            androidx.biometric.BiometricManager.from(this)
                .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        if (!canBiometric) return
        btn.visibility = View.VISIBLE
        btn.setOnClickListener { launchBiometric() }
    }

    private fun launchBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    app.aa.aano.AanoApp.isUnlockedThisSession = true
                    setResult(RESULT_OK)
                    finish()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_auth_title))
                .setSubtitle(getString(R.string.biometric_auth_subtitle))
                .setNegativeButtonText(getString(R.string.biometric_negative))
                .build()
        )
    }

    companion object {
        const val EXTRA_MODE = "lock_mode"
        const val EXTRA_NEW_PIN = "new_pin"
        const val MODE_UNLOCK = "unlock"
        const val MODE_SET_PIN = "set"
        const val MODE_CONFIRM_PIN = "confirm"
        const val MODE_DISABLE = "disable"
        const val MODE_SIGN_OUT_ONLY = "signout"
        private const val MAX_PIN = 4
    }
}