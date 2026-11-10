// Peer discovery, UDP beacon broadcaster, and P2P pairing for AANO Drop.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.drop

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import app.aa.aano.data.Prefs
import app.aa.aano.drop.AanoDropManager.DiscoveredPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class DropRadar(private val context: Context, private val prefs: Prefs) {

    companion object {
        private const val TAG = "DropRadar"
        const val DISCOVERY_PORT = 8767
        const val DROP_PORT = 8766
        const val PROTOCOL_VERSION = "aano-drop-v1"
    }

    private var udpBeaconSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startUdpBeacon(isRunning: () -> Boolean, getLocalWifiIp: () -> String) {
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
                while (isRunning()) {
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
                        if (isRunning()) Log.d(TAG, "UDP beacon loop notice: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start UDP beacon on port $DISCOVERY_PORT: ${e.message}")
            } finally {
                stopUdpBeacon()
            }
        }
    }

    fun stopUdpBeacon() {
        try {
            udpBeaconSocket?.close()
            udpBeaconSocket = null
        } catch (e: Exception) {
            Log.d(TAG, "Beacon socket close notice: ${e.message}")
        }
        try {
            multicastLock?.release()
            multicastLock = null
        } catch (e: Exception) {
            Log.d(TAG, "MulticastLock release notice: ${e.message}")
        }
    }

    suspend fun pairWithPeer(targetIp: String, targetPort: Int = 8765, pin: String? = null, myIp: String): Result<DiscoveredPeer> {
        val cleanIp = if (targetIp.contains(":")) targetIp.split(":")[0].trim() else targetIp.trim()
        val port = if (targetIp.contains(":")) targetIp.split(":")[1].trim().toIntOrNull() ?: targetPort else targetPort
        val url = URL("http://$cleanIp:$port/aano/pair")
        return try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = JSONObject().apply {
                put("name", "Android Phone")
                put("ip", myIp)
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

    suspend fun discoverNearbyPeers(myIp: String): List<DiscoveredPeer> {
        if (myIp == "127.0.0.1") return emptyList()
        val found = mutableListOf<DiscoveredPeer>()
        val seen = mutableSetOf<String>()

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
                return found
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fast UDP discovery skipped: ${e.message}")
        }

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
        return found
    }
}
