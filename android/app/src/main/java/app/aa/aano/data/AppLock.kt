// App lock: PIN (hashed with PBKDF2 + salt) and elapsed-time auto-lock.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AppLock(context: Context) {

    private val prefs = Prefs(context)

    val pinEnabled: Boolean get() = !prefs.pinHash.isNullOrBlank()
    val lockTimeoutMinutes: Long get() = prefs.lockTimeoutMinutes

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.pinHash = Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + hash(pin, salt)
    }

    fun clearPin() {
        prefs.pinHash = null
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.pinHash ?: return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        return MessageDigest.isEqual(
            hash(pin, salt).toByteArray(Charsets.UTF_8),
            parts[1].toByteArray(Charsets.UTF_8)
        )
    }

    private fun hash(pin: String, salt: ByteArray): String {
        // PBKDF2WithHmacSHA256 is the standard password hashing primitive
        // available on all Android devices.
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        private const val ITERATIONS = 20_000
        private const val KEY_LENGTH = 256
    }
}