package app.aa.aano.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.aa.aano.R
import app.aa.aano.databinding.ActivityCropBinding
import java.io.File

class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
        const val EXTRA_PURGE_ORIGINAL = "extra_purge_original"
    }

    private lateinit var binding: ActivityCropBinding
    private var inputPath: String? = null
    private var sourceBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inputPath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (inputPath.isNullOrBlank() || !File(inputPath!!).exists()) {
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadSourceBitmap()
        setupListeners()
    }

    private fun loadSourceBitmap() {
        val path = inputPath ?: return
        try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, boundsOpts)

            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > 2560 || boundsOpts.outHeight / sampleSize > 2560) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            sourceBitmap = BitmapFactory.decodeFile(path, decodeOpts)

            if (sourceBitmap != null) {
                binding.cropOverlayView.setBitmap(sourceBitmap!!)
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error decoding image: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnRotate.setOnClickListener {
            binding.cropOverlayView.rotate90()
        }

        // Aspect ratio buttons
        binding.btnRatioFree.setOnClickListener {
            selectRatioButton(binding.btnRatioFree)
            binding.cropOverlayView.setAspectRatio(null)
        }
        binding.btnRatioSquare.setOnClickListener {
            selectRatioButton(binding.btnRatioSquare)
            binding.cropOverlayView.setAspectRatio(1.0f)
        }
        binding.btnRatio43.setOnClickListener {
            selectRatioButton(binding.btnRatio43)
            binding.cropOverlayView.setAspectRatio(4f / 3f)
        }
        binding.btnRatio169.setOnClickListener {
            selectRatioButton(binding.btnRatio169)
            binding.cropOverlayView.setAspectRatio(16f / 9f)
        }

        binding.btnApply.setOnClickListener {
            applyCropAndFinish()
        }
    }

    private fun selectRatioButton(selected: com.google.android.material.button.MaterialButton) {
        val activeColor = ContextCompat.getColor(this, R.color.brand)
        val inactiveColor = android.graphics.Color.parseColor("#475569")
        val buttons = listOf(binding.btnRatioFree, binding.btnRatioSquare, binding.btnRatio43, binding.btnRatio169)

        buttons.forEach { btn ->
            if (btn == selected) {
                btn.strokeColor = android.content.res.ColorStateList.valueOf(activeColor)
                btn.setTextColor(activeColor)
            } else {
                btn.strokeColor = android.content.res.ColorStateList.valueOf(inactiveColor)
                btn.setTextColor(android.graphics.Color.WHITE)
            }
        }
    }

    private fun applyCropAndFinish() {
        val cropped = binding.cropOverlayView.getCroppedBitmap()
        if (cropped == null) {
            Toast.makeText(this, "Could not crop image", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val mediaDir = File(filesDir, "media/images").apply { mkdirs() }
            val outputFile = File(mediaDir, "crop_${System.currentTimeMillis()}_${(100..999).random()}.webp")

            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            outputFile.outputStream().use { out ->
                cropped.compress(format, 80, out)
            }

            // Optional purge of original if requested
            if (intent.getBooleanExtra(EXTRA_PURGE_ORIGINAL, false)) {
                inputPath?.let { old ->
                    try { File(old).delete() } catch (e: Exception) { Log.w("AANO", "Failed to purge original image $old: ${e.message}") }
                }
            }

            val resultIntent = Intent().apply {
                putExtra(EXTRA_OUTPUT_PATH, outputFile.absolutePath)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save cropped image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
