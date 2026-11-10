// EditorMediaHelper: Handles copying, downsampling and compressing picked images into app storage
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

object EditorMediaHelper {

    fun copyUriToLocalMedia(context: Context, uri: Uri, isHdQuality: Boolean = false): String? {
        return try {
            val mediaDir = File(context.filesDir, "media/images").apply { mkdirs() }
            if (isHdQuality) {
                val destFile = File(mediaDir, "img_${System.currentTimeMillis()}_${(100..999).random()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.absolutePath
            } else {
                val destFile = File(mediaDir, "img_${System.currentTimeMillis()}_${(100..999).random()}.webp")
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, boundsOpts)
                }
                val origW = boundsOpts.outWidth
                val origH = boundsOpts.outHeight
                val maxDim = 1280
                var sampleSize = 1
                if (origW > maxDim || origH > maxDim) {
                    val halfW = origW / 2
                    val halfH = origH / 2
                    while ((halfW / sampleSize) >= maxDim || (halfH / sampleSize) >= maxDim) {
                        sampleSize *= 2
                    }
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOpts)
                }
                if (decoded != null) {
                    val width = decoded.width
                    val height = decoded.height
                    val scaled = if (width > maxDim || height > maxDim) {
                        val ratio = width.toFloat() / height.toFloat()
                        val res = if (width > height) {
                            Bitmap.createScaledBitmap(decoded, maxDim, (maxDim / ratio).toInt().coerceAtLeast(1), true)
                        } else {
                            Bitmap.createScaledBitmap(decoded, (maxDim * ratio).toInt().coerceAtLeast(1), maxDim, true)
                        }
                        if (res != decoded) decoded.recycle()
                        res
                    } else {
                        decoded
                    }
                    destFile.outputStream().use { out ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
                        } else {
                            @Suppress("DEPRECATION")
                            scaled.compress(Bitmap.CompressFormat.WEBP, 80, out)
                        }
                    }
                    scaled.recycle()
                    destFile.absolutePath
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("AANO", "copyUriToLocalMedia error: ${e.message}", e)
            null
        }
    }
}
