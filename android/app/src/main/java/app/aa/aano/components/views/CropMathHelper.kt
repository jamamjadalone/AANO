package app.aa.aano.components.views

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max

enum class CropTouchMode {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

object CropMathHelper {

    fun detectTouchMode(x: Float, y: Float, cropRect: RectF, touchRadius: Float): CropTouchMode {
        if (abs(x - cropRect.left) < touchRadius && abs(y - cropRect.top) < touchRadius) {
            return CropTouchMode.TOP_LEFT
        }
        if (abs(x - cropRect.right) < touchRadius && abs(y - cropRect.top) < touchRadius) {
            return CropTouchMode.TOP_RIGHT
        }
        if (abs(x - cropRect.left) < touchRadius && abs(y - cropRect.bottom) < touchRadius) {
            return CropTouchMode.BOTTOM_LEFT
        }
        if (abs(x - cropRect.right) < touchRadius && abs(y - cropRect.bottom) < touchRadius) {
            return CropTouchMode.BOTTOM_RIGHT
        }
        if (cropRect.contains(x, y)) {
            return CropTouchMode.MOVE
        }
        return CropTouchMode.NONE
    }

    fun applyDrag(
        mode: CropTouchMode,
        dx: Float,
        dy: Float,
        cropRect: RectF,
        imageBounds: RectF,
        targetRatio: Float?,
        minCropSize: Float
    ) {
        when (mode) {
            CropTouchMode.MOVE -> {
                var adjDx = dx
                var adjDy = dy
                if (cropRect.left + adjDx < imageBounds.left) adjDx = imageBounds.left - cropRect.left
                if (cropRect.right + adjDx > imageBounds.right) adjDx = imageBounds.right - cropRect.right
                if (cropRect.top + adjDy < imageBounds.top) adjDy = imageBounds.top - cropRect.top
                if (cropRect.bottom + adjDy > imageBounds.bottom) adjDy = imageBounds.bottom - cropRect.bottom
                cropRect.offset(adjDx, adjDy)
            }
            CropTouchMode.TOP_LEFT -> {
                var newL = (cropRect.left + dx).coerceIn(imageBounds.left, cropRect.right - minCropSize)
                var newT = (cropRect.top + dy).coerceIn(imageBounds.top, cropRect.bottom - minCropSize)
                if (targetRatio != null) {
                    val w = cropRect.right - newL
                    val h = w / targetRatio
                    newT = cropRect.bottom - h
                    if (newT < imageBounds.top) {
                        newT = imageBounds.top
                        newL = cropRect.right - (cropRect.bottom - newT) * targetRatio
                    }
                }
                cropRect.left = newL
                cropRect.top = newT
            }
            CropTouchMode.TOP_RIGHT -> {
                var newR = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, imageBounds.right)
                var newT = (cropRect.top + dy).coerceIn(imageBounds.top, cropRect.bottom - minCropSize)
                if (targetRatio != null) {
                    val w = newR - cropRect.left
                    val h = w / targetRatio
                    newT = cropRect.bottom - h
                    if (newT < imageBounds.top) {
                        newT = imageBounds.top
                        newR = cropRect.left + (cropRect.bottom - newT) * targetRatio
                    }
                }
                cropRect.right = newR
                cropRect.top = newT
            }
            CropTouchMode.BOTTOM_LEFT -> {
                var newL = (cropRect.left + dx).coerceIn(imageBounds.left, cropRect.right - minCropSize)
                var newB = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, imageBounds.bottom)
                if (targetRatio != null) {
                    val w = cropRect.right - newL
                    val h = w / targetRatio
                    newB = cropRect.top + h
                    if (newB > imageBounds.bottom) {
                        newB = imageBounds.bottom
                        newL = cropRect.right - (newB - cropRect.top) * targetRatio
                    }
                }
                cropRect.left = newL
                cropRect.bottom = newB
            }
            CropTouchMode.BOTTOM_RIGHT -> {
                var newR = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, imageBounds.right)
                var newB = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, imageBounds.bottom)
                if (targetRatio != null) {
                    val w = newR - cropRect.left
                    val h = w / targetRatio
                    newB = cropRect.top + h
                    if (newB > imageBounds.bottom) {
                        newB = imageBounds.bottom
                        newR = cropRect.left + (newB - cropRect.top) * targetRatio
                    }
                }
                cropRect.right = newR
                cropRect.bottom = newB
            }
            CropTouchMode.NONE -> {}
        }
    }

    fun cropAndScaleBitmap(bmp: Bitmap, cropRect: RectF, imageBounds: RectF, maxDim: Int = 1280): Bitmap? {
        if (cropRect.isEmpty || imageBounds.isEmpty) return null

        val scaleX = bmp.width.toFloat() / imageBounds.width()
        val scaleY = bmp.height.toFloat() / imageBounds.height()

        val cropX = ((cropRect.left - imageBounds.left) * scaleX).toInt().coerceIn(0, bmp.width - 1)
        val cropY = ((cropRect.top - imageBounds.top) * scaleY).toInt().coerceIn(0, bmp.height - 1)
        val cropW = (cropRect.width() * scaleX).toInt().coerceIn(1, bmp.width - cropX)
        val cropH = (cropRect.height() * scaleY).toInt().coerceIn(1, bmp.height - cropY)

        val rawCropped = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)
        return if (rawCropped.width > maxDim || rawCropped.height > maxDim) {
            val ratio = rawCropped.width.toFloat() / rawCropped.height.toFloat()
            val targetW = if (ratio >= 1f) maxDim else max(1, (maxDim * ratio).toInt())
            val targetH = if (ratio >= 1f) max(1, (maxDim / ratio).toInt()) else maxDim
            Bitmap.createScaledBitmap(rawCropped, targetW, targetH, true)
        } else {
            rawCropped
        }
    }
}
