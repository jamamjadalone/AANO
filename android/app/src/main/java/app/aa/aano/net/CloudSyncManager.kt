// Cloudflare Edge Sync Manager for AANO
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.net

import android.content.Context
import app.aa.aano.data.Note
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
        get() = !prefs.cloudToken.isNullOrBlank()

    val currentEmail: String?
        get() = prefs.cloudEmail

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
        val token = prefs.cloudToken ?: return@withContext Result.failure(Exception("Not logged in"))
        try {
            val localNotes = notesRepo.getAll()
            val localTasks = tasksRepo.getAll()

            val notesArray = JSONArray()
            for (n in localNotes) {
                notesArray.put(JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("content", n.content)
                    put("color", n.color)
                    put("folder", n.folder)
                    put("pinned", n.pinned)
                    put("updatedAt", n.updatedAt)
                    put("deleted", n.deleted)
                    put("archived", n.archived)
                })
            }

            val tasksArray = JSONArray()
            for (t in localTasks) {
                tasksArray.put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("completed", t.completed)
                    put("priority", t.priority.name)
                    put("dueAt", t.dueAt ?: 0L)
                    put("updatedAt", t.updatedAt)
                })
            }

            val payload = JSONObject().apply {
                put("notes", notesArray)
                put("tasks", tasksArray)
                put("lastSyncTime", prefs.lastCloudSyncTime)
            }

            val res = postJson("$BASE_URL/api/sync", payload, token)
            if (res.optBoolean("success", false)) {
                val serverNotes = res.optJSONArray("notes") ?: JSONArray()
                val serverTasks = res.optJSONArray("tasks") ?: JSONArray()

                // Merge server notes into local
                if (serverNotes.length() > 0) {
                    val incomingNotes = mutableListOf<Note>()
                    val localNotesMap = localNotes.associateBy { it.id }
                    for (i in 0 until serverNotes.length()) {
                        val obj = serverNotes.getJSONObject(i)
                        val parsed = Note.fromJson(obj)
                        val localExisting = localNotesMap[parsed.id]
                        val finalNote = if (!obj.has("archived") && localExisting != null) {
                            parsed.copy(archived = localExisting.archived)
                        } else if (localExisting != null && localExisting.archived && !parsed.archived && parsed.updatedAt <= localExisting.updatedAt) {
                            parsed.copy(archived = true)
                        } else {
                            parsed
                        }
                        incomingNotes.add(finalNote)
                    }
                    notesRepo.replaceAll(incomingNotes)
                }

                // Merge server tasks into local
                if (serverTasks.length() > 0) {
                    val incomingTasks = mutableListOf<Task>()
                    for (i in 0 until serverTasks.length()) {
                        val obj = serverTasks.getJSONObject(i)
                        val prioStr = obj.optString("priority", "MEDIUM")
                        val prio = runCatching { TaskPriority.valueOf(prioStr) }.getOrDefault(TaskPriority.MEDIUM)
                        val dueAtRaw = obj.optLong("dueAt", 0L)
                        incomingTasks.add(
                            Task(
                                id = obj.getString("id"),
                                title = obj.optString("title", ""),
                                completed = obj.optBoolean("completed", false),
                                priority = prio,
                                dueAt = if (dueAtRaw > 0) dueAtRaw else null,
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    tasksRepo.replaceAll(incomingTasks)
                }

                prefs.lastCloudSyncTime = res.optLong("serverTime", System.currentTimeMillis())
                Result.success(serverNotes.length() + serverTasks.length())
            } else {
                val err = res.optString("error", "Sync error")
                if (err == "Unauthorized access token." || err.contains("401")) {
                    prefs.cloudToken = null
                }
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            if (e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true) {
                prefs.cloudToken = null
            }
            Result.failure(e)
        }
    }

    fun logout() {
        prefs.cloudToken = null
        prefs.cloudEmail = null
        prefs.cloudUserId = null
        prefs.lastCloudSyncTime = 0L
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
