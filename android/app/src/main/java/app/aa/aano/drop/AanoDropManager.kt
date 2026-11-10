// AANO Drop - Bidirectional Local Wi-Fi P2P Transfer Engine for Android (Facade).
// Delegates to DropNetworkHelper, DropRadar, DropClient, and DropServer.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.drop

import android.content.Context
import android.graphics.Bitmap
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import kotlin.random.Random

class AanoDropManager(private val context: Context) {

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

    data class DiscoveredPeer(val name: String, val ip: String, val port: Int)

    companion object {
        const val DROP_PORT = 8766
        const val DISCOVERY_PORT = 8767
        const val PROTOCOL_VERSION = "aano-drop-v1"

        @Volatile
        private var instance: AanoDropManager? = null

        fun getInstance(context: Context): AanoDropManager {
            return instance ?: synchronized(this) {
                instance ?: AanoDropManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val repository = NoteRepository(context)
    private val prefs = Prefs(context)
    private val radar = DropRadar(context, prefs)
    private val client = DropClient(context, prefs)
    private val server = DropServer(context, repository, prefs)

    val pin = String.format("%06d", Random.nextInt(100000, 999999))
    val token = "drop_android_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"

    var onNotesReceivedListener: ((count: Int, images: Int) -> Unit)? = null

    fun startServer() {
        if (server.isRunning) return
        server.start(
            pin = pin,
            token = token,
            getLocalWifiIp = { getLocalWifiIp() },
            onNotesReceived = { count, images -> onNotesReceivedListener?.invoke(count, images) }
        )
        radar.startUdpBeacon(
            isRunning = { server.isRunning },
            getLocalWifiIp = { getLocalWifiIp() }
        )
    }

    fun stopServer() {
        radar.stopUdpBeacon()
        server.stop()
    }

    fun getNetworkState(): DropNetworkState {
        return DropNetworkHelper.getNetworkState(context)
    }

    fun getLocalWifiIp(): String {
        return getNetworkState().ip
    }

    fun generateQrBitmap(contents: String, sizePx: Int = 512): Bitmap? {
        return DropNetworkHelper.generateQrBitmap(contents, sizePx)
    }

    fun openHotspotSettings(activityContext: Context) {
        DropNetworkHelper.openHotspotSettings(activityContext)
    }

    fun openWifiSettings(activityContext: Context) {
        DropNetworkHelper.openWifiSettings(activityContext)
    }

    suspend fun sendDropToDevice(
        targetIp: String,
        targetPort: Int = 8765,
        notes: List<Note>,
        targetPin: String? = null
    ): Result<String> {
        return client.sendDropToDevice(targetIp, targetPort, notes, targetPin, getLocalWifiIp())
    }

    suspend fun pairWithPeer(targetIp: String, targetPort: Int = 8765, pin: String? = null): Result<DiscoveredPeer> {
        return radar.pairWithPeer(targetIp, targetPort, pin, getLocalWifiIp())
    }

    suspend fun discoverNearbyPeers(): List<DiscoveredPeer> {
        return radar.discoverNearbyPeers(getLocalWifiIp())
    }
}
