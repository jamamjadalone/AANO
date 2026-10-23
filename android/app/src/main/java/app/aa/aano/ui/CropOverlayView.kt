package app.aa.aano.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private val imageBounds = RectF()
    private val cropRect = RectF()

    private var targetRatio: Float? = null // null means Freeform

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#55FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private var activeTouchMode = TouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchRadius = 32f * resources.displayMetrics.density
    private val minCropSize = 48f * resources.displayMetrics.density

    enum class TouchMode {
        NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    fun setBitmap(bitmap: Bitmap) {
        this.sourceBitmap = bitmap
        calculateLayout()
        resetCropRect()
        invalidate()
    }

    fun setAspectRatio(ratio: Float?) {
        targetRatio = ratio
        applyCurrentRatio()
        invalidate()
    }

    fun rotate90() {
        sourceBitmap?.let { bmp ->
            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            sourceBitmap = rotated
            calculateLayout()
            resetCropRect()
            invalidate()
        }
    }

    private fun calculateLayout() {
        val bmp = sourceBitmap ?: return
        if (width == 0 || height == 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val scale = min((viewW - 32) / bmpW, (viewH - 32) / bmpH)
        val drawnW = bmpW * scale
        val drawnH = bmpH * scale
        val left = (viewW - drawnW) / 2f
        val top = (viewH - drawnH) / 2f

        imageBounds.set(left, top, left + drawnW, top + drawnH)
    }

    private fun resetCropRect() {
        if (imageBounds.isEmpty) return
        val insetX = imageBounds.width() * 0.08f
        val insetY = imageBounds.height() * 0.08f
        cropRect.set(
            imageBounds.left + insetX,
            imageBounds.top + insetY,
            imageBounds.right - insetX,
            imageBounds.bottom - insetY
        )
        applyCurrentRatio()
    }

    private fun applyCurrentRatio() {
        val ratio = targetRatio ?: return
        if (cropRect.isEmpty) return

        val curW = cropRect.width()
        val curH = cropRect.height()

        var newW = curW
        var newH = curW / ratio

        if (newH > imageBounds.height()) {
            newH = imageBounds.height()
            newW = newH * ratio
        }
        if (newW > imageBounds.width()) {
            newW = imageBounds.width()
            newH = newW / ratio
        }

        val cx = cropRect.centerX()
        val cy = cropRect.centerY()

        cropRect.left = (cx - newW / 2f).coerceAtLeast(imageBounds.left)
        cropRect.right = (cropRect.left + newW).coerceAtMost(imageBounds.right)
        cropRect.top = (cy - newH / 2f).coerceAtLeast(imageBounds.top)
        cropRect.bottom = (cropRect.top + newH).coerceAtMost(imageBounds.bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout()
        resetCropRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return

        // 1. Draw image
        canvas.drawBitmap(bmp, null, imageBounds, null)

        if (cropRect.isEmpty) return

        // 2. Draw 4 translucent mask rectangles outside cropRect
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, maskPaint) // Top
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), maskPaint) // Bottom
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, maskPaint) // Left
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, maskPaint) // Right

        // 3. Draw crop box border
        canvas.drawRect(cropRect, borderPaint)

        // 4. Draw rule of thirds grid lines
        val stepX = cropRect.width() / 3f
        val stepY = cropRect.height() / 3f
        canvas.drawLine(cropRect.left + stepX, cropRect.top, cropRect.left + stepX, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + stepX * 2, cropRect.top, cropRect.left + stepX * 2, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + stepY, cropRect.right, cropRect.top + stepY, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + stepY * 2, cropRect.right, cropRect.top + stepY * 2, gridPaint)

        // 5. Draw 4 corner handles
        val handleLen = 18f * resources.displayMetrics.density
        // Top-Left
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + handleLen, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + handleLen, cornerPaint)
        // Top-Right
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right - handleLen, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + handleLen, cornerPaint)
        // Bottom-Left
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + handleLen, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left, cropRect.bottom - handleLen, cornerPaint)
        // Bottom-Right
        canvas.drawLine(cropRect.right, cropRect.bottom, cropRect.right - handleLen, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.bottom, cropRect.right, cropRect.bottom - handleLen, cornerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeTouchMode = detectTouchMode(x, y)
                lastTouchX = x
                lastTouchY = y
                return activeTouchMode != TouchMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTouchMode == TouchMode.NONE) return false
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                handleDrag(dx, dy)
                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeTouchMode = TouchMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun detectTouchMode(x: Float, y: Float): TouchMode {
        if (abs(x - cropRect.left) < touchRadius && abs(y - cropRect.top) < touchRadius) {
            return TouchMode.TOP_LEFT
        }
        if (abs(x - cropRect.right) < touchRadius && abs(y - cropRect.top) < touchRadius) {
            return TouchMode.TOP_RIGHT
        }
        if (abs(x - cropRect.left) < touchRadius && abs(y - cropRect.bottom) < touchRadius) {
            return TouchMode.BOTTOM_LEFT
        }
        if (abs(x - cropRect.right) < touchRadius && abs(y - cropRect.bottom) < touchRadius) {
            return TouchMode.BOTTOM_RIGHT
        }
        if (cropRect.contains(x, y)) {
            return TouchMode.MOVE
        }
        return TouchMode.NONE
    }

    private fun handleDrag(dx: Float, dy: Float) {
        val ratio = targetRatio
        when (activeTouchMode) {
            TouchMode.MOVE -> {
                var adjDx = dx
                var adjDy = dy
                if (cropRect.left + adjDx < imageBounds.left) adjDx = imageBounds.left - cropRect.left
                if (cropRect.right + adjDx > imageBounds.right) adjDx = imageBounds.right - cropRect.right
                if (cropRect.top + adjDy < imageBounds.top) adjDy = imageBounds.top - cropRect.top
                if (cropRect.bottom + adjDy > imageBounds.bottom) adjDy = imageBounds.bottom - cropRect.bottom
                cropRect.offset(adjDx, adjDy)
            }
            TouchMode.TOP_LEFT -> {
                var newL = (cropRect.left + dx).coerceIn(imageBounds.left, cropRect.right - minCropSize)
                var newT = (cropRect.top + dy).coerceIn(imageBounds.top, cropRect.bottom - minCropSize)
                if (ratio != null) {
                    val w = cropRect.right - newL
                    val h = w / ratio
                    newT = cropRect.bottom - h
                    if (newT < imageBounds.top) {
                        newT = imageBounds.top
                        newL = cropRect.right - (cropRect.bottom - newT) * ratio
                    }
                }
                cropRect.left = newL
                cropRect.top = newT
            }
            TouchMode.TOP_RIGHT -> {
                var newR = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, imageBounds.right)
                var newT = (cropRect.top + dy).coerceIn(imageBounds.top, cropRect.bottom - minCropSize)
                if (ratio != null) {
                    val w = newR - cropRect.left
                    val h = w / ratio
                    newT = cropRect.bottom - h
                    if (newT < imageBounds.top) {
                        newT = imageBounds.top
                        newR = cropRect.left + (cropRect.bottom - newT) * ratio
                    }
                }
                cropRect.right = newR
                cropRect.top = newT
            }
            TouchMode.BOTTOM_LEFT -> {
                var newL = (cropRect.left + dx).coerceIn(imageBounds.left, cropRect.right - minCropSize)
                var newB = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, imageBounds.bottom)
                if (ratio != null) {
                    val w = cropRect.right - newL
                    val h = w / ratio
                    newB = cropRect.top + h
                    if (newB > imageBounds.bottom) {
                        newB = imageBounds.bottom
                        newL = cropRect.right - (newB - cropRect.top) * ratio
                    }
                }
                cropRect.left = newL
                cropRect.bottom = newB
            }
            TouchMode.BOTTOM_RIGHT -> {
                var newR = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, imageBounds.right)
                var newB = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, imageBounds.bottom)
                if (ratio != null) {
                    val w = newR - cropRect.left
                    val h = w / ratio
                    newB = cropRect.top + h
                    if (newB > imageBounds.bottom) {
                        newB = imageBounds.bottom
                        newR = cropRect.left + (newB - cropRect.top) * ratio
                    }
                }
                cropRect.right = newR
                cropRect.bottom = newB
            }
            TouchMode.NONE -> {}
        }
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = sourceBitmap ?: return null
        if (cropRect.isEmpty || imageBounds.isEmpty) return null

        val scaleX = bmp.width.toFloat() / imageBounds.width()
        val scaleY = bmp.height.toFloat() / imageBounds.height()

        val cropX = ((cropRect.left - imageBounds.left) * scaleX).toInt().coerceIn(0, bmp.width - 1)
        val cropY = ((cropRect.top - imageBounds.top) * scaleY).toInt().coerceIn(0, bmp.height - 1)
        val cropW = (cropRect.width() * scaleX).toInt().coerceIn(1, bmp.width - cropX)
        val cropH = (cropRect.height() * scaleY).toInt().coerceIn(1, bmp.height - cropY)

        val rawCropped = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)

        // Constrain max dimension to 1280px preserving aspect ratio
        val maxDim = 1280
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
