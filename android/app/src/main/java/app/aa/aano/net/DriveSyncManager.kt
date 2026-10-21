// Google Drive REST API sync for AANO (notes and tasks, appDataFolder).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.net

import android.content.Context
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
        const val APP_FOLDER = "appDataFolder"
        const val REMOTE_NOTES_FILE = "aano-notes.json"
        const val REMOTE_TASKS_FILE = "aano-tasks.json"
        val SCOPE = listOf(DriveScopes.DRIVE_APPDATA)
    }

    fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
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
        service.files().list()
            .setSpaces(APP_FOLDER)
            .setQ("name='$name'")
            .setFields("files(id, name, modifiedTime)")
            .execute()
            .files
            .firstOrNull()

    suspend fun pullFromDrive(): Boolean = withContext(Dispatchers.IO) {
        val service = buildDriveService()
        var pulled = false
        findRemoteFile(service, REMOTE_NOTES_FILE)?.let { remote ->
            val bytes = service.files().get(remote.id)
                .executeMediaAsInputStream()
                .use { it.readBytes() }
            notesRepository.importJson(String(bytes, Charsets.UTF_8))
            pulled = true
        }
        findRemoteFile(service, REMOTE_TASKS_FILE)?.let { remote ->
            val bytes = service.files().get(remote.id)
                .executeMediaAsInputStream()
                .use { it.readBytes() }
            tasksRepository.importJson(String(bytes, Charsets.UTF_8))
            pulled = true
        }
        pulled
    }

    suspend fun pushToDrive(): Boolean = withContext(Dispatchers.IO) {
        val service = buildDriveService()
        push(service, REMOTE_NOTES_FILE, notesRepository.exportJson())
        push(service, REMOTE_TASKS_FILE, tasksRepository.exportJson())
        true
    }

    private fun push(service: Drive, name: String, json: String) {
        val content = ByteArrayContent.fromString("application/json", json)
        val remote = findRemoteFile(service, name)
        if (remote != null) {
            service.files().update(remote.id, File().setName(name), content)
                .setUploadType("media")
                .execute()
        } else {
            service.files().create(File().setName(name), content)
                .setUploadType("media")
                .setFields("id,name")
                .execute()
        }
    }

    /** Re-throws auth exceptions so the UI can offer account recovery. */
    fun isRecoverable(e: Throwable): Boolean = e is UserRecoverableAuthIOException
}