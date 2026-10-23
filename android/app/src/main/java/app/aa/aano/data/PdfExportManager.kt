// PdfExportManager: Native Android PdfDocument export without external libraries.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfExportManager(private val context: Context) {

    fun exportNoteToPdf(note: Note): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // Standard A4 width in PostScript points (72 dpi)
        val pageHeight = 842 // Standard A4 height in points
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Background
        canvas.drawColor(Color.WHITE)

        val margin = 40f
        var currentY = margin + 20f

        // Title Paint
        val titlePaint = Paint().apply {
            color = Color.rgb(0, 77, 64) // Brand deep teal
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Subtitle / Date Paint
        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139) // Slate muted
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        // Content Paint
        val contentPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        // 1. Draw Title
        val titleText = if (note.title.isNotBlank()) note.title else "Untitled Note"
        canvas.drawText(titleText, margin, currentY, titlePaint)
        currentY += 22f

        // 2. Draw Metadata (Folder & Date)
        val sdf = SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(if (note.updatedAt > 0) note.updatedAt else note.createdAt))
        val metaText = "Folder: ${note.folder} | $dateStr"
        canvas.drawText(metaText, margin, currentY, metaPaint)
        currentY += 16f

        // Separator line
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, linePaint)
        currentY += 24f

        // 3. Draw Body Text (Multi-line word wrap)
        val plainContent = note.content.replace("<br>", "\n")
            .replace(Regex("<[^>]*>"), "") // strip HTML tags
        val maxWidth = pageWidth - (margin * 2)

        for (paragraph in plainContent.lines()) {
            if (paragraph.isBlank()) {
                currentY += 12f
                continue
            }
            val words = paragraph.split(" ")
            var line = ""
            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val measure = contentPaint.measureText(testLine)
                if (measure > maxWidth) {
                    canvas.drawText(line, margin, currentY, contentPaint)
                    currentY += 16f
                    line = word
                    if (currentY > pageHeight - margin - 40f) break
                } else {
                    line = testLine
                }
            }
            if (line.isNotEmpty() && currentY <= pageHeight - margin - 40f) {
                canvas.drawText(line, margin, currentY, contentPaint)
                currentY += 18f
            }
            if (currentY > pageHeight - margin - 40f) break
        }

        // 4. Draw first attached image thumbnail if exists and room remains
        if (note.images.isNotEmpty() && currentY <= pageHeight - margin - 150f) {
            val imgFile = File(note.images[0])
            if (imgFile.exists()) {
                try {
                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                    if (bmp != null) {
                        currentY += 10f
                        val maxImgH = 140f
                        val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                        val drawW = minOf(maxWidth, maxImgH * ratio)
                        val drawH = drawW / ratio
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, drawW.toInt(), drawH.toInt(), true)
                        canvas.drawBitmap(scaled, margin, currentY, null)
                    }
                } catch (_: Exception) {}
            }
        }

        // Footer brand watermark
        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Exported from AANO Notes • Private & Encrypted", pageWidth / 2f, pageHeight - 20f, footerPaint)

        pdfDocument.finishPage(page)

        val cleanTitle = note.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
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
