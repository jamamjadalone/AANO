// Note editor screen for AANO with rich text formatting toolbar and auto-save.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import android.net.Uri
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.os.SystemClock
import android.graphics.BitmapFactory
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.ActivityEditorBinding
import app.aa.aano.net.CloudSyncManager
import app.aa.aano.net.NoteReminderReceiver
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque
import java.util.Calendar

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_FOLDER = "extra_folder"
        const val EXTRA_IS_LOCAL = "extra_is_local"
        private const val AUTO_SAVE_DELAY_MS = 800L
        private const val REMINDER_PERMISSION_CODE = 42

        val NOTE_COLORS = listOf(
            R.color.note_color_default,
            R.color.note_color_teal,
            R.color.note_color_yellow,
            R.color.note_color_green,
            R.color.note_color_blue,
            R.color.note_color_purple,
            R.color.note_color_pink,
            R.color.note_color_orange,
            R.color.note_color_red,
            R.color.note_color_sand,
            R.color.note_color_slate,
            R.color.note_color_charcoal
        )

        fun presetColors(): List<Int> = NOTE_COLORS

        fun resolveNoteColor(context: android.content.Context, colorValue: Int): Int {
            return when {
                colorValue in 1 until NOTE_COLORS.size -> ContextCompat.getColor(context, NOTE_COLORS[colorValue])
                colorValue == 0 -> ContextCompat.getColor(context, R.color.card_surface)
                else -> colorValue // Direct 32-bit ARGB color
            }
        }
    }

    private lateinit var binding: ActivityEditorBinding
    private lateinit var repository: NoteRepository
    private lateinit var prefs: Prefs
    private lateinit var cloudSyncManager: CloudSyncManager
    private var noteId: String? = null
    private var loaded = false
    private var selectedColor = 0
    private var selectedFolder = ""
    private var selectedTags = mutableListOf<String>()

    // Local multimedia state
    private var isLocalNote: Boolean = false
    private val attachedImages = mutableListOf<String>()
    private var attachedAudioUri: String? = null
    private var attachedAudioDuration: Long = 0L

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording: Boolean = false
    private var isPlayingVoice: Boolean = false
    private var recordingStartTime: Long = 0L
    private val recordingTimerHandler = Handler(Looper.getMainLooper())
    private var currentRecordingFile: File? = null

    private val saveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable {
        if (loaded) saveSilently()
    }

    // Undo / Redo history stacks
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var isUndoRedoActive = false
    private var isDeleted = false
    private var isEditMode = true

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { uri ->
                copyUriToLocalMedia(uri)?.let { path ->
                    attachedImages.add(path)
                }
            }
            renderAttachedImages()
            saveSilently()
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecording()
        } else {
            Toast.makeText(this, "Microphone permission required for voice memo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndFinish()
            }
        })

        repository = NoteRepository(this)
        prefs = Prefs(this)
        cloudSyncManager = CloudSyncManager(
            this,
            repository,
            TaskRepository(this)
        )
        noteId = intent.getStringExtra(EXTRA_NOTE_ID)

        binding.toolbar.setNavigationOnClickListener { saveAndFinish() }

        setupFolderChip()
        setupFormattingToolbar()
        setupWordCounter()
        buildColorRow()
        setupSeamlessTouch()

        attachAutoSave(binding.noteTitle)
        attachAutoSave(binding.noteContent)

        noteId?.let { id ->
            lifecycleScope.launch {
                val note = repository.getById(id)
                if (note != null) {
                    binding.noteTitle.setText(note.title)
                    if (note.content.contains("<") && note.content.contains(">")) {
                        binding.noteContent.setText(Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT))
                    } else {
                        binding.noteContent.setText(note.content)
                    }
                    selectedFolder = note.folder
                    selectedTags = note.tags.toMutableList()
                    isLocalNote = note.isLocal
                    attachedImages.clear()
                    attachedImages.addAll(note.images)
                    attachedAudioUri = note.audioUri
                    attachedAudioDuration = note.audioDuration
                    updateFolderChipDisplay()
                    updateLocalChipDisplay()
                    renderAttachedImages()
                    renderVoiceMemoBar()
                    selectedColor = note.color
                    highlightSelectedColor()
                    updateWordCount()
                    pushHistorySnapshot()
                    isEditMode = false
                    applyModeUi()
                } else {
                    noteId = null
                    pushHistorySnapshot()
                    isEditMode = true
                    applyModeUi()
                }
                loaded = true
            }
        } ?: run {
            selectedFolder = intent.getStringExtra(EXTRA_FOLDER) ?: ""
            isLocalNote = intent.getBooleanExtra(EXTRA_IS_LOCAL, false)
            loaded = true
            isEditMode = true
            updateFolderChipDisplay()
            updateLocalChipDisplay()
            updateWordCount()
            pushHistorySnapshot()
            applyModeUi()
            binding.noteTitle.post {
                binding.noteTitle.requestFocus()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(binding.noteTitle, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun setupFolderChip() {
        updateFolderChipDisplay()
        binding.chipFolder.setOnClickListener {
            showFolderPickerDialog()
        }
    }

    private fun updateFolderChipDisplay() {
        binding.chipFolder.text = if (selectedFolder.isBlank()) getString(R.string.folder_all) else selectedFolder
    }

    private fun showFolderPickerDialog() {
        lifecycleScope.launch {
            val folders = repository.folders().toMutableList()
            if (!folders.contains("General")) folders.add(0, "General")
            if (!folders.contains("Work")) folders.add("Work")
            if (!folders.contains("Personal")) folders.add("Personal")

            val options = (listOf("General (None)") + folders.filter { it != "General" } + listOf("+ Create New Folder")).toTypedArray()

            MaterialAlertDialogBuilder(this@EditorActivity)
                .setTitle("Select Folder")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            selectedFolder = ""
                            updateFolderChipDisplay()
                            triggerAutoSave()
                        }
                        options.size - 1 -> {
                            showCreateFolderDialog()
                        }
                        else -> {
                            selectedFolder = options[which]
                            updateFolderChipDisplay()
                            triggerAutoSave()
                        }
                    }
                }
                .show()
        }
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = "Folder name"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    selectedFolder = name
                    updateFolderChipDisplay()
                    triggerAutoSave()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupWordCounter() {
        binding.noteContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateWordCount()
                if (!isUndoRedoActive && loaded) {
                    pushHistorySnapshot()
                    applyLinkFormatting()
                }
            }
        })
    }

    private fun updateWordCount() {
        val text = binding.noteContent.text?.toString() ?: ""
        val words = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
        val chars = text.length
        binding.tvWordCount.text = "$words words • $chars chars"
    }

    private fun pushHistorySnapshot() {
        val current = binding.noteContent.text?.toString() ?: ""
        if (undoStack.isEmpty() || undoStack.peek() != current) {
            undoStack.push(current)
            if (undoStack.size > 40) undoStack.removeLast()
            redoStack.clear()
        }
    }

    private fun setupFormattingToolbar() {
        // Multimedia: Photos & Voice Memo
        binding.btnAddImage.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }
        binding.btnRecordVoice.setOnClickListener {
            handleRecordVoiceClick()
        }
        binding.btnStopRecording.setOnClickListener {
            stopVoiceRecording()
        }
        binding.btnVoicePlayPause.setOnClickListener {
            playVoiceMemo()
        }
        binding.btnVoiceDelete.setOnClickListener {
            deleteVoiceMemo()
        }

        // Undo / Redo
        binding.btnUndo.setOnClickListener {
            if (undoStack.size > 1) {
                isUndoRedoActive = true
                val current = undoStack.pop()
                redoStack.push(current)
                val prev = undoStack.peek() ?: ""
                binding.noteContent.setText(prev)
                binding.noteContent.setSelection(prev.length.coerceAtMost(binding.noteContent.text?.length ?: 0))
                isUndoRedoActive = false
                triggerAutoSave()
            } else {
                Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRedo.setOnClickListener {
            if (redoStack.isNotEmpty()) {
                isUndoRedoActive = true
                val next = redoStack.pop()
                undoStack.push(next)
                binding.noteContent.setText(next)
                binding.noteContent.setSelection(next.length.coerceAtMost(binding.noteContent.text?.length ?: 0))
                isUndoRedoActive = false
                triggerAutoSave()
            } else {
                Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show()
            }
        }

        // H1 Heading
        binding.btnH1.setOnClickListener {
            toggleHeading(1.4f)
        }

        // H2 Heading
        binding.btnH2.setOnClickListener {
            toggleHeading(1.2f)
        }

        // Bold
        binding.btnBold.setOnClickListener {
            toggleStyleSpan(Typeface.BOLD)
        }

        // Italic
        binding.btnItalic.setOnClickListener {
            toggleStyleSpan(Typeface.ITALIC)
        }

        // Underline
        binding.btnUnderline.setOnClickListener {
            toggleUnderlineSpan()
        }

        // Strikethrough
        binding.btnStrikethrough.setOnClickListener {
            toggleStrikethroughSpan()
        }

        // Highlight
        binding.btnHighlight.setOnClickListener {
            showHighlightColorDialog()
        }

        // Text Color
        binding.btnTextColor.setOnClickListener {
            showTextColorDialog()
        }

        // Bullet list
        binding.btnBullet.setOnClickListener {
            insertBulletPoint()
        }
    }

    private fun getSelectionRange(): Pair<Int, Int> {
        val start = binding.noteContent.selectionStart
        val end = binding.noteContent.selectionEnd
        return if (start < end) Pair(start, end) else Pair(end, start)
    }

    private fun toggleStyleSpan(style: Int) {
        val (start, end) = getSelectionRange()
        val editable = binding.noteContent.text ?: return
        if (start == end) {
            Toast.makeText(this, "Select text to format", Toast.LENGTH_SHORT).show()
            return
        }
        val spans = editable.getSpans(start, end, StyleSpan::class.java)
        val existing = spans.firstOrNull { it.style == style }
        if (existing != null) {
            editable.removeSpan(existing)
        } else {
            editable.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        triggerAutoSave()
    }

    private fun toggleUnderlineSpan() {
        val (start, end) = getSelectionRange()
        val editable = binding.noteContent.text ?: return
        if (start == end) {
            Toast.makeText(this, "Select text to underline", Toast.LENGTH_SHORT).show()
            return
        }
        val spans = editable.getSpans(start, end, UnderlineSpan::class.java)
        if (spans.isNotEmpty()) {
            spans.forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        triggerAutoSave()
    }

    private fun toggleStrikethroughSpan() {
        val (start, end) = getSelectionRange()
        val editable = binding.noteContent.text ?: return
        if (start == end) {
            Toast.makeText(this, "Select text to strikethrough", Toast.LENGTH_SHORT).show()
            return
        }
        val spans = editable.getSpans(start, end, StrikethroughSpan::class.java)
        if (spans.isNotEmpty()) {
            spans.forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        triggerAutoSave()
    }

    private fun showHighlightColorDialog() {
        val (start, end) = getSelectionRange()
        val editable = binding.noteContent.text ?: return
        if (start == end) {
            Toast.makeText(this, "Select text to highlight", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf(
            "💛 Yellow",
            "💚 Mint Green",
            "💖 Soft Pink",
            "💙 Sky Blue",
            "🧡 Peach Orange",
            "❌ Remove Highlight"
        )
        val colors = intArrayOf(
            ContextCompat.getColor(this, R.color.highlight_yellow),
            ContextCompat.getColor(this, R.color.highlight_green),
            ContextCompat.getColor(this, R.color.highlight_pink),
            ContextCompat.getColor(this, R.color.highlight_blue),
            ContextCompat.getColor(this, R.color.highlight_orange)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Highlight Color")
            .setItems(options) { _, which ->
                val spans = editable.getSpans(start, end, BackgroundColorSpan::class.java)
                spans.forEach { editable.removeSpan(it) }
                if (which in colors.indices) {
                    editable.setSpan(
                        BackgroundColorSpan(colors[which]),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                triggerAutoSave()
            }
            .show()
    }

    private fun showTextColorDialog() {
        val (start, end) = getSelectionRange()
        val editable = binding.noteContent.text ?: return
        if (start == end) {
            Toast.makeText(this, "Select text to change color", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf(
            "🖤 Default Text",
            "🟢 Emerald Green",
            "🔵 Royal Blue",
            "🔴 Crimson Red",
            "🟣 Deep Purple"
        )
        val colors = intArrayOf(
            ContextCompat.getColor(this, R.color.on_surface),
            ContextCompat.getColor(this, R.color.text_color_emerald),
            ContextCompat.getColor(this, R.color.text_color_blue),
            ContextCompat.getColor(this, R.color.text_color_red),
            ContextCompat.getColor(this, R.color.text_color_purple)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Text Color")
            .setItems(options) { _, which ->
                val spans = editable.getSpans(start, end, ForegroundColorSpan::class.java)
                spans.forEach { editable.removeSpan(it) }
                if (which > 0 && which < colors.size) {
                    editable.setSpan(
                        ForegroundColorSpan(colors[which]),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                triggerAutoSave()
            }
            .show()
    }

    private fun toggleHeading(scale: Float) {
        val (start, end) = getSelectionRange()
        val editable = binding.noteContent.text ?: return
        if (start == end) {
            // Apply to the current line
            val textStr = editable.toString()
            val lineStart = textStr.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
            val lineEnd = textStr.indexOf('\n', start).let { if (it == -1) textStr.length else it }
            if (lineEnd > lineStart) {
                val spans = editable.getSpans(lineStart, lineEnd, RelativeSizeSpan::class.java)
                if (spans.isNotEmpty()) {
                    spans.forEach { editable.removeSpan(it) }
                } else {
                    editable.setSpan(RelativeSizeSpan(scale), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    editable.setSpan(StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } else {
            val spans = editable.getSpans(start, end, RelativeSizeSpan::class.java)
            if (spans.isNotEmpty()) {
                spans.forEach { editable.removeSpan(it) }
            } else {
                editable.setSpan(RelativeSizeSpan(scale), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        triggerAutoSave()
    }

    private fun insertBulletPoint() {
        val start = binding.noteContent.selectionStart
        val editable = binding.noteContent.text ?: return
        val textStr = editable.toString()
        val lineStart = textStr.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        editable.insert(lineStart, "• ")
        triggerAutoSave()
    }

    private fun buildColorRow() {
        val density = resources.displayMetrics.density
        binding.colorRow.removeAllViews()
        NOTE_COLORS.forEachIndexed { index, colorRes ->
            val dot = View(this).apply {
                val size = (34 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins((5 * density).toInt(), 0, (5 * density).toInt(), 0)
                }
                val dotColor = if (index == 0) {
                    ContextCompat.getColor(this@EditorActivity, R.color.surface)
                } else {
                    ContextCompat.getColor(this@EditorActivity, colorRes)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(dotColor)
                    setStroke((1 * density).toInt(), 0x33000000.toInt())
                }
                isClickable = true
                tag = index
                setOnClickListener {
                    selectedColor = index
                    highlightSelectedColor()
                    triggerAutoSave()
                }
            }
            binding.colorRow.addView(dot)
        }

        // Dedicated Custom Color Button (Apni Marzi)
        val customBtn = ImageView(this).apply {
            val size = (34 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins((5 * density).toInt(), 0, (8 * density).toInt(), 0)
            }
            setImageResource(R.drawable.ic_palette)
            setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFF1F5F9.toInt())
                setStroke((1.5f * density).toInt(), ContextCompat.getColor(this@EditorActivity, R.color.brand))
            }
            isClickable = true
            tag = -999
            contentDescription = "Custom Color"
            setOnClickListener {
                showCustomColorPickerDialog()
            }
        }
        binding.colorRow.addView(customBtn)

        highlightSelectedColor()
    }

    private fun showCustomColorPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val swatchesGrid = dialogView.findViewById<android.widget.GridLayout>(R.id.swatchesGrid)
        val livePreview = dialogView.findViewById<View>(R.id.liveColorPreview)
        val hexInput = dialogView.findViewById<EditText>(R.id.hexInput)

        val swatches = listOf(
            "#FFFFFF", "#FFF9C4", "#FFF59D", "#FFE082", "#FFCC80",
            "#FFAB91", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9",
            "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB",
            "#C8E6C9", "#DCEDC8", "#EFEBE9", "#CFD8DC", "#37474F"
        )

        var currentColor = resolveNoteColor(this, selectedColor)
        val density = resources.displayMetrics.density

        fun updatePreview(color: Int) {
            currentColor = color
            livePreview.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
                setStroke((1 * density).toInt(), 0x33000000.toInt())
            }
        }

        updatePreview(currentColor)
        val hexStr = String.format("#%06X", 0xFFFFFF and currentColor)
        hexInput.setText(hexStr)

        swatches.forEach { hex ->
            val swatchColor = android.graphics.Color.parseColor(hex)
            val dot = View(this).apply {
                val size = (34 * density).toInt()
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                }
                layoutParams = params
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(swatchColor)
                    setStroke((1 * density).toInt(), 0x33000000.toInt())
                }
                isClickable = true
                setOnClickListener {
                    updatePreview(swatchColor)
                    hexInput.setText(hex)
                }
            }
            swatchesGrid.addView(dot)
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                val text = s?.toString()?.trim() ?: ""
                if (text.startsWith("#") && (text.length == 7 || text.length == 9)) {
                    try {
                        val parsed = android.graphics.Color.parseColor(text)
                        updatePreview(parsed)
                    } catch (_: Exception) {}
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        MaterialAlertDialogBuilder(this)
            .setTitle("Custom Note Color")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val inputHex = hexInput.text.toString().trim()
                val finalColor = runCatching {
                    android.graphics.Color.parseColor(if (inputHex.startsWith("#")) inputHex else "#$inputHex")
                }.getOrDefault(currentColor)
                selectedColor = finalColor
                highlightSelectedColor()
                triggerAutoSave()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun highlightSelectedColor() {
        val bgColor = resolveNoteColor(this, selectedColor)
        binding.root.setBackgroundColor(bgColor)

        // Contrast adaptation for dark colors (charcoal / dark paper)
        val isDark = androidx.core.graphics.ColorUtils.calculateLuminance(bgColor) < 0.35
        val textColor = if (isDark) 0xFFFFFFFF.toInt() else ContextCompat.getColor(this, R.color.on_surface)
        val hintColor = if (isDark) 0xAAFFFFFF.toInt() else ContextCompat.getColor(this, R.color.text_secondary)
        binding.noteTitle.setTextColor(textColor)
        binding.noteTitle.setHintTextColor(hintColor)
        binding.noteContent.setTextColor(textColor)
        binding.noteContent.setHintTextColor(hintColor)
        val linkColor = if (isDark) 0xFF80D8FF.toInt() else ContextCompat.getColor(this, R.color.brand)
        binding.noteContent.setLinkTextColor(linkColor)

        val density = resources.displayMetrics.density
        for (i in 0 until binding.colorRow.childCount) {
            val dot = binding.colorRow.getChildAt(i)
            val tagVal = dot.tag as? Int ?: continue
            val selected = if (tagVal == -999) {
                selectedColor !in 0 until NOTE_COLORS.size
            } else {
                tagVal == selectedColor
            }
            dot.alpha = if (selected) 1f else 0.45f
            val gd = dot.background as? android.graphics.drawable.GradientDrawable
            if (selected) {
                gd?.setStroke((3 * density).toInt(), ContextCompat.getColor(this, R.color.brand))
            } else {
                gd?.setStroke((1 * density).toInt(), 0x33000000.toInt())
            }
        }
    }

    private fun attachAutoSave(view: EditText) {
        view.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                triggerAutoSave()
            }
        })
    }

    private fun triggerAutoSave() {
        if (loaded && prefs.autoSave) {
            saveHandler.removeCallbacks(autoSaveRunnable)
            saveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS)
        }
    }

    private fun applyLinkFormatting() {
        try {
            Linkify.addLinks(binding.noteContent, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
            val isDark = binding.noteTitle.currentTextColor == 0xFFFFFFFF.toInt()
            val linkColor = if (isDark) 0xFF80D8FF.toInt() else ContextCompat.getColor(this, R.color.brand)
            binding.noteContent.setLinkTextColor(linkColor)
        } catch (_: Exception) {}
    }

    inner class SmartTouchMovementMethod(private val onNonLinkTapped: (offset: Int) -> Unit) : LinkMovementMethod() {
        override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
            if (isEditMode) {
                // In Edit Mode, suppress link activation entirely! Allow normal typing & cursor placement.
                return false
            }
            val action = event.action
            if (action == MotionEvent.ACTION_UP) {
                val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY

                val layout = widget.layout
                if (layout != null) {
                    val line = layout.getLineForVertical(y)
                    val off = layout.getOffsetForHorizontal(line, x.toFloat())
                    val links = buffer.getSpans(off, off, URLSpan::class.java)

                    if (links.isNotEmpty()) {
                        return super.onTouchEvent(widget, buffer, event)
                    } else {
                        onNonLinkTapped(off)
                        return true
                    }
                }
            }
            return super.onTouchEvent(widget, buffer, event)
        }
    }

    private fun setupSeamlessTouch() {
        binding.noteContent.movementMethod = SmartTouchMovementMethod { tapOffset ->
            switchToEditMode(focusTitle = false, tapOffset = tapOffset)
        }

        binding.noteTitle.setOnClickListener {
            if (!isEditMode) {
                switchToEditMode(focusTitle = true)
            }
        }

        binding.root.setOnClickListener {
            if (!isEditMode) {
                switchToEditMode(focusTitle = false)
            }
        }
    }

    private fun switchToEditMode(focusTitle: Boolean = false, tapOffset: Int? = null) {
        if (isEditMode) return
        isEditMode = true
        applyModeUi()
        if (focusTitle) {
            binding.noteTitle.post {
                binding.noteTitle.requestFocus()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.noteTitle, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            binding.noteContent.post {
                binding.noteContent.requestFocus()
                if (tapOffset != null && tapOffset in 0..(binding.noteContent.text?.length ?: 0)) {
                    binding.noteContent.setSelection(tapOffset)
                }
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.noteContent, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun applyModeUi() {
        applyLinkFormatting()

        if (isEditMode) {
            binding.noteTitle.isFocusable = true
            binding.noteTitle.isFocusableInTouchMode = true
            binding.noteTitle.isCursorVisible = true

            binding.noteContent.isFocusable = true
            binding.noteContent.isFocusableInTouchMode = true
            binding.noteContent.isCursorVisible = true
            binding.noteContent.movementMethod = ArrowKeyMovementMethod.getInstance()

            binding.formattingToolbar.visibility = View.VISIBLE
        } else {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

            binding.noteTitle.isFocusable = false
            binding.noteTitle.isFocusableInTouchMode = false
            binding.noteTitle.isCursorVisible = false

            binding.noteContent.isFocusable = false
            binding.noteContent.isFocusableInTouchMode = false
            binding.noteContent.isCursorVisible = false
            binding.noteContent.movementMethod = SmartTouchMovementMethod { tapOffset ->
                switchToEditMode(focusTitle = false, tapOffset = tapOffset)
            }

            binding.formattingToolbar.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_local -> {
                isLocalNote = !isLocalNote
                updateLocalChipDisplay()
                Toast.makeText(
                    this,
                    if (isLocalNote) "Marked as Local (100% on-device)" else "Marked as Cloud note",
                    Toast.LENGTH_SHORT
                ).show()
                saveSilently()
                true
            }
            R.id.action_share -> { shareNote(); true }
            R.id.action_remind -> { pickReminder(); true }
            R.id.action_archive -> { archiveNote(); true }
            R.id.action_delete -> { confirmDeleteNote(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun archiveNote() {
        lifecycleScope.launch {
            if (noteId == null) {
                persist()
            }
            val currentId = noteId
            if (currentId != null) {
                repository.archive(currentId)
                Toast.makeText(this@EditorActivity, "Note archived", Toast.LENGTH_SHORT).show()
                if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try { cloudSyncManager.syncNow() } catch (_: Exception) {}
                    }
                }
                finish()
            }
        }
    }

    private fun confirmDeleteNote() {
        val currentId = noteId
        if (currentId == null) {
            isDeleted = true
            saveHandler.removeCallbacksAndMessages(null)
            finish()
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_note)
            .setMessage("Are you sure you want to delete this note? It will be removed across all synced devices.")
            .setPositiveButton(R.string.delete_note) { _, _ ->
                isDeleted = true
                saveHandler.removeCallbacksAndMessages(null)
                lifecycleScope.launch {
                    repository.delete(currentId)
                    if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try { cloudSyncManager.syncNow() } catch (_: Exception) {}
                        }
                    }
                    finish()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickReminder() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                TimePickerDialog(
                    this,
                    { _, h, min ->
                        val target = Calendar.getInstance()
                        target.set(y, m, d, h, min, 0)
                        val at = target.timeInMillis
                        if (at < System.currentTimeMillis()) {
                            Toast.makeText(this, R.string.reminder_past, Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }
                        scheduleNoteReminder(at)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun scheduleNoteReminder(at: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REMINDER_PERMISSION_CODE)
        }
        lifecycleScope.launch {
            if (noteId == null) {
                persist()
            }
            val id = noteId ?: return@launch
            val title = binding.noteTitle.text?.toString()?.trim().orEmpty()
            repository.setReminder(id, at)
            NoteReminderReceiver.schedule(this@EditorActivity, id, title, at)
            Toast.makeText(this@EditorActivity, getString(R.string.reminder_set), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareNote() {
        val title = binding.noteTitle.text?.toString()?.trim().orEmpty()
        val content = binding.noteContent.text?.toString().orEmpty()
        val text = buildString {
            if (title.isNotBlank()) append(title).append("\n\n")
            append(content)
        }
        if (text.isBlank()) {
            Toast.makeText(this, R.string.note_empty_share, Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_note)))
    }


    private fun saveAndFinish() {
        if (isDeleted) {
            finish()
            return
        }
        saveHandler.removeCallbacks(autoSaveRunnable)
        if (titleAndContentEmpty() && noteId == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            persist()
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        cloudSyncManager.syncNow()
                    } catch (_: Exception) {}
                }
            }
            finish()
        }
    }

    private fun saveSilently() {
        if (isDeleted) return
        if (titleAndContentEmpty() && noteId == null) return
        lifecycleScope.launch {
            persist()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isDeleted && loaded && !titleAndContentEmpty()) {
            lifecycleScope.launch {
                persist()
            }
        }
    }

    private suspend fun persist() {
        val title = binding.noteTitle.text?.toString()?.trim() ?: ""
        val editable = binding.noteContent.text
        val content = if (editable != null && containsFormattingSpans(editable)) {
            Html.toHtml(editable, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        } else {
            editable?.toString() ?: ""
        }
        val folder = selectedFolder.trim()
        val tags = selectedTags

        if (noteId != null) {
            repository.update(
                id = noteId!!,
                title = title,
                content = content,
                color = selectedColor,
                folder = folder,
                tags = tags,
                isLocal = isLocalNote,
                images = attachedImages,
                audioUri = attachedAudioUri,
                audioDuration = attachedAudioDuration
            )
        } else {
            val created = repository.create(
                title = title,
                content = content,
                color = selectedColor,
                folder = folder,
                tags = tags,
                isLocal = isLocalNote,
                images = attachedImages,
                audioUri = attachedAudioUri,
                audioDuration = attachedAudioDuration
            )
            noteId = created.id
        }
    }

    private fun containsFormattingSpans(editable: Editable): Boolean {
        val spans = editable.getSpans(0, editable.length, CharacterStyle::class.java)
        return spans.isNotEmpty()
    }

    private fun titleAndContentEmpty(): Boolean =
        binding.noteTitle.text?.isNullOrEmpty() != false &&
            binding.noteContent.text?.isNullOrEmpty() != false &&
            attachedImages.isEmpty() &&
            attachedAudioUri.isNullOrEmpty()

    private fun updateLocalChipDisplay() {
        binding.chipLocal.visibility = if (isLocalNote) View.VISIBLE else View.GONE
    }

    private fun copyUriToLocalMedia(uri: Uri): String? {
        return try {
            val mediaDir = File(filesDir, "media/images").apply { mkdirs() }
            val destFile = File(mediaDir, "img_${System.currentTimeMillis()}_${(100..999).random()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun renderAttachedImages() {
        binding.imagesContainer.removeAllViews()
        if (attachedImages.isEmpty()) {
            binding.imagesScrollView.visibility = View.GONE
            return
        }
        binding.imagesScrollView.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        val itemSize = (90 * density).toInt()
        val margin = (4 * density).toInt()

        attachedImages.forEachIndexed { index, path ->
            val frame = android.widget.FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(itemSize, itemSize).apply {
                    setMargins(margin, margin, margin, margin)
                }
            }

            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
                radius = 12 * density
                cardElevation = 1 * density
                strokeWidth = (1 * density).toInt()
                strokeColor = ContextCompat.getColor(this@EditorActivity, R.color.card_stroke)
            }

            val iv = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                var scale = 1
                while (opts.outWidth / scale / 2 >= 180 && opts.outHeight / scale / 2 >= 180) {
                    scale *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = scale }
                val bmp = BitmapFactory.decodeFile(path, decodeOpts)
                iv.setImageBitmap(bmp)
            } catch (_: Exception) {}

            card.addView(iv)
            frame.addView(card)

            val btnRemove = ImageView(this).apply {
                val btnSize = (24 * density).toInt()
                layoutParams = android.widget.FrameLayout.LayoutParams(btnSize, btnSize).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                }
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundResource(R.drawable.circle_bg_translucent)
                setColorFilter(0xFFFFFFFF.toInt())
                setPadding((3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt())
                setOnClickListener {
                    attachedImages.removeAt(index)
                    try { File(path).delete() } catch (_: Exception) {}
                    renderAttachedImages()
                    saveSilently()
                }
            }
            frame.addView(btnRemove)

            binding.imagesContainer.addView(frame)
        }
    }

    private fun renderVoiceMemoBar() {
        val uri = attachedAudioUri
        if (uri.isNullOrBlank() || !File(uri).exists()) {
            binding.voiceMemoBar.visibility = View.GONE
            return
        }
        binding.voiceMemoBar.visibility = View.VISIBLE
        binding.tvVoiceTimer.text = "Voice Memo (${formatDuration(attachedAudioDuration)})"
        binding.btnVoicePlayPause.setImageResource(
            if (isPlayingVoice) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun handleRecordVoiceClick() {
        if (isRecording) {
            stopVoiceRecording()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording()
            } else {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startVoiceRecording() {
        try {
            val audioDir = File(filesDir, "media/audio").apply { mkdirs() }
            val file = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = SystemClock.elapsedRealtime()
            binding.recordingLiveBar.visibility = View.VISIBLE
            startRecordingTimer()
        } catch (e: Exception) {
            isRecording = false
            Toast.makeText(this, "Could not start recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoiceRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        stopRecordingTimer()
        binding.recordingLiveBar.visibility = View.GONE

        val duration = SystemClock.elapsedRealtime() - recordingStartTime
        currentRecordingFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                attachedAudioUri = file.absolutePath
                attachedAudioDuration = duration
                renderVoiceMemoBar()
                saveSilently()
                Toast.makeText(this, "Voice memo saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecordingTimer() {
        recordingTimerHandler.post(object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = SystemClock.elapsedRealtime() - recordingStartTime
                    binding.tvRecordingDuration.text = "Recording Voice... ${formatDuration(elapsed)}"
                    recordingTimerHandler.postDelayed(this, 500)
                }
            }
        })
    }

    private fun stopRecordingTimer() {
        recordingTimerHandler.removeCallbacksAndMessages(null)
    }

    private fun playVoiceMemo() {
        val uri = attachedAudioUri ?: return
        val file = File(uri)
        if (!file.exists()) return

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPlayingVoice = false
            binding.btnVoicePlayPause.setImageResource(R.drawable.ic_play)
            return
        }

        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        isPlayingVoice = false
                        binding.btnVoicePlayPause.setImageResource(R.drawable.ic_play)
                        binding.tvVoiceTimer.text = "Voice Memo (${formatDuration(attachedAudioDuration)})"
                    }
                }
            }
            mediaPlayer?.start()
            isPlayingVoice = true
            binding.btnVoicePlayPause.setImageResource(R.drawable.ic_pause)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play audio: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteVoiceMemo() {
        AlertDialog.Builder(this)
            .setTitle("Delete Voice Memo")
            .setMessage("Delete this recorded voice memo?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                } catch (_: Exception) {}
                mediaPlayer = null
                isPlayingVoice = false

                attachedAudioUri?.let { File(it).delete() }
                attachedAudioUri = null
                attachedAudioDuration = 0L
                renderVoiceMemoBar()
                saveSilently()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        recordingTimerHandler.removeCallbacksAndMessages(null)
        saveHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
