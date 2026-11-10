// EditorExportHelper: Handles exporting notes to TXT, MD, PDF, ZIP, and .aano packages
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import app.aa.aano.data.Note
import app.aa.aano.data.NoteBlockParser
import app.aa.aano.data.PdfExportManager

object EditorExportHelper {

    fun showExportDialog(context: Context, note: Note, cacheDir: File) {
        val formats = arrayOf(
            "⚡  AANO Package (.aano - Direct Open)",
            "📄  Text File (.txt)",
            "📕  PDF Document (.pdf)",
            "📝  Markdown (.md)",
            "📦  ZIP Archive (.zip with Media)"
        )
        MaterialAlertDialogBuilder(context)
            .setTitle("Export Note")
            .setItems(formats) { _, which ->
                when (which) {
                    0 -> exportToAano(context, note, cacheDir)
                    1 -> exportToTxt(context, note, cacheDir)
                    2 -> exportToPdf(context, note)
                    3 -> exportToMd(context, note, cacheDir)
                    4 -> exportToZip(context, note, cacheDir)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cleanTitle(title: String): String =
        (if (title.isNotBlank()) title else "Untitled Note")
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(30).ifBlank { "Note" }

    private fun extractPlainText(content: String): String =
        if (content.contains("<") && content.contains(">")) {
            android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
        } else {
            content.trim()
        }

    private fun formatDuration(ms: Long): String {
        val sec = (ms / 1000) % 60
        val min = (ms / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    private fun shareFile(context: Context, file: File, mimeType: String, subject: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "app.aa.aano.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    private fun writeZipAttachments(zos: ZipOutputStream, images: List<String>, audioUri: String?) {
        for (imgPath in images) {
            val imgFile = File(imgPath)
            if (imgFile.exists() && imgFile.length() > 0) {
                zos.putNextEntry(ZipEntry("media/images/${imgFile.name}"))
                imgFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        if (!audioUri.isNullOrBlank()) {
            val audioFile = File(audioUri)
            if (audioFile.exists() && audioFile.length() > 0) {
                zos.putNextEntry(ZipEntry("media/audio/${audioFile.name}"))
                audioFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    fun exportToTxt(context: Context, note: Note, cacheDir: File) {
        val rawText = extractPlainText(note.content)
        val plainContent = NoteBlockParser.toPlainText(NoteBlockParser.parse(rawText))
        val title = if (note.title.isNotBlank()) note.title else "Untitled Note"
        val sb = StringBuilder().append(title).append("\n")
            .append("=".repeat(title.length.coerceAtLeast(8))).append("\n\n")
            .append("Folder: ").append(note.folder.ifBlank { "General" }).append("\n\n")
            .append(plainContent).append("\n")

        if (note.images.isNotEmpty() || !note.audioUri.isNullOrBlank()) {
            sb.append("\n----------------------------------------\nATTACHMENTS:\n")
            if (!note.audioUri.isNullOrBlank()) {
                sb.append("- 🎙️ Voice Memo: ${File(note.audioUri).name} (${formatDuration(note.audioDuration)})\n")
            }
            note.images.forEach { sb.append("  * ").append(File(it).name).append("\n") }
        }

        try {
            val exportDir = File(cacheDir, "exports").apply { mkdirs() }
            val txtFile = File(exportDir, "${cleanTitle(title)}.txt").apply { writeText(sb.toString(), Charsets.UTF_8) }
            val uri = FileProvider.getUriForFile(context, "app.aa.aano.fileprovider", txtFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, plainContent)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Note (.txt)"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export Text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToMd(context: Context, note: Note, cacheDir: File) {
        val rawText = extractPlainText(note.content)
        val formattedMd = NoteBlockParser.toMarkdown(NoteBlockParser.parse(rawText))
        val title = if (note.title.isNotBlank()) note.title else "Untitled Note"
        val sb = StringBuilder().append("# ").append(title).append("\n\n")
            .append("> **Folder:** ").append(note.folder.ifBlank { "General" }).append("\n\n")
            .append(formattedMd).append("\n")

        if (note.images.isNotEmpty() || !note.audioUri.isNullOrBlank()) {
            sb.append("\n\n---\n### Attachments\n\n")
            if (!note.audioUri.isNullOrBlank()) {
                sb.append("> 🎙️ **Voice Memo:** `${File(note.audioUri).name}` (${formatDuration(note.audioDuration)})\n\n")
            }
            if (note.images.isNotEmpty()) {
                sb.append("#### Photos (${note.images.size}):\n")
                note.images.forEach { sb.append("- ![${File(it).name}](${File(it).name})\n") }
            }
        }

        try {
            val exportDir = File(cacheDir, "exports").apply { mkdirs() }
            val mdFile = File(exportDir, "${cleanTitle(title)}.md").apply { writeText(sb.toString(), Charsets.UTF_8) }
            shareFile(context, mdFile, "text/markdown", title, "Export Note (.md)")
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export Markdown: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToPdf(context: Context, note: Note) {
        val pdfFile = PdfExportManager(context).exportNoteToPdf(note)
        if (pdfFile != null && pdfFile.exists()) {
            shareFile(context, pdfFile, "application/pdf", note.title.ifBlank { "Note" }, "Share Note PDF")
        } else {
            Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToZip(context: Context, note: Note, cacheDir: File) {
        val title = if (note.title.isNotBlank()) note.title else "Untitled Note"
        val filename = cleanTitle(title)
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now))
        val plainContent = extractPlainText(note.content)

        val txtSb = StringBuilder().append(title).append("\n")
            .append("=".repeat(title.length.coerceAtLeast(8))).append("\n\n")
            .append("Folder: ").append(note.folder.ifBlank { "General" }).append(" | Updated: ").append(dateStr).append("\n\n")
            .append(plainContent).append("\n")

        val mdSb = StringBuilder().append("# ").append(title).append("\n\n")
            .append("> **Folder:** ").append(note.folder.ifBlank { "General" }).append(" | **Updated:** ").append(dateStr).append("\n\n")
            .append(plainContent).append("\n")

        if (note.images.isNotEmpty() || !note.audioUri.isNullOrBlank()) {
            txtSb.append("\n----------------------------------------\nATTACHMENTS:\n")
            mdSb.append("\n\n---\n### Attachments\n\n")
            if (!note.audioUri.isNullOrBlank()) {
                val durStr = formatDuration(note.audioDuration)
                val audioFn = File(note.audioUri).name
                txtSb.append("- 🎙️ Voice Memo: $audioFn ($durStr)\n")
                mdSb.append("> 🎙️ **Voice Memo:** `$audioFn` ($durStr)\n\n")
            }
            if (note.images.isNotEmpty()) {
                txtSb.append("- 📷 Photos (${note.images.size}):\n")
                mdSb.append("#### Photos (${note.images.size}):\n")
                note.images.forEach {
                    val fn = File(it).name
                    txtSb.append("  * ").append(fn).append("\n")
                    mdSb.append("- ![$fn](media/images/$fn)\n")
                }
            }
        }

        try {
            val exportDir = File(cacheDir, "exports").apply { mkdirs() }
            val zipFile = File(exportDir, "${filename}_bundle.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("$filename.txt"))
                zos.write(txtSb.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("$filename.md"))
                zos.write(mdSb.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                writeZipAttachments(zos, note.images, note.audioUri)
            }
            shareFile(context, zipFile, "application/zip", "$title (ZIP Bundle)", "Share Note ZIP Bundle")
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export ZIP: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToAano(context: Context, note: Note, cacheDir: File) {
        val title = if (note.title.isNotBlank()) note.title else "Untitled Note"
        val filename = cleanTitle(title)
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now))
        val plainContent = extractPlainText(note.content)

        val mdSb = StringBuilder().append("# ").append(title).append("\n\n")
            .append("> **Folder:** ").append(note.folder.ifBlank { "General" }).append(" | **Updated:** ").append(dateStr).append("\n\n")
            .append(plainContent).append("\n")

        val jsonObj = JSONObject().apply {
            put("id", note.id)
            put("title", title)
            put("content", note.content)
            put("folder", note.folder.ifBlank { "General" })
            put("color", note.color)
            put("audioDuration", note.audioDuration)
            put("createdAt", now)
            put("updatedAt", now)
            put("images", JSONArray(note.images.map { File(it).name }))
            if (!note.audioUri.isNullOrBlank()) {
                put("audioUri", File(note.audioUri).name)
            }
        }

        try {
            val exportDir = File(cacheDir, "exports").apply { mkdirs() }
            val aanoFile = File(exportDir, "$filename.aano")
            ZipOutputStream(FileOutputStream(aanoFile)).use { zos ->
                zos.putNextEntry(ZipEntry("note.json"))
                zos.write(jsonObj.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("$filename.md"))
                zos.write(mdSb.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                writeZipAttachments(zos, note.images, note.audioUri)
            }
            shareFile(context, aanoFile, "application/octet-stream", "$title (AANO Package)", "Share AANO Note Package (.aano)")
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create AANO package: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
