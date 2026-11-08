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
import app.aa.aano.data.NoteMergeEngine
import app.aa.aano.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.random.Random

class AanoDropManager(private val context: Context) {

    companion object {
        const val DROP_PORT = 8766
        const val DISCOVERY_PORT = 8767
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
    private val prefs = app.aa.aano.data.Prefs(context)
    private var serverSocket: ServerSocket? = null
    private var udpBeaconSocket: DatagramSocket? = null
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null
    private var isRunning = false
    val pin = String.format("%06d", Random.nextInt(100000, 999999))
    val token = "drop_android_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"

    var onNotesReceivedListener: ((count: Int, images: Int) -> Unit)? = null

    private fun startUdpBeacon() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("aano_beacon_lock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
                udpBeaconSocket = DatagramSocket(DISCOVERY_PORT).apply {
                    soTimeout = 1000
                    broadcast = true
                }
                val buf = ByteArray(2048)
                while (isRunning) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        udpBeaconSocket?.receive(packet) ?: break
                        val msgStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(msgStr)
                        if (json.optString("action") == "ping" && json.optString("app") == "aano") {
                            val resp = JSONObject().apply {
                                put("action", "pong")
                                put("app", "aano")
                                put("device", Build.MODEL)
                                put("ip", getLocalWifiIp())
                                put("port", DROP_PORT)
                                put("type", "android")
                            }
                            val respBytes = resp.toString().toByteArray(Charsets.UTF_8)
                            val respPacket = DatagramPacket(respBytes, respBytes.size, packet.address, packet.port)
                            udpBeaconSocket?.send(respPacket)
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        if (isRunning) Log.d(TAG, "UDP beacon loop error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start UDP beacon on port $DISCOVERY_PORT: ${e.message}")
            } finally {
                try {
                    multicastLock?.release()
                    multicastLock = null
                } catch (e: Exception) {
                    Log.d(TAG, "MulticastLock release notice: ${e.message}")
                }
            }
        }
    }

    fun startServer() {
        if (isRunning) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                lockProcessToWifi()
                serverSocket = ServerSocket(DROP_PORT)
                isRunning = true
                startUdpBeacon()
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
            udpBeaconSocket?.close()
            udpBeaconSocket = null
            multicastLock?.release()
            multicastLock = null
        } catch (e: Exception) {
            Log.d(TAG, "Error closing UDP beacon: ${e.message}")
        }
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
                val output = client.getOutputStream()

                // Read HTTP request headers byte-by-byte until \r\n\r\n
                val headerBytes = ByteArrayOutputStream()
                var p3 = -1
                var p2 = -1
                var p1 = -1
                var b: Int
                while (input.read().also { b = it } != -1) {
                    headerBytes.write(b)
                    if (p3 == '\r'.code && p2 == '\n'.code && p1 == '\r'.code && b == '\n'.code) {
                        break
                    }
                    p3 = p2
                    p2 = p1
                    p1 = b
                }

                val headerString = headerBytes.toString("UTF-8")
                val lines = headerString.lines()
                val requestLine = lines.firstOrNull() ?: return@launch
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                // Read headers
                var contentLength = 0
                var incomingToken = ""
                var incomingPinHeader = ""
                for (hdr in lines) {
                    if (hdr.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = hdr.substringAfter(":").trim().toIntOrNull() ?: 0
                    } else if (hdr.startsWith("X-AANO-TOKEN:", ignoreCase = true)) {
                        incomingToken = hdr.substringAfter(":").trim()
                    } else if (hdr.startsWith("X-AANO-PIN:", ignoreCase = true)) {
                        incomingPinHeader = hdr.substringAfter(":").trim()
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

                if (path.startsWith("/aano/pair") && method.equals("POST", ignoreCase = true)) {
                    if (contentLength <= 0 || contentLength > 64 * 1024) {
                        sendHttpResponse(output, 400, "Bad Request", "application/json", "{\"success\":false,\"error\":\"Invalid pair payload length\"}")
                        return@launch
                    }
                    val bodyBytes = ByteArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = input.read(bodyBytes, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    val bodyString = String(bodyBytes, 0, totalRead, Charsets.UTF_8)
                    val payload = JSONObject(bodyString)
                    val senderName = payload.optString("name", "Desktop PC")
                    val senderIp = payload.optString("ip").ifEmpty { client.inetAddress?.hostAddress ?: "" }
                    val senderPort = payload.optInt("port", 8765)
                    val incomingPin = payload.optString("pin")
                    if (pin.isNotEmpty() && (incomingPin.isEmpty() || pin != incomingPin)) {
                        sendHttpResponse(output, 403, "Forbidden", "application/json", "{\"success\":false,\"error\":\"Invalid or missing PIN\"}")
                        return@launch
                    }
                    prefs.lastDropIp = if (senderPort == 8765) senderIp else "$senderIp:$senderPort"
                    prefs.lastDropPeerName = senderName
                    prefs.lastDropPin = incomingPin
                    val respJson = JSONObject().apply {
                        put("success", true)
                        put("name", "Android Phone")
                        put("ip", getLocalWifiIp())
                        put("port", DROP_PORT)
                    }
                    sendHttpResponse(output, 200, "OK", "application/json", respJson.toString())
                    return@launch
                }

                if (path.startsWith("/aano/drop") && method.equals("POST", ignoreCase = true)) {
                    if (contentLength <= 0 || contentLength > 20 * 1024 * 1024) {
                        sendHttpResponse(output, 400, "Bad Request", "application/json", "{\"success\":false,\"error\":\"Invalid content length\"}")
                        return@launch
                    }

                    // Pre-Auth Check before memory allocation (prevents DoS heap exhaustion)
                    val headerAuth = incomingToken.ifEmpty { incomingPinHeader }
                    val clientIp = client.inetAddress?.hostAddress ?: ""
                    val isPaired = prefs.lastDropIp.isNotEmpty() && prefs.lastDropIp.startsWith(clientIp)
                    if (contentLength > 64 * 1024) {
                        val authMatches = (headerAuth.isNotEmpty() && (headerAuth == pin || headerAuth == token)) || isPaired
                        if (!authMatches) {
                            sendHttpResponse(output, 403, "Forbidden", "application/json", "{\"success\":false,\"error\":\"Invalid or missing authentication headers for large payload\"}")
                            return@launch
                        }
                    }

                    // Read payload body as raw bytes (full UTF-8 & Unicode immunity)
                    val bodyBytes = ByteArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = input.read(bodyBytes, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    val bodyString = String(bodyBytes, 0, totalRead, Charsets.UTF_8)
                    val payload = JSONObject(bodyString)

                    // Auto-record sender peer
                    val senderName = payload.optString("sender_name", "Desktop PC")
                    val senderIp = payload.optString("sender_ip").ifEmpty { client.inetAddress?.hostAddress ?: "" }
                    val senderPort = payload.optInt("sender_port", 8765)
                    if (senderIp.isNotEmpty()) {
                        prefs.lastDropIp = if (senderPort == 8765) senderIp else "$senderIp:$senderPort"
                        prefs.lastDropPeerName = senderName
                    }

                    // Validate PIN or Token
                    val reqToken = payload.optString("token").ifEmpty { payload.optString("pin", headerAuth) }
                    if (pin != reqToken && token != reqToken && !isPaired) {
                        sendHttpResponse(output, 403, "Forbidden", "application/json", "{\"success\":false,\"error\":\"Invalid PIN or Token\"}")
                        return@launch
                    }

                    // 1. Process images with extension whitelist
                    val allowedImageExts = setOf("png", "jpg", "jpeg", "webp", "gif")
                    val imagesArr = payload.optJSONArray("images")
                    var savedImages = 0
                    if (imagesArr != null) {
                        val mediaDir = File(context.filesDir, "media/images").apply { mkdirs() }
                        for (i in 0 until imagesArr.length()) {
                            val imgObj = imagesArr.getJSONObject(i)
                            val rawName = File(imgObj.optString("name", "img_${System.currentTimeMillis()}.webp")).name
                            val ext = rawName.substringAfterLast('.', "").lowercase()
                            if (ext !in allowedImageExts) {
                                Log.w(TAG, "Rejected non-image drop upload: $rawName")
                                continue
                            }
                            val b64 = imgObj.optString("data_base64").ifEmpty { imgObj.optString("bytes_base64") }
                            if (rawName.isNotEmpty() && b64.isNotEmpty()) {
                                try {
                                    val cleanB64 = if (b64.contains(",")) b64.substringAfter(",") else b64
                                    val rawBytes = Base64.decode(cleanB64, Base64.DEFAULT)
                                    File(mediaDir, rawName).writeBytes(rawBytes)
                                    savedImages++
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to write dropped image $rawName: ${e.message}")
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
                            val incoming = repository.deserialize(nObj)
                            val existing = repository.getById(id)
                            val finalNote = if (existing != null && existing.content != incoming.content) {
                                val (mergedContent, _) = NoteMergeEngine.mergeNoteContent(existing.content, incoming.content)
                                incoming.copy(
                                    content = mergedContent,
                                    tags = (existing.tags + incoming.tags).distinct(),
                                    images = (existing.images + incoming.images).distinct(),
                                    isLocal = true,
                                    updatedAt = maxOf(nObj.optLong("updatedAt", System.currentTimeMillis()), System.currentTimeMillis())
                                )
                            } else {
                                incoming.copy(
                                    isLocal = true,
                                    updatedAt = maxOf(nObj.optLong("updatedAt", System.currentTimeMillis()), System.currentTimeMillis())
                                )
                            }
                            repository.upsertNote(finalNote)
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
                try {
                    client.close()
                } catch (e: Exception) {
                    Log.d(TAG, "Socket client close notice: ${e.message}")
                }
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
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(180)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AanoDrop", "Haptic chime suppressed: ${e.message}")
        }
    }

    enum class DropNetworkMode {
        WIFI_CONNECTED,
        HOTSPOT_ACTIVE,
        OFFLINE
    }

    data class DropNetworkState(
        val ip: String,
        val mode: DropNetworkMode,
        val message: String
    )

    fun getNetworkState(): DropNetworkState {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifiClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            cm?.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }

        var wifiIp: String? = null
        var hotspotIp: String? = null

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val name = iface.name.lowercase()

                // Exclude cellular / virtual / tunnel interfaces
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") ||
                    name.startsWith("ppp") || name.startsWith("dummy") || name.startsWith("tun") ||
                    name.startsWith("sit") || name.startsWith("ip6tnl")) {
                    continue
                }

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("127.")) continue

                        // Check for Hotspot interface or IP (ap0, softap, rndis, or 192.168.43.1 / 192.168.44.1 / 192.168.50.1 / 192.168.49.1)
                        val isApInterface = name.startsWith("ap") || name.startsWith("softap") || name.contains("tether") || name.contains("hotspot")
                        val isHotspotSubnet = host.startsWith("192.168.43.") || host.startsWith("192.168.44.") || host.startsWith("192.168.50.") || host.startsWith("192.168.49.")

                        if (isApInterface || isHotspotSubnet) {
                            if (hotspotIp == null) hotspotIp = host
                        } else if (name.startsWith("wlan") || name.startsWith("eth")) {
                            if (wifiIp == null) wifiIp = host
                        } else if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            if (wifiIp == null) wifiIp = host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network state detection error: ${e.message}")
        }

        return when {
            isWifiClient && wifiIp != null -> {
                DropNetworkState(wifiIp, DropNetworkMode.WIFI_CONNECTED, "Connected to Wi-Fi ($wifiIp)")
            }
            hotspotIp != null -> {
                DropNetworkState(hotspotIp, DropNetworkMode.HOTSPOT_ACTIVE, "Direct Hotspot Active ($hotspotIp)")
            }
            wifiIp != null -> {
                DropNetworkState(wifiIp, DropNetworkMode.HOTSPOT_ACTIVE, "Local AP / Hotspot ($wifiIp)")
            }
            else -> {
                DropNetworkState("127.0.0.1", DropNetworkMode.OFFLINE, "No Wi-Fi or Hotspot active")
            }
        }
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
                        if (getNetworkState().mode == DropNetworkMode.WIFI_CONNECTED) {
                            cm.bindProcessToNetwork(network)
                            Log.i(TAG, "Process successfully bound to Wi-Fi network: zero cellular quota egress.")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Wi-Fi process lock warning: ${e.message}")
        }
    }

    fun getLocalWifiIp(): String {
        return getNetworkState().ip
    }

    fun generateQrBitmap(contents: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            hints[EncodeHintType.MARGIN] = 2
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(contents, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val encoder = BarcodeEncoder()
            encoder.createBitmap(bitMatrix)
        } catch (e: Exception) {
            Log.w(TAG, "BarcodeEncoder error: ${e.message}, fallback to QrCode.java")
            try {
                QrCode.encodeText(contents, QrCode.Ecc.MEDIUM).toBitmap(sizePx)
            } catch (_: Exception) { null }
        }
    }

    fun openHotspotSettings(activityContext: Context) {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activityContext.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.d(TAG, "Tether intent fallback skipped: ${e.message}")
            }
        }
    }

    fun openWifiSettings(activityContext: Context) {
        try {
            activityContext.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch Wi-Fi settings: ${e.message}")
        }
    }

    suspend fun sendDropToDevice(
        targetIp: String,
        targetPort: Int = 8765,
        notes: List<Note>,
        targetPin: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val (host, explicitPort) = if (targetIp.contains(":")) {
            val parts = targetIp.split(":")
            Pair(parts[0].trim(), parts[1].trim().toIntOrNull() ?: targetPort)
        } else {
            Pair(targetIp.trim(), null)
        }

        // Auto port fallback: if port not specified, try 8765 (PC) then 8766 (Android phone)
        val portsToTry = if (explicitPort != null) {
            listOf(explicitPort)
        } else {
            listOf(targetPort, if (targetPort == 8765) 8766 else 8765)
        }

        // Pack notes (clean plaintext JSON for P2P transfer)
        val notesArr = JSONArray()
        val requiredImages = mutableSetOf<String>()
        for (note in notes) {
            notesArr.put(note.toJson())
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

        val effectivePin = if (!targetPin.isNullOrBlank()) targetPin else prefs.lastDropPin

        val payload = JSONObject().apply {
            put("protocol", PROTOCOL_VERSION)
            put("pin", effectivePin)
            put("token", effectivePin)
            put("sender_name", "Android Phone")
            put("sender_ip", getLocalWifiIp())
            put("sender_port", DROP_PORT)
            put("notes", notesArr)
            put("images", imagesArr)
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
                    // Auto-record peer on success
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

    data class DiscoveredPeer(val name: String, val ip: String, val port: Int)

    suspend fun pairWithPeer(targetIp: String, targetPort: Int = 8765, pin: String? = null): Result<DiscoveredPeer> = withContext(Dispatchers.IO) {
        val cleanIp = if (targetIp.contains(":")) targetIp.split(":")[0].trim() else targetIp.trim()
        val port = if (targetIp.contains(":")) targetIp.split(":")[1].trim().toIntOrNull() ?: targetPort else targetPort
        val url = URL("http://$cleanIp:$port/aano/pair")
        try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = JSONObject().apply {
                put("name", "Android Phone")
                put("ip", getLocalWifiIp())
                put("port", DROP_PORT)
                put("pin", pin ?: "")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val resp = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val peerName = resp.optString("name", "Desktop PC")
                prefs.lastDropIp = if (port == 8765) cleanIp else "$cleanIp:$port"
                prefs.lastDropPeerName = peerName
                prefs.lastDropPin = pin ?: ""
                Result.success(DiscoveredPeer(peerName, cleanIp, port))
            } else {
                Result.failure(Exception("Peer rejected pair: HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun discoverNearbyPeers(): List<DiscoveredPeer> = withContext(Dispatchers.IO) {
        val myIp = getLocalWifiIp()
        if (myIp == "127.0.0.1") return@withContext emptyList()
        val found = mutableListOf<DiscoveredPeer>()
        val seen = mutableSetOf<String>()

        // 1. Ultra-fast UDP broadcast probe (<400ms)
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifiManager?.createMulticastLock("aano_drop_discovery")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            val udpSock = DatagramSocket().apply {
                broadcast = true
                soTimeout = 350
            }
            val pingMsg = JSONObject().apply {
                put("action", "ping")
                put("app", "aano")
                put("device", Build.MODEL)
            }.toString().toByteArray(Charsets.UTF_8)

            val bcastAddr = InetAddress.getByName("255.255.255.255")
            udpSock.send(DatagramPacket(pingMsg, pingMsg.size, bcastAddr, DISCOVERY_PORT))
            if (myIp.contains(".")) {
                try {
                    val subnetAddr = InetAddress.getByName(myIp.substringBeforeLast(".") + ".255")
                    udpSock.send(DatagramPacket(pingMsg, pingMsg.size, subnetAddr, DISCOVERY_PORT))
                } catch (e: Exception) {
                    Log.d(TAG, "Subnet bcast notice: ${e.message}")
                }
            }

            val buf = ByteArray(2048)
            val startT = System.currentTimeMillis()
            while (System.currentTimeMillis() - startT < 350) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    udpSock.receive(packet)
                    val host = packet.address?.hostAddress ?: ""
                    if (host.isEmpty() || host == myIp || host == "127.0.0.1") continue
                    val respStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val json = JSONObject(respStr)
                    if (json.optString("app") == "aano" && json.optString("action") == "pong") {
                        val port = json.optInt("port", 8765)
                        val devName = json.optString("device", host)
                        val key = "$host:$port"
                        if (seen.add(key)) {
                            found.add(DiscoveredPeer(devName, host, port))
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    break
                }
            }
            udpSock.close()
            try {
                lock?.release()
            } catch (e: Exception) {
                Log.d(TAG, "MulticastLock release notice: ${e.message}")
            }

            if (found.isNotEmpty()) {
                Log.i(TAG, "Fast UDP peer discovery found ${found.size} devices")
                return@withContext found
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fast UDP discovery skipped: ${e.message}")
        }

        // 2. Subnet scan fallback
        val prefix = myIp.substringBeforeLast(".") + "."
        coroutineScope {
            val jobs = mutableListOf<Deferred<Unit>>()
            for (i in 1..254) {
                val host = "$prefix$i"
                if (host == myIp) continue
                for (port in listOf(8765, 8766)) {
                    val d = async {
                        try {
                            val conn = (URL("http://$host:$port/aano/info").openConnection() as HttpURLConnection).apply {
                                connectTimeout = 450
                                readTimeout = 600
                            }
                            if (conn.responseCode in 200..299) {
                                val text = conn.inputStream.bufferedReader().use { it.readText() }
                                val json = JSONObject(text)
                                if (json.optString("protocol") == PROTOCOL_VERSION) {
                                    val name = json.optString("device", json.optString("hostname", "Device"))
                                    val key = "$host:$port"
                                    synchronized(found) {
                                        if (seen.add(key)) {
                                            found.add(DiscoveredPeer(name, host, port))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Radar probe timeout/skip for $host:$port: ${e.message}")
                        }
                        Unit
                    }
                    jobs.add(d)
                }
            }
            jobs.forEach { it.await() }
        }
        found
    }
}
