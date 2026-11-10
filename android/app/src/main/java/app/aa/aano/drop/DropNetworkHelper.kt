// Network state, hotspot utilities, and QR bitmap generation for AANO Drop.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.drop

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.provider.Settings
import android.util.Log
import app.aa.aano.drop.AanoDropManager.DropNetworkMode
import app.aa.aano.drop.AanoDropManager.DropNetworkState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.Inet4Address
import java.net.NetworkInterface

object DropNetworkHelper {

    private const val TAG = "DropNetwork"

    fun getNetworkState(context: Context): DropNetworkState {
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

    fun lockProcessToWifi(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (getNetworkState(context).mode == DropNetworkMode.WIFI_CONNECTED) {
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
            } catch (ex: Exception) {
                Log.d(TAG, "Fallback QR encode notice: ${ex.message}")
                null
            }
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
}
