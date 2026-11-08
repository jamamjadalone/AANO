// Cloudflare Edge Sync Manager for AANO
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.net

import android.content.Context
import app.aa.aano.data.Note
import app.aa.aano.data.NoteMergeEngine
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.Task
import app.aa.aano.data.TaskPriority
import app.aa.aano.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class CloudSyncManager(
    private val context: Context,
    private val notesRepo: NoteRepository,
    private val tasksRepo: TaskRepository
) {
    private val prefs = Prefs(context)

    companion object {
        const val BASE_URL = "https://aano-api.jamamjadalone.workers.dev"
        const val APP_KEY = "aano_shield_key_2026_pk_sec"
        private const val TIMEOUT_MS = 15000
    }

    val isLoggedIn: Boolean
        get() {
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) return true
            return !prefs.cloudToken.isNullOrBlank()
        }

    val currentEmail: String?
        get() {
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) return account.email
            return prefs.cloudEmail
        }

    suspend fun requestOtp(email: String, purpose: String = "register"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("purpose", purpose)
            }
            val res = postJson("$BASE_URL/api/request-otp", body, null)
            if (res.optBoolean("success", false)) {
                Result.success(res.optString("message", "OTP sent"))
            } else {
                Result.failure(Exception(res.optString("error", "Failed to send OTP")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyAndRegister(email: String, otp: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("otp", otp)
                put("password", pass)
            }
            val res = postJson("$BASE_URL/api/verify-and-register", body, null)
            if (res.optBoolean("success", false)) {
                prefs.cloudToken = res.getString("token")
                prefs.cloudEmail = res.getString("email")
                prefs.cloudUserId = res.getString("userId")
                Result.success(true)
            } else {
                Result.failure(Exception(res.optString("error", "Registration failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String, otp: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("otp", otp)
                put("password", pass)
            }
            val res = postJson("$BASE_URL/api/reset-password", body, null)
            if (res.optBoolean("success", false)) {
                prefs.cloudToken = res.getString("token")
                prefs.cloudEmail = res.getString("email")
                prefs.cloudUserId = res.getString("userId")
                Result.success(true)
            } else {
                Result.failure(Exception(res.optString("error", "Password reset failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", pass)
            }
            val res = postJson("$BASE_URL/api/login", body, null)
            if (res.optBoolean("success", false)) {
                prefs.cloudToken = res.getString("token")
                prefs.cloudEmail = res.getString("email")
                prefs.cloudUserId = res.getString("userId")
                Result.success(true)
            } else {
                Result.failure(Exception(res.optString("error", "Invalid credentials")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncNow(): Result<Int> = withContext(Dispatchers.IO) {
        val googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        if (googleAccount != null) {
            val driveSync = DriveSyncManager(context, notesRepo, tasksRepo)
            val ok = driveSync.sync()
            return@withContext if (ok) Result.success(notesRepo.getAll().size) else Result.failure(Exception("Google Drive sync failed"))
        }

        Result.failure(Exception("Google Drive account required for cloud sync. Please sign in with Google."))
    }

    suspend fun subscribeNewsletter(email: String, source: String = "android_app"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("source", source)
            }
            val res = postJson("$BASE_URL/api/newsletter/subscribe", body, null)
            if (res.optBoolean("success", false)) {
                Result.success(res.optString("message", "Subscribed to newsletter"))
            } else {
                Result.failure(Exception(res.optString("error", "Subscription failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unsubscribeNewsletter(email: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
            }
            val res = postJson("$BASE_URL/api/newsletter/unsubscribe", body, null)
            if (res.optBoolean("success", false)) {
                Result.success(res.optString("message", "Unsubscribed from newsletter"))
            } else {
                Result.failure(Exception(res.optString("error", "Unsubscribe failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        prefs.cloudToken = null
        prefs.cloudEmail = null
        prefs.cloudUserId = null
        prefs.lastCloudSyncTime = 0L
        try {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                context,
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).signOut()
        } catch (e: Exception) {
            android.util.Log.w("CloudSyncManager", "Google sign out notice: ${e.message}")
        }
    }

    private fun postJson(urlStr: String, body: JSONObject, token: String?): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-AANO-KEY", APP_KEY)
            setRequestProperty("User-Agent", "AANO-Android/1.0")
            if (token != null) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            ?: throw Exception("HTTP $code with no response")

        val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
        return JSONObject(responseText)
    }
}
