// DropHubViewHelper: View builder for offline hotspot & QR code cards in Drop Hub dialog
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import app.aa.aano.drop.AanoDropManager

object DropHubViewHelper {

    fun buildOfflineCard(
        context: Context,
        density: Float,
        onStartHotspot: () -> Unit,
        onRefreshNetwork: () -> Unit
    ): LinearLayout {
        val offlineCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 14 * density
                setStroke((1 * density).toInt(), Color.parseColor("#F59E0B"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (12 * density).toInt()) }
        }

        offlineCard.addView(TextView(context).apply {
            text = "📡 Wi-Fi Router Not Connected"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        offlineCard.addView(TextView(context).apply {
            text = "Direct Hotspot Mode (Offline Transfer):\n1. Is phone ka Hotspot on karein.\n2. Doosra phone is Hotspot se connect karein.\n3. Notes direct bina internet transfer karein!"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
        })

        offlineCard.addView(MaterialButton(context).apply {
            text = "🔥 Turn On Hotspot (Offline Mode)"
            setBackgroundColor(Color.parseColor("#D97706"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (8 * density).toInt()) }
            setOnClickListener { onStartHotspot() }
        })

        offlineCard.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "🔄 Refresh Network Status"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onRefreshNetwork() }
        })

        return offlineCard
    }

    fun buildQrCard(
        context: Context,
        density: Float,
        qrBmp: android.graphics.Bitmap,
        targetPx: Int
    ): LinearLayout {
        val qrCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 16 * density
                setStroke((2 * density).toInt(), Color.parseColor("#E2E8F0"))
            }
            val padBox = (12 * density).toInt()
            setPadding(padBox, padBox, padBox, padBox)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (10 * density).toInt()) }
        }
        qrCard.addView(ImageView(context).apply {
            setImageBitmap(qrBmp)
            layoutParams = LinearLayout.LayoutParams(targetPx, targetPx)
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        return qrCard
    }
}
