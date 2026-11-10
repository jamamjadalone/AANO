// Decryption, media persistence, and note merge engine for incoming AANO Drop payloads.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.drop

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.util.Log
import app.aa.aano.data.NoteMergeEngine
import app.aa.aano.data.NoteRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object DropPayloadProcessor {

    private const val TAG = "DropPayload"
    private val ALLOWED_IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif")

    suspend fun processIncomingPayload(
        context: Context,
        repository: NoteRepository,
        payload: JSONObject,
        reqToken: String,
        pin: String
    ): Result<Pair<Int, Int>> {
        val isEncrypted = payload.optBoolean("encrypted", false)
        val imagesArr: JSONArray?
        val notesArr: JSONArray?

        if (isEncrypted) {
            val ivB64 = payload.optString("iv_b64")
            val dataB64 = payload.optString("data_b64")
            if (ivB64.isNotEmpty() && dataB64.isNotEmpty()) {
                try {
                    val iv = Base64.decode(ivB64, Base64.DEFAULT)
                    val ct = Base64.decode(dataB64, Base64.DEFAULT)
                    val effectiveKeyPin = if (pin.isNotEmpty()) pin else reqToken
                    val keyBytes = MessageDigest.getInstance("SHA-256")
                        .digest("AANO_DROP_TRANSPORT_KEY_V1:$effectiveKeyPin".toByteArray(Charsets.UTF_8))
                    val secretKey = SecretKeySpec(keyBytes, "AES")
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                    val innerJson = JSONObject(String(cipher.doFinal(ct), Charsets.UTF_8))
                    imagesArr = innerJson.optJSONArray("images")
                    notesArr = innerJson.optJSONArray("notes")
                } catch (e: Exception) {
                    Log.w(TAG, "Decryption failed: ${e.message}")
                    return Result.failure(Exception("Payload decryption failed."))
                }
            } else {
                imagesArr = null
                notesArr = null
            }
        } else {
            imagesArr = payload.optJSONArray("images")
            notesArr = payload.optJSONArray("notes")
        }

        var savedImages = 0
        if (imagesArr != null) {
            val mediaDir = File(context.filesDir, "media/images").apply { mkdirs() }
            for (i in 0 until imagesArr.length()) {
                val imgObj = imagesArr.getJSONObject(i)
                val rawName = File(imgObj.optString("name", "img_${System.currentTimeMillis()}.webp")).name
                val ext = rawName.substringAfterLast('.', "").lowercase()
                if (ext !in ALLOWED_IMAGE_EXTS) continue
                val b64 = imgObj.optString("data_base64").ifEmpty { imgObj.optString("bytes_base64") }
                if (rawName.isNotEmpty() && b64.isNotEmpty()) {
                    try {
                        val cleanB64 = if (b64.contains(",")) b64.substringAfter(",") else b64
                        File(mediaDir, rawName).writeBytes(Base64.decode(cleanB64, Base64.DEFAULT))
                        savedImages++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to write image $rawName: ${e.message}")
                    }
                }
            }
        }

        var savedNotes = 0
        if (notesArr != null) {
            for (i in 0 until notesArr.length()) {
                val nObj = notesArr.getJSONObject(i)
                val id = nObj.optString("id")
                if (id.isEmpty()) continue
                val incoming = repository.deserialize(nObj)
                val existing = repository.getById(id)
                val finalNote = if (existing != null && existing.content != incoming.content) {
                    val (mergedContent, _) = NoteMergeEngine.mergeNoteContent(existing.content, incoming.content)
                    incoming.copy(
                        content = mergedContent,
                        tags = (existing.tags + incoming.tags).distinct(),
                        images = (existing.images + incoming.images).distinct(),
                        isLocal = true,
                        updatedAt = maxOf(nObj.optLong("updatedAt", System.currentTimeMillis()), System.currentTimeMillis())
                    )
                } else {
                    incoming.copy(
                        isLocal = true,
                        updatedAt = maxOf(nObj.optLong("updatedAt", System.currentTimeMillis()), System.currentTimeMillis())
                    )
                }
                repository.upsertNote(finalNote)
                savedNotes++
            }
        }

        triggerHapticChime(context)
        return Result.success(Pair(savedNotes, savedImages))
    }

    fun triggerHapticChime(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(180)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic chime suppressed: ${e.message}")
        }
    }
}
