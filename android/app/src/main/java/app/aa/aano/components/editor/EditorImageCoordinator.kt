// EditorImageCoordinator: Manages image picker, cropper, replacer launchers and view rendering
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.aa.aano.databinding.ActivityEditorBinding
import app.aa.aano.pages.CropActivity
import java.io.File

class EditorImageCoordinator(
    private val activity: AppCompatActivity,
    private val binding: ActivityEditorBinding,
    private val attachedImages: MutableList<String>,
    private val onImagesModified: () -> Unit
) {
    private var croppingIndex: Int = -1
    private var replacingIndex: Int = -1
    var isHdQuality: Boolean = false

    private val pickImagesLauncher = activity.registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { uri ->
                EditorMediaHelper.copyUriToLocalMedia(activity, uri, isHdQuality)?.let { attachedImages.add(it) }
            }
            render()
            onImagesModified()
        }
    }

    private val cropImageLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && croppingIndex in attachedImages.indices) {
            val newPath = res.data?.getStringExtra(CropActivity.EXTRA_OUTPUT_PATH)
            if (!newPath.isNullOrBlank() && File(newPath).exists()) {
                val old = attachedImages[croppingIndex]
                attachedImages[croppingIndex] = newPath
                if (old != newPath) try { File(old).delete() } catch (_: Exception) {}
                render()
                onImagesModified()
            }
        }
        croppingIndex = -1
    }

    private val replaceImageLauncher = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && replacingIndex in attachedImages.indices) {
            val newPath = EditorMediaHelper.copyUriToLocalMedia(activity, uri, isHdQuality)
            if (newPath != null) {
                val old = attachedImages[replacingIndex]
                attachedImages[replacingIndex] = newPath
                if (old != newPath) try { File(old).delete() } catch (_: Exception) {}
                render()
                onImagesModified()
            }
        }
        replacingIndex = -1
    }

    fun pickImages() {
        pickImagesLauncher.launch("image/*")
    }

    fun toggleQuality(btn: android.widget.TextView, context: android.content.Context) {
        isHdQuality = !isHdQuality
        btn.text = if (isHdQuality) "HD" else "STD"
        btn.setTextColor(if (isHdQuality) 0xFFD97706.toInt() else androidx.core.content.ContextCompat.getColor(context, app.aa.aano.R.color.brand))
        android.widget.Toast.makeText(context, if (isHdQuality) "Attachment Quality: HD Original" else "Attachment Quality: Standard WebP", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun render() {
        EditorImageHelper.renderAttachedImages(
            activity = activity,
            container = binding.imagesContainer,
            scrollView = binding.imagesScrollView,
            attachedImages = attachedImages,
            onImagesModified = onImagesModified,
            onCropRequested = { p, i ->
                croppingIndex = i
                cropImageLauncher.launch(Intent(activity, CropActivity::class.java).putExtra(CropActivity.EXTRA_IMAGE_PATH, p))
            },
            onReplaceRequested = { i ->
                replacingIndex = i
                replaceImageLauncher.launch("image/*")
            }
        )
    }
}
