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
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
import java.util.ArrayDeque
import java.util.Calendar

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_FOLDER = "extra_folder"
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
            R.color.note_color_red
        )
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

    private val saveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable {
        if (loaded) saveSilently()
    }

    // Undo / Redo history stacks
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var isUndoRedoActive = false

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
                    updateFolderChipDisplay()
                    selectedColor = note.color
                    highlightSelectedColor()
                    updateWordCount()
                    pushHistorySnapshot()
                } else {
                    noteId = null
                    pushHistorySnapshot()
                }
                loaded = true
            }
        } ?: run {
            selectedFolder = intent.getStringExtra(EXTRA_FOLDER) ?: ""
            loaded = true
            updateFolderChipDisplay()
            updateWordCount()
            pushHistorySnapshot()
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
                    setMargins((6 * density).toInt(), 0, (6 * density).toInt(), 0)
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
        highlightSelectedColor()
    }

    private fun highlightSelectedColor() {
        val colorRes = if (selectedColor in 1 until NOTE_COLORS.size) {
            NOTE_COLORS[selectedColor]
        } else {
            R.color.surface
        }
        val bgColor = ContextCompat.getColor(this, colorRes)
        binding.root.setBackgroundColor(bgColor)

        val density = resources.displayMetrics.density
        for (i in 0 until binding.colorRow.childCount) {
            val dot = binding.colorRow.getChildAt(i)
            val selected = (dot.tag as Int) == selectedColor
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

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> { shareNote(); true }
            R.id.action_remind -> { pickReminder(); true }
            else -> super.onOptionsItemSelected(item)
        }
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

    override fun onDestroy() {
        saveHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun saveAndFinish() {
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
        if (titleAndContentEmpty() && noteId == null) return
        lifecycleScope.launch {
            persist()
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        cloudSyncManager.syncNow()
                    } catch (_: Exception) {}
                }
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
            repository.update(noteId!!, title, content, color = selectedColor, folder = folder, tags = tags)
        } else {
            val created = repository.create(
                title, content, color = selectedColor, folder = folder, tags = tags
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
            binding.noteContent.text?.isNullOrEmpty() != false
}
