package app.aa.aano.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class VaultExportSummary(
    val zipUri: Uri,
    val fileName: String,
    val notesCount: Int,
    val imagesCount: Int,
    val audioCount: Int,
    val fileSizeFormatted: String
)

data class VaultRestoreSummary(
    val notesRestored: Int,
    val imagesRestored: Int,
    val audioRestored: Int
)

object LocalVaultManager {

    suspend fun exportVault(context: Context, repository: NoteRepository): VaultExportSummary = withContext(Dispatchers.IO) {
        val localNotes = repository.getLocalNotes()
        val tempDir = File(context.cacheDir, "vault_export_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempZip = File(tempDir, "aano_vault.zip")

        var imagesCount = 0
        var audioCount = 0

        val notesJsonArray = JSONArray()
        val filesToZip = mutableListOf<Pair<String, File>>() // Pair(relativeZipPath, actualFile)

        for (note in localNotes) {
            val noteObj = note.toJson()
            val relativeImages = mutableListOf<String>()

            // Normalize and collect image files
            for (imgPath in note.images) {
                val f = File(imgPath)
                if (f.exists() && f.isFile) {
                    val zipName = "media/images/${f.name}"
                    relativeImages.add(zipName)
                    filesToZip.add(zipName to f)
                    imagesCount++
                }
            }
            noteObj.put("images", JSONArray(relativeImages))

            // Normalize and collect audio file
            note.audioUri?.let { audioPath ->
                val af = File(audioPath)
                if (af.exists() && af.isFile) {
                    val zipName = "media/audio/${af.name}"
                    noteObj.put("audioUri", zipName)
                    filesToZip.add(zipName to af)
                    audioCount++
                }
            }

            notesJsonArray.put(noteObj)
        }

        // Write zip
        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
            // 1. Add notes.json
            val jsonBytes = notesJsonArray.toString(2).toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("notes.json"))
            zos.write(jsonBytes)
            zos.closeEntry()

            // 2. Add media files (deduplicating by zipPath)
            val addedPaths = mutableSetOf<String>()
            for ((zipPath, file) in filesToZip) {
                if (addedPaths.add(zipPath)) {
                    zos.putNextEntry(ZipEntry(zipPath))
                    file.inputStream().use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }

        // Save to Downloads/AANO
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "AANO-LocalVault-$timeStamp.zip"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AANO")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create vault zip in Downloads")

        resolver.openOutputStream(uri)?.use { out ->
            tempZip.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("Could not write to destination zip")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        val sizeFormatted = formatFileSize(tempZip.length())
        try { tempDir.deleteRecursively() } catch (_: Exception) {}

        VaultExportSummary(
            zipUri = uri,
            fileName = fileName,
            notesCount = localNotes.size,
            imagesCount = imagesCount,
            audioCount = audioCount,
            fileSizeFormatted = sizeFormatted
        )
    }

    suspend fun restoreVault(context: Context, zipUri: Uri, repository: NoteRepository): VaultRestoreSummary = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(zipUri)
            ?: throw IllegalStateException("Could not open zip stream")

        val targetImagesDir = File(context.filesDir, "media/images").apply { mkdirs() }
        val targetAudioDir = File(context.filesDir, "media/audio").apply { mkdirs() }

        var jsonContent: String? = null
        var imagesRestored = 0
        var audioRestored = 0

        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "notes.json" || name == "local_notes.json") {
                    jsonContent = zis.readBytes().toString(Charsets.UTF_8)
                } else if (name.startsWith("media/images/") || name.startsWith("images/")) {
                    val fileName = File(name).name
                    val destFile = File(targetImagesDir, fileName)
                    destFile.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                    imagesRestored++
                } else if (name.startsWith("media/audio/") || name.startsWith("audio/")) {
                    val fileName = File(name).name
                    val destFile = File(targetAudioDir, fileName)
                    destFile.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                    audioRestored++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (jsonContent.isNullOrBlank()) {
            throw IllegalArgumentException("Invalid backup file: no notes.json found inside the archive.")
        }

        val jsonArray = JSONArray(jsonContent)
        val restoredNotes = mutableListOf<Note>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            // Reconstruct absolute image paths for current device
            val rawImages = obj.optJSONArray("images")
            val absoluteImages = mutableListOf<String>()
            if (rawImages != null) {
                for (j in 0 until rawImages.length()) {
                    val rel = rawImages.getString(j)
                    val fn = File(rel).name
                    val localFile = File(targetImagesDir, fn)
                    absoluteImages.add(localFile.absolutePath)
                }
            }
            obj.put("images", JSONArray(absoluteImages))

            // Reconstruct absolute audio path
            if (obj.has("audioUri") && !obj.isNull("audioUri")) {
                val relAudio = obj.getString("audioUri")
                val fn = File(relAudio).name
                val localAudio = File(targetAudioDir, fn)
                obj.put("audioUri", localAudio.absolutePath)
            }

            // Ensure isLocal = true
            obj.put("isLocal", true)

            restoredNotes.add(Note.fromJson(obj))
        }

        repository.importLocalVaultNotes(restoredNotes)

        VaultRestoreSummary(
            notesRestored = restoredNotes.size,
            imagesRestored = imagesRestored,
            audioRestored = audioRestored
        )
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
