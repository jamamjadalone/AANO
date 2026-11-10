// SettingsBackupDelegate: Handles JSON backup export and import, and Media Vault export/import.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject

object SettingsBackupDelegate {

    suspend fun createBackupJson(noteRepo: NoteRepository, taskRepo: TaskRepository): String {
        val notesJson = org.json.JSONArray(noteRepo.exportJson())
        val tasksJson = org.json.JSONArray(taskRepo.exportJson())
        val bundle = JSONObject()
            .put("version", 1)
            .put("notes", notesJson)
            .put("tasks", tasksJson)
        return bundle.toString()
    }

    suspend fun restoreBackupJson(context: Context, uri: Uri, noteRepo: NoteRepository, taskRepo: TaskRepository) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?.toString(Charsets.UTF_8)
            ?: throw IllegalStateException("Could not read file")
        val bundle = JSONObject(text)
        if (bundle.has("notes") && bundle.has("tasks")) {
            noteRepo.importJson(bundle.getString("notes"))
            taskRepo.importJson(bundle.getString("tasks"))
        } else {
            noteRepo.importJson(text)
        }
    }

    suspend fun exportVault(context: Context, noteRepo: NoteRepository): VaultExportSummary =
        LocalVaultManager.exportVault(context, noteRepo)

    suspend fun restoreVault(context: Context, uri: Uri, noteRepo: NoteRepository): VaultRestoreSummary =
        LocalVaultManager.restoreVault(context, uri, noteRepo)

    fun saveToDownloads(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "/AANO")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val uri: Uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create file")
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Could not open output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }
}
