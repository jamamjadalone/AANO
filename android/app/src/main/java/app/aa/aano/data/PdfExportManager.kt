// PdfExportManager: Native Android PdfDocument export without external libraries.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfExportManager(private val context: Context) {

    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun exportNoteToPdf(note: Note): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // Standard A4 width in PostScript points (72 dpi)
        val pageHeight = 842 // Standard A4 height in points
        val margin = 40f
        val maxWidth = pageWidth - (margin * 2)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        var currentY = margin + 20f

        val titlePaint = Paint().apply {
            color = Color.rgb(0, 77, 64) // Brand deep teal
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139) // Slate muted
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val contentPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        fun drawFooter() {
            canvas.drawText("Exported from AANO Notes • Page $pageNumber", pageWidth / 2f, pageHeight - 20f, footerPaint)
        }

        fun startNewPage() {
            drawFooter()
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            currentY = margin + 20f
        }

        // 1. Draw Title
        val titleText = if (note.title.isNotBlank()) note.title else "Untitled Note"
        canvas.drawText(titleText, margin, currentY, titlePaint)
        currentY += 22f

        // 2. Draw Metadata
        val sdf = SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(if (note.updatedAt > 0) note.updatedAt else note.createdAt))
        val metaText = "Folder: ${note.folder.ifBlank { "General" }} | $dateStr"
        canvas.drawText(metaText, margin, currentY, metaPaint)
        currentY += 16f

        // Separator line
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, linePaint)
        currentY += 20f

        // 3. Draw Voice Memo Card if present
        if (!note.audioUri.isNullOrBlank() || note.audioDuration > 0L) {
            val cardBg = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val cardBorder = Paint().apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }
            val audioTitlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val audioSubPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 9f
                isAntiAlias = true
            }

            val cardH = 34f
            val cardRect = RectF(margin, currentY, margin + 240f, currentY + cardH)
            canvas.drawRoundRect(cardRect, 8f, 8f, cardBg)
            canvas.drawRoundRect(cardRect, 8f, 8f, cardBorder)

            val durStr = formatDuration(note.audioDuration)
            canvas.drawText("🎙️ Voice Memo ($durStr)", margin + 12f, currentY + 15f, audioTitlePaint)
            val audioFn = if (!note.audioUri.isNullOrBlank()) File(note.audioUri!!).name else "voice_memo.m4a"
            canvas.drawText(audioFn, margin + 12f, currentY + 27f, audioSubPaint)
            currentY += cardH + 16f
        }

        // 4. Draw Body Text
        val plainContent = note.content.replace("<br>", "\n")
            .replace("</div>", "\n")
            .replace("</p>", "\n\n")
            .replace(Regex("<[^>]*>"), "")
            .trim()

        for (paragraph in plainContent.lines()) {
            if (paragraph.isBlank()) {
                currentY += 10f
                if (currentY > pageHeight - margin - 40f) startNewPage()
                continue
            }
            val words = paragraph.split(" ")
            var line = ""
            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val measure = contentPaint.measureText(testLine)
                if (measure > maxWidth) {
                    if (currentY > pageHeight - margin - 40f) startNewPage()
                    canvas.drawText(line, margin, currentY, contentPaint)
                    currentY += 16f
                    line = word
                } else {
                    line = testLine
                }
            }
            if (line.isNotEmpty()) {
                if (currentY > pageHeight - margin - 40f) startNewPage()
                canvas.drawText(line, margin, currentY, contentPaint)
                currentY += 18f
            }
        }

        // 5. Draw Attached Images
        if (note.images.isNotEmpty()) {
            val sectionPaint = Paint().apply {
                color = Color.rgb(0, 77, 64)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            if (currentY > pageHeight - margin - 120f) startNewPage()
            currentY += 15f
            canvas.drawText("Attached Photos (${note.images.size})", margin, currentY, sectionPaint)
            currentY += 12f

            for (imgPath in note.images) {
                val imgFile = File(imgPath)
                if (!imgFile.exists() || imgFile.length() == 0L) continue

                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(imgFile.absolutePath, opts)
                    var sample = 1
                    while (opts.outWidth / sample > 1200 || opts.outHeight / sample > 1200) {
                        sample *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath, decodeOpts)
                    if (bmp != null) {
                        val maxImgH = 260f
                        val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                        val drawW = minOf(maxWidth, maxImgH * ratio)
                        val drawH = drawW / ratio

                        if (currentY + drawH > pageHeight - margin - 30f) {
                            startNewPage()
                        }

                        val scaled = Bitmap.createScaledBitmap(bmp, drawW.toInt(), drawH.toInt(), true)
                        canvas.drawBitmap(scaled, margin, currentY, null)
                        currentY += drawH + 16f
                    }
                } catch (_: Exception) {}
            }
        }

        drawFooter()
        pdfDocument.finishPage(page)

        val cleanTitle = note.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30).ifBlank { "Note" }
        val outFile = File(context.cacheDir, "AANO_${cleanTitle}_${System.currentTimeMillis()}.pdf")
        return try {
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            outFile
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }
}
