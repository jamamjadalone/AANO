package app.aa.aano.components.views

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

    private var activeTouchMode = CropTouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchRadius = 32f * resources.displayMetrics.density
    private val minCropSize = 48f * resources.displayMetrics.density

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

        canvas.drawBitmap(bmp, null, imageBounds, null)
        if (cropRect.isEmpty) return

        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, maskPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), maskPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, maskPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, maskPaint)

        canvas.drawRect(cropRect, borderPaint)

        val stepX = cropRect.width() / 3f
        val stepY = cropRect.height() / 3f
        canvas.drawLine(cropRect.left + stepX, cropRect.top, cropRect.left + stepX, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + stepX * 2, cropRect.top, cropRect.left + stepX * 2, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + stepY, cropRect.right, cropRect.top + stepY, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + stepY * 2, cropRect.right, cropRect.top + stepY * 2, gridPaint)

        val handleLen = 18f * resources.displayMetrics.density
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + handleLen, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + handleLen, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right - handleLen, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + handleLen, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + handleLen, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left, cropRect.bottom - handleLen, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.bottom, cropRect.right - handleLen, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.bottom, cropRect.right, cropRect.bottom - handleLen, cornerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeTouchMode = CropMathHelper.detectTouchMode(x, y, cropRect, touchRadius)
                lastTouchX = x
                lastTouchY = y
                return activeTouchMode != CropTouchMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTouchMode == CropTouchMode.NONE) return false
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                CropMathHelper.applyDrag(activeTouchMode, dx, dy, cropRect, imageBounds, targetRatio, minCropSize)
                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeTouchMode = CropTouchMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = sourceBitmap ?: return null
        return CropMathHelper.cropAndScaleBitmap(bmp, cropRect, imageBounds)
    }
}
