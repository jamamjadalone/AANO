// EditorImageHelper: Image thumbnails, fullscreen preview, crop/replace/save to gallery
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.R
import app.aa.aano.pages.CropActivity
import java.io.File
import java.io.FileInputStream

object EditorImageHelper {

    fun renderAttachedImages(
        activity: Activity,
        container: LinearLayout,
        scrollView: HorizontalScrollView,
        attachedImages: MutableList<String>,
        onImagesModified: () -> Unit,
        onCropRequested: (path: String, index: Int) -> Unit,
        onReplaceRequested: (index: Int) -> Unit
    ) {
        container.removeAllViews()
        if (attachedImages.isEmpty()) {
            scrollView.visibility = View.GONE
            return
        }
        scrollView.visibility = View.VISIBLE
        val density = activity.resources.displayMetrics.density
        val itemSize = (110 * density).toInt()
        val margin = (5 * density).toInt()

        attachedImages.forEachIndexed { index, path ->
            val frame = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(itemSize, itemSize).apply {
                    setMargins(margin, margin, margin, margin)
                }
            }

            val card = MaterialCardView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                radius = 12 * density
                cardElevation = 2 * density
                strokeWidth = (1 * density).toInt()
                strokeColor = ContextCompat.getColor(activity, R.color.card_stroke)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    showImagePreview(activity, path, index, attachedImages, onImagesModified, onCropRequested, onReplaceRequested)
                }
                setOnLongClickListener {
                    showImageContextMenu(activity, path, index, attachedImages, onImagesModified, onCropRequested, onReplaceRequested)
                    true
                }
            }

            val iv = ImageView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                var scale = 1
                while (opts.outWidth / scale / 2 >= 220 && opts.outHeight / scale / 2 >= 220) scale *= 2
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = scale }
                val bmp = BitmapFactory.decodeFile(path, decodeOpts)
                iv.setImageBitmap(bmp)
            } catch (e: Exception) { Log.w("EditorImageHelper", "Decode thumbnail error: ${e.message}") }

            card.addView(iv)
            frame.addView(card)

            val btnRemove = ImageView(activity).apply {
                val btnSize = (26 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, (2 * density).toInt(), (2 * density).toInt(), 0)
                }
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundResource(R.drawable.circle_bg_translucent)
                setColorFilter(0xFFFFFFFF.toInt())
                setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                setOnClickListener {
                    attachedImages.removeAt(index)
                    try { File(path).delete() } catch (e: Exception) { Log.w("EditorImageHelper", "Delete image error: ${e.message}") }
                    renderAttachedImages(activity, container, scrollView, attachedImages, onImagesModified, onCropRequested, onReplaceRequested)
                    onImagesModified()
                }
            }
            frame.addView(btnRemove)
            container.addView(frame)
        }
    }

    fun showImagePreview(
        activity: Activity,
        path: String,
        index: Int,
        attachedImages: MutableList<String>,
        onImagesModified: () -> Unit,
        onCropRequested: (path: String, index: Int) -> Unit,
        onReplaceRequested: (index: Int) -> Unit
    ) {
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.window?.setBackgroundDrawable(ColorDrawable(0xF20B1120.toInt()))
        val root = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val imageView = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
                setMargins(20, 100, 20, 120)
            }
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        try {
            val bmp = BitmapFactory.decodeFile(path)
            imageView.setImageBitmap(bmp)
        } catch (e: Exception) { Log.w("EditorImageHelper", "Decode preview error: ${e.message}") }
        root.addView(imageView)

        val density = activity.resources.displayMetrics.density
        val btnClose = ImageView(activity).apply {
            val size = (44 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, (40 * density).toInt(), (20 * density).toInt(), 0)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.circle_bg_translucent)
            val pad = (10 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnClose)

        val bottomBar = LinearLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (32 * density).toInt()
            }
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.circle_bg_translucent)
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
        }

        fun makeBtn(text: String, bg: Int, fg: Int, onClick: () -> Unit) = MaterialButton(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (40 * density).toInt()).apply { marginEnd = (8 * density).toInt() }
            this.text = text
            setTextColor(fg)
            setBackgroundColor(bg)
            cornerRadius = (20 * density).toInt()
            setOnClickListener { onClick() }
        }

        bottomBar.addView(makeBtn("✂️ Crop", 0x33FFFFFF.toInt(), 0xFFFFFFFF.toInt()) {
            dialog.dismiss()
            onCropRequested(path, index)
        })
        bottomBar.addView(makeBtn("🔄 Replace", 0x33FFFFFF.toInt(), 0xFFFFFFFF.toInt()) {
            dialog.dismiss()
            onReplaceRequested(index)
        })
        bottomBar.addView(makeBtn("💾 Gallery", 0x33FFFFFF.toInt(), 0xFFFFFFFF.toInt()) {
            saveImageToGallery(activity, path)
        })
        bottomBar.addView(makeBtn("🗑️ Remove", 0x44C62828.toInt(), 0xFFFFCDD2.toInt()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Remove Image")
                .setMessage("Remove this image from the note?")
                .setPositiveButton("Remove") { _, _ ->
                    dialog.dismiss()
                    if (index in attachedImages.indices) {
                        attachedImages.removeAt(index)
                        try { File(path).delete() } catch (e: Exception) { Log.w("EditorImageHelper", "Delete error: ${e.message}") }
                        onImagesModified()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        })
        root.addView(bottomBar)

        dialog.setContentView(root)
        dialog.show()
    }

    private fun showImageContextMenu(
        activity: Activity,
        path: String,
        index: Int,
        attachedImages: MutableList<String>,
        onImagesModified: () -> Unit,
        onCropRequested: (path: String, index: Int) -> Unit,
        onReplaceRequested: (index: Int) -> Unit
    ) {
        val options = arrayOf("🔍 View Fullscreen", "✂️ Crop Image", "🔄 Replace Image", "💾 Save to Gallery", "🗑️ Remove Image")
        MaterialAlertDialogBuilder(activity)
            .setTitle("Image Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showImagePreview(activity, path, index, attachedImages, onImagesModified, onCropRequested, onReplaceRequested)
                    1 -> onCropRequested(path, index)
                    2 -> onReplaceRequested(index)
                    3 -> saveImageToGallery(activity, path)
                    4 -> {
                        if (index in attachedImages.indices) {
                            attachedImages.removeAt(index)
                            try { File(path).delete() } catch (e: Exception) { Log.w("EditorImageHelper", "Delete error: ${e.message}") }
                            onImagesModified()
                        }
                    }
                }
            }
            .show()
    }

    fun saveImageToGallery(context: Context, path: String) {
        try {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, "Image file not found", Toast.LENGTH_SHORT).show()
                return
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "AANO_${System.currentTimeMillis()}.${file.extension}")
                put(MediaStore.Images.Media.MIME_TYPE, if (file.extension.equals("png", true)) "image/png" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AANO")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input -> input.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Toast.makeText(context, "Saved image to Pictures/AANO", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
