// LocalVaultDialogHelper: Handles Vault export/restore UI dialogs
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.aa.aano.data.LocalVaultManager
import app.aa.aano.data.NoteRepository

object LocalVaultDialogHelper {

    fun exportLocalVault(
        context: Context,
        scope: CoroutineScope,
        repository: NoteRepository,
        onShareVault: (shareIntent: Intent) -> Unit
    ) {
        scope.launch {
            try {
                val summary = LocalVaultManager.exportVault(context, repository)
                AlertDialog.Builder(context)
                    .setTitle("Media Vault Exported")
                    .setMessage(
                        "Local vault archived successfully!\n\n" +
                        "• Notes: ${summary.notesCount}\n" +
                        "• Photos: ${summary.imagesCount}\n" +
                        "• Audio Memos: ${summary.audioCount}\n" +
                        "• Size: ${summary.fileSizeFormatted}\n\n" +
                        "Saved to: Downloads/AANO/${summary.fileName}"
                    )
                    .setPositiveButton("Share Archive") { _, _ ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, summary.zipUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        onShareVault(Intent.createChooser(shareIntent, "Share Vault Archive"))
                    }
                    .setNegativeButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun restoreLocalVault(
        context: Context,
        scope: CoroutineScope,
        uri: Uri,
        repository: NoteRepository,
        onSuccess: () -> Unit
    ) {
        scope.launch {
            try {
                val summary = LocalVaultManager.restoreVault(context, uri, repository)
                onSuccess()
                AlertDialog.Builder(context)
                    .setTitle("Media Vault Restored")
                    .setMessage(
                        "Vault restore complete!\n\n" +
                        "• Local Notes: ${summary.notesRestored}\n" +
                        "• Photos Extracted: ${summary.imagesRestored}\n" +
                        "• Audio Memos: ${summary.audioRestored}\n\n" +
                        "Your local multimedia notes are now ready."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
