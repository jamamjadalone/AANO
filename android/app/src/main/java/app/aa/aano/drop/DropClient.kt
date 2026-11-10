// Outbound P2P HTTP client for dispatching AANO Drop payloads.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.drop

import android.content.Context
import android.util.Base64
import android.util.Log
import app.aa.aano.data.Note
import app.aa.aano.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DropClient(private val context: Context, private val prefs: Prefs) {

    companion object {
        private const val TAG = "DropClient"
        const val DROP_PORT = 8766
        const val PROTOCOL_VERSION = "aano-drop-v1"
    }

    suspend fun sendDropToDevice(
        targetIp: String,
        targetPort: Int = 8765,
        notes: List<Note>,
        targetPin: String? = null,
        myIp: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val (host, explicitPort) = if (targetIp.contains(":")) {
            val parts = targetIp.split(":")
            Pair(parts[0].trim(), parts[1].trim().toIntOrNull() ?: targetPort)
        } else {
            Pair(targetIp.trim(), null)
        }

        val portsToTry = if (explicitPort != null) {
            listOf(explicitPort)
        } else {
            listOf(targetPort, if (targetPort == 8765) 8766 else 8765)
        }

        val notesArr = JSONArray()
        val requiredImages = mutableSetOf<String>()
        for (note in notes) {
            notesArr.put(note.toJson())
            for (img in note.images) {
                val fn = File(img).name
                if (fn.isNotEmpty()) requiredImages.add(fn)
            }
        }

        val imagesArr = JSONArray()
        val mediaDir = File(context.filesDir, "media/images")
        for (fn in requiredImages) {
            val f = File(mediaDir, fn)
            if (f.exists()) {
                val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                imagesArr.put(JSONObject().apply {
                    put("name", fn)
                    put("data_base64", b64)
                })
            }
        }

        val effectivePin = if (!targetPin.isNullOrBlank()) targetPin else prefs.lastDropPin
        var isEncrypted = false
        var ivB64: String? = null
        var dataB64: String? = null

        if (effectivePin.isNotBlank()) {
            try {
                val innerJson = JSONObject().apply {
                    put("notes", notesArr)
                    put("images", imagesArr)
                }
                val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
                val keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest("AANO_DROP_TRANSPORT_KEY_V1:$effectivePin".toByteArray(Charsets.UTF_8))
                val secretKey = SecretKeySpec(keyBytes, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                val ct = cipher.doFinal(innerJson.toString().toByteArray(Charsets.UTF_8))

                ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                dataB64 = Base64.encodeToString(ct, Base64.NO_WRAP)
                isEncrypted = true
            } catch (e: Exception) {
                Log.w(TAG, "Transport encryption failed: ${e.message}")
            }
        }

        val payload = JSONObject().apply {
            put("protocol", PROTOCOL_VERSION)
            put("pin", effectivePin)
            put("token", effectivePin)
            put("sender_name", "Android Phone")
            put("sender_ip", myIp)
            put("sender_port", DROP_PORT)
            put("encrypted", isEncrypted)
            if (isEncrypted) {
                put("iv_b64", ivB64)
                put("data_b64", dataB64)
                put("notes", JSONArray())
                put("images", JSONArray())
            } else {
                put("notes", notesArr)
                put("images", imagesArr)
            }
        }
        val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)

        var lastException: Exception? = null
        for (port in portsToTry) {
            try {
                val targetUrl = "http://$host:$port/aano/drop"
                val url = URL(targetUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (effectivePin.isNotBlank()) {
                        setRequestProperty("X-AANO-TOKEN", effectivePin)
                    }
                }

                conn.outputStream.use { os ->
                    os.write(payloadBytes)
                    os.flush()
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    prefs.lastDropIp = if (port == 8765) host else "$host:$port"
                    if (effectivePin.isNotBlank()) prefs.lastDropPin = effectivePin
                    if (prefs.lastDropPeerName.isBlank()) prefs.lastDropPeerName = "Desktop PC"
                    return@withContext Result.success(resp)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    return@withContext Result.failure(Exception(err))
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "AANO Drop attempt to $host:$port failed: ${e.message}, trying next...")
            }
        }
        Log.e(TAG, "sendDropToDevice all attempts failed: ${lastException?.message}")
        Result.failure(lastException ?: Exception("Could not connect to $host on ports $portsToTry"))
    }
}
