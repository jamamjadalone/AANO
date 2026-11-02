// Google Drive REST API sync for AANO (user-owned vault files in Google Drive via DRIVE_FILE scope).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.net

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveSyncManager(
    private val context: Context,
    private val notesRepository: NoteRepository,
    private val tasksRepository: TaskRepository
) {

    companion object {
        const val REMOTE_NOTES_FILE = "aano-notes.json"
        const val REMOTE_TASKS_FILE = "aano-tasks.json"
        val SCOPE = listOf(DriveScopes.DRIVE_FILE)
    }

    fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestEmail()
            .build()

    private fun buildDriveService(): Drive {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("No signed-in Google account.")
        val credential = GoogleAccountCredential.usingOAuth2(context, SCOPE).apply {
            selectedAccountName = account.email
        }
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("AANO").build()
    }

    private fun findRemoteFile(service: Drive, name: String): File? =
        try {
            service.files().list()
                .setQ("name = '$name' and trashed = false")
                .setOrderBy("modifiedTime desc")
                .setFields("files(id, name, modifiedTime, size)")
                .execute()
                .files
                ?.firstOrNull()
        } catch (e: Exception) {
            Log.w("AANO", "Drive findRemoteFile error for $name: ${e.message}")
            null
        }

    suspend fun pullFromDrive(): Boolean = withContext(Dispatchers.IO) {
        val service = buildDriveService()
        var pulled = false
        findRemoteFile(service, REMOTE_NOTES_FILE)?.let { remote ->
            try {
                val bytes = service.files().get(remote.id)
                    .executeMediaAsInputStream()
                    .use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    notesRepository.importJson(String(bytes, Charsets.UTF_8))
                    pulled = true
                }
            } catch (e: Exception) {
                Log.e("AANO", "Failed to pull notes from Drive: ${e.message}", e)
            }
            Unit
        }
        findRemoteFile(service, REMOTE_TASKS_FILE)?.let { remote ->
            try {
                val bytes = service.files().get(remote.id)
                    .executeMediaAsInputStream()
                    .use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    tasksRepository.importJson(String(bytes, Charsets.UTF_8))
                    pulled = true
                }
            } catch (e: Exception) {
                Log.e("AANO", "Failed to pull tasks from Drive: ${e.message}", e)
            }
            Unit
        }
        pulled
    }

    suspend fun pushToDrive(): Boolean = withContext(Dispatchers.IO) {
        val service = buildDriveService()
        push(service, REMOTE_NOTES_FILE, notesRepository.exportJson())
        push(service, REMOTE_TASKS_FILE, tasksRepository.exportJson())
        true
    }

    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        try {
            pullFromDrive()
            pushToDrive()
            true
        } catch (e: Exception) {
            Log.e("AANO", "Drive sync error: ${e.message}", e)
            false
        }
    }

    private fun push(service: Drive, name: String, json: String) {
        if (json.isEmpty()) return
        val content = ByteArrayContent.fromString("application/json", json)
        val files = try {
            service.files().list()
                .setQ("name = '$name' and trashed = false")
                .setOrderBy("modifiedTime desc")
                .setFields("files(id, name, modifiedTime)")
                .execute()
                .files ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (files.isNotEmpty()) {
            val primary = files[0]
            service.files().update(primary.id, null, content)
                .setUploadType("media")
                .execute()

            // Trash stale duplicate files with identical names to keep user Drive clean
            if (files.size > 1) {
                for (i in 1 until files.size) {
                    try {
                        service.files().delete(files[i].id).execute()
                    } catch (e: Exception) {
                        Log.w("AANO", "Failed to trash stale duplicate file ${files[i].id}", e)
                    }
                }
            }
        } else {
            val fileMetadata = File().apply {
                this.name = name
                this.mimeType = "application/json"
            }
            service.files().create(fileMetadata, content)
                .setUploadType("multipart")
                .setFields("id,name")
                .execute()
        }
    }

    /** Re-throws auth exceptions so the UI can offer account recovery. */
    fun isRecoverable(e: Throwable): Boolean = e is UserRecoverableAuthIOException
}