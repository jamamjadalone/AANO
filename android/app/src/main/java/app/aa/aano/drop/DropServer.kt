// Inbound P2P HTTP server and request routing engine for AANO Drop.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.drop

import android.content.Context
import android.util.Log
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class DropServer(
    private val context: Context,
    private val repository: NoteRepository,
    private val prefs: Prefs
) {

    companion object {
        private const val TAG = "DropServer"
        const val DROP_PORT = 8766
        const val PROTOCOL_VERSION = "aano-drop-v1"
    }

    private var serverSocket: ServerSocket? = null
    var isRunning = false
        private set

    fun start(
        pin: String,
        token: String,
        getLocalWifiIp: () -> String,
        onNotesReceived: ((Int, Int) -> Unit)?
    ) {
        if (isRunning) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DropNetworkHelper.lockProcessToWifi(context)
                serverSocket = ServerSocket(DROP_PORT)
                isRunning = true
                Log.i(TAG, "AANO Drop Android server started on port $DROP_PORT with PIN $pin")

                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client, pin, token, getLocalWifiIp, onNotesReceived)
                    } catch (e: Exception) {
                        if (isRunning) Log.w(TAG, "Server accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start drop server: ${e.message}", e)
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        }
    }

    private fun handleClient(
        client: Socket,
        pin: String,
        token: String,
        getLocalWifiIp: () -> String,
        onNotesReceived: ((Int, Int) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.tcpNoDelay = true
                val input = client.getInputStream()
                val output = client.getOutputStream()

                val headerBytes = ByteArrayOutputStream()
                var p3 = -1
                var p2 = -1
                var p1 = -1
                var b: Int
                while (input.read().also { b = it } != -1) {
                    headerBytes.write(b)
                    if (p3 == '\r'.code && p2 == '\n'.code && p1 == '\r'.code && b == '\n'.code) break
                    p3 = p2
                    p2 = p1
                    p1 = b
                }

                val lines = headerBytes.toString("UTF-8").lines()
                val requestLine = lines.firstOrNull() ?: return@launch
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

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
                    handlePair(input, output, contentLength, client, pin, getLocalWifiIp)
                    return@launch
                }

                if (path.startsWith("/aano/drop") && method.equals("POST", ignoreCase = true)) {
                    handleDrop(input, output, contentLength, client, pin, token, incomingToken, incomingPinHeader, onNotesReceived)
                } else {
                    sendHttpResponse(output, 404, "Not Found", "text/plain", "Endpoint not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling drop client: ${e.message}", e)
            } finally {
                try { client.close() } catch (e: Exception) { Log.d(TAG, "Client close notice: ${e.message}") }
            }
        }
    }

    private fun handlePair(input: java.io.InputStream, output: OutputStream, contentLength: Int, client: Socket, pin: String, getLocalWifiIp: () -> String) {
        if (contentLength <= 0 || contentLength > 64 * 1024) {
            sendHttpResponse(output, 400, "Bad Request", "application/json", "{\"success\":false,\"error\":\"Invalid pair payload length\"}")
            return
        }
        val bodyBytes = readBytes(input, contentLength)
        val payload = JSONObject(String(bodyBytes, Charsets.UTF_8))
        val incomingPin = payload.optString("pin")
        if (pin.isNotEmpty() && (incomingPin.isEmpty() || pin != incomingPin)) {
            sendHttpResponse(output, 403, "Forbidden", "application/json", "{\"success\":false,\"error\":\"Invalid or missing PIN\"}")
            return
        }
        val senderName = payload.optString("name", "Desktop PC")
        val senderIp = payload.optString("ip").ifEmpty { client.inetAddress?.hostAddress ?: "" }
        val senderPort = payload.optInt("port", 8765)
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
    }

    private suspend fun handleDrop(
        input: java.io.InputStream,
        output: OutputStream,
        contentLength: Int,
        client: Socket,
        pin: String,
        token: String,
        incomingToken: String,
        incomingPinHeader: String,
        onNotesReceived: ((Int, Int) -> Unit)?
    ) {
        if (contentLength <= 0 || contentLength > 20 * 1024 * 1024) {
            sendHttpResponse(output, 400, "Bad Request", "application/json", "{\"success\":false,\"error\":\"Invalid content length\"}")
            return
        }

        val headerAuth = incomingToken.ifEmpty { incomingPinHeader }
        val clientIp = client.inetAddress?.hostAddress ?: ""
        val isPaired = prefs.lastDropIp.isNotEmpty() && prefs.lastDropIp.startsWith(clientIp)
        if (contentLength > 64 * 1024) {
            val authMatches = (headerAuth.isNotEmpty() && (headerAuth == pin || headerAuth == token)) || isPaired
            if (!authMatches) {
                sendHttpResponse(output, 403, "Forbidden", "application/json", "{\"success\":false,\"error\":\"Missing auth for large payload\"}")
                return
            }
        }

        val bodyBytes = readBytes(input, contentLength)
        val payload = JSONObject(String(bodyBytes, Charsets.UTF_8))

        val senderName = payload.optString("sender_name", "Desktop PC")
        val senderIp = payload.optString("sender_ip").ifEmpty { clientIp }
        val senderPort = payload.optInt("sender_port", 8765)
        if (senderIp.isNotEmpty()) {
            prefs.lastDropIp = if (senderPort == 8765) senderIp else "$senderIp:$senderPort"
            prefs.lastDropPeerName = senderName
        }

        val reqToken = payload.optString("token").ifEmpty { payload.optString("pin", headerAuth) }
        if (pin != reqToken && token != reqToken && !isPaired) {
            sendHttpResponse(output, 403, "Forbidden", "application/json", "{\"success\":false,\"error\":\"Invalid PIN or Token\"}")
            return
        }

        val processResult = DropPayloadProcessor.processIncomingPayload(context, repository, payload, reqToken, pin)
        if (processResult.isFailure) {
            sendHttpResponse(output, 403, "Forbidden", "application/json", "{\"success\":false,\"error\":\"${processResult.exceptionOrNull()?.message}\"}")
            return
        }

        val (savedNotes, savedImages) = processResult.getOrThrow()
        withContext(Dispatchers.Main) {
            onNotesReceived?.invoke(savedNotes, savedImages)
        }

        val respJson = JSONObject().apply {
            put("success", true)
            put("count", savedNotes)
            put("images", savedImages)
        }
        sendHttpResponse(output, 200, "OK", "application/json", respJson.toString())
    }

    private fun readBytes(input: java.io.InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var total = 0
        while (total < length) {
            val r = input.read(buf, total, length - total)
            if (r == -1) break
            total += r
        }
        return buf
    }

    private fun sendHttpResponse(output: OutputStream, code: Int, reason: String, contentType: String, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $reason\r\n" +
                "Content-Type: $contentType; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) output.write(bodyBytes)
        output.flush()
    }
}
