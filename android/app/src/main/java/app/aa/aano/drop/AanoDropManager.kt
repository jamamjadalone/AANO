// AANO Drop - Bidirectional Local Wi-Fi P2P Transfer Engine for Android
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.drop

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.util.Log
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.random.Random

class AanoDropManager(private val context: Context) {

    companion object {
        const val DROP_PORT = 8766
        const val PROTOCOL_VERSION = "aano-drop-v1"
        private const val TAG = "AANODrop"

        @Volatile
        private var instance: AanoDropManager? = null

        fun getInstance(context: Context): AanoDropManager {
            return instance ?: synchronized(this) {
                instance ?: AanoDropManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val repository = NoteRepository(context)
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    val pin = String.format("%06d", Random.nextInt(100000, 999999))
    val token = "drop_android_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"

    var onNotesReceivedListener: ((count: Int, images: Int) -> Unit)? = null

    fun startServer() {
        if (isRunning) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                lockProcessToWifi()
                serverSocket = ServerSocket(DROP_PORT)
                isRunning = true
                Log.i(TAG, "AANO Drop Android server started on port $DROP_PORT with PIN $pin")

                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "Server accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start drop server: ${e.message}", e)
                isRunning = false
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        }
    }

    private fun handleClient(client: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.tcpNoDelay = true
                val input = client.getInputStream()
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                val output = client.getOutputStream()

                // Read request line
                val requestLine = reader.readLine() ?: return@launch
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                // Read headers
                var contentLength = 0
                var incomingToken = ""
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    val header = line!!
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                    } else if (header.startsWith("X-AANO-TOKEN:", ignoreCase = true)) {
                        incomingToken = header.substringAfter(":").trim()
                    }
                }

                if (method.equals("OPTIONS", ignoreCase = true)) {
                    sendHttpResponse(output, 200, "OK", "text/plain", "")
                    return@launch
                }

                if (path.startsWith("/aano/info")) {
                    val info = JSONObject().apply {
                        put("protocol", PROTOCOL_VERSION)
                        put("device", "Android Phone")
                        put("ip", getLocalWifiIp())
                        put("port", DROP_PORT)
                        put("status", "ready")
                    }
                    sendHttpResponse(output, 200, "OK", "application/json", info.toString())
                    return@launch
                }

                if (path.startsWith("/aano/drop") && method.equals("POST", ignoreCase = true)) {
                    if (contentLength <= 0 || contentLength > 100 * 1024 * 1024) {
                        sendHttpResponse(output, 400, "Bad Request", "application/json", "{\"success\":false,\"error\":\"Invalid content length\"}")
                        return@launch
                    }

                    // Read payload body
                    val bodyChars = CharArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    val bodyString = String(bodyChars, 0, totalRead)
                    val payload = JSONObject(bodyString)

                    // Validate PIN or Token
                    val reqToken = payload.optString("token").ifEmpty { payload.optString("pin", incomingToken) }
                    if (pin != reqToken && token != reqToken) {
                        sendHttpResponse(output, 403, "Forbidden", "application/json", "{\"success\":false,\"error\":\"Invalid PIN or Token\"}")
                        return@launch
                    }

                    // 1. Process images
                    val imagesArr = payload.optJSONArray("images")
                    var savedImages = 0
                    if (imagesArr != null) {
                        val mediaDir = File(context.filesDir, "media/images").apply { mkdirs() }
                        for (i in 0 until imagesArr.length()) {
                            val imgObj = imagesArr.getJSONObject(i)
                            val name = File(imgObj.optString("name", "img_${System.currentTimeMillis()}.webp")).name
                            val b64 = imgObj.optString("data_base64").ifEmpty { imgObj.optString("bytes_base64") }
                            if (name.isNotEmpty() && b64.isNotEmpty()) {
                                try {
                                    val cleanB64 = if (b64.contains(",")) b64.substringAfter(",") else b64
                                    val rawBytes = Base64.decode(cleanB64, Base64.DEFAULT)
                                    File(mediaDir, name).writeBytes(rawBytes)
                                    savedImages++
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to write dropped image $name: ${e.message}")
                                }
                            }
                        }
                    }

                    // 2. Process notes
                    val notesArr = payload.optJSONArray("notes")
                    var savedNotes = 0
                    if (notesArr != null) {
                        for (i in 0 until notesArr.length()) {
                            val nObj = notesArr.getJSONObject(i)
                            val id = nObj.optString("id")
                            if (id.isEmpty()) continue
                            val note = repository.deserialize(nObj).copy(
                                isLocal = true,
                                updatedAt = maxOf(nObj.optLong("updatedAt", System.currentTimeMillis()), System.currentTimeMillis())
                            )
                            repository.upsertNote(note)
                            savedNotes++
                        }
                    }

                    // Haptic buzz feedback
                    triggerHapticChime()

                    // Dispatch UI update
                    withContext(Dispatchers.Main) {
                        onNotesReceivedListener?.invoke(savedNotes, savedImages)
                    }

                    val respJson = JSONObject().apply {
                        put("success", true)
                        put("count", savedNotes)
                        put("images", savedImages)
                    }
                    sendHttpResponse(output, 200, "OK", "application/json", respJson.toString())
                } else {
                    sendHttpResponse(output, 404, "Not Found", "text/plain", "Endpoint not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling drop client: ${e.message}", e)
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }
    }

    private fun sendHttpResponse(output: OutputStream, code: Int, reason: String, contentType: String, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $reason\r\n" +
                "Content-Type: $contentType; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }
        output.flush()
    }

    private fun triggerHapticChime() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(180)
                }
            }
        } catch (_: Exception) {}
    }

    private fun lockProcessToWifi() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cm.bindProcessToNetwork(network)
                        Log.i(TAG, "Process successfully bound to Wi-Fi network: zero cellular quota egress.")
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Wi-Fi process lock warning: ${e.message}")
        }
    }

    fun getLocalWifiIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    suspend fun sendDropToDevice(
        targetIp: String,
        targetPort: Int = 8765,
        notes: List<Note>,
        targetPin: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = "http://$targetIp:$targetPort/aano/drop"
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (!targetPin.isNullOrBlank()) {
                    setRequestProperty("X-AANO-TOKEN", targetPin)
                }
            }

            // Pack notes
            val notesArr = JSONArray()
            val requiredImages = mutableSetOf<String>()
            for (note in notes) {
                notesArr.put(repository.serialize(note))
                for (img in note.images) {
                    val fn = File(img).name
                    if (fn.isNotEmpty()) requiredImages.add(fn)
                }
            }

            // Pack images
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

            val payload = JSONObject().apply {
                put("protocol", PROTOCOL_VERSION)
                put("pin", targetPin ?: "")
                put("token", targetPin ?: "")
                put("notes", notesArr)
                put("images", imagesArr)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(resp)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendDropToDevice failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
