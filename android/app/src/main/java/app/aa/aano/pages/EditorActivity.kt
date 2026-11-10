// Note editor screen for AANO with rich text formatting toolbar and auto-save.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.pages

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.aa.aano.R
import app.aa.aano.components.editor.*
import app.aa.aano.data.EditorPersistenceDelegate
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.ActivityEditorBinding
import app.aa.aano.net.CloudSyncManager

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_FOLDER = "extra_folder"
        const val EXTRA_IS_LOCAL = "extra_is_local"
        const val EXTRA_COLOR = "extra_color"
        private const val AUTO_SAVE_DELAY_MS = 600L
        private const val REMINDER_PERMISSION_CODE = 1002
        val NOTE_COLORS: List<Int> get() = EditorColorPickerHelper.NOTE_COLORS
        fun presetColors(): List<Int> = EditorColorPickerHelper.NOTE_COLORS
        fun resolveNoteColor(context: android.content.Context, colorValue: Int): Int =
            EditorColorPickerHelper.resolveNoteColor(context, colorValue)
    }

    private lateinit var binding: ActivityEditorBinding
    private lateinit var repository: NoteRepository
    private lateinit var prefs: Prefs
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var audioHelper: EditorAudioHelper
    private lateinit var imageCoordinator: EditorImageCoordinator
    private val undoRedoManager = EditorUndoRedoManager()
    private var noteId: String? = null
    private var loaded = false
    private var selectedColor = 0
    private var selectedFolder = ""
    private var selectedTags = mutableListOf<String>()
    private var isLocalNote: Boolean = false
    private val attachedImages = mutableListOf<String>()
    private var attachedAudioUri: String? = null
    private var attachedAudioDuration: Long = 0L
    private var isDeleted = false
    private var isEditMode = true
    private val saveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable { if (loaded) saveSilently() }

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { res ->
        res.contents?.takeIf { it.isNotBlank() }?.let {
            EditorDropHelper.handleScannedQr(this, lifecycleScope, prefs, it, buildCurrentNote())
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) audioHelper.startVoiceRecording()
        else Toast.makeText(this, "Microphone permission required for voice memo", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { saveAndFinish() }
        })

        repository = NoteRepository(this)
        prefs = Prefs(this)
        cloudSyncManager = CloudSyncManager(this, repository, TaskRepository(this))
        noteId = intent.getStringExtra(EXTRA_NOTE_ID)

        binding.toolbar.setNavigationOnClickListener { saveAndFinish() }

        setupHelpers()
        setupFolderChip()
        setupWordCounter()
        EditorTouchHelper.setupSeamlessTouch(binding, { isEditMode }, { ft, off ->
            EditorTouchHelper.switchToEditMode(this, binding, ft, off) { isEditMode = true }
        })
        attachAutoSave(binding.noteTitle); attachAutoSave(binding.noteContent)
        loadInitialNote()
        if (intent.getBooleanExtra("EXTRA_START_VOICE", false)) binding.btnRecordVoice.performClick()
    }

    private fun setupHelpers() {
        imageCoordinator = EditorImageCoordinator(this, binding, attachedImages) { saveSilently() }
        audioHelper = EditorAudioHelper(this, binding) { uri, dur ->
            attachedAudioUri = uri; attachedAudioDuration = dur; saveSilently()
        }

        EditorFormatBarHelper.setupToolbar(this, binding, undoRedoManager) {
            updateWordCount(); triggerAutoSave()
        }

        binding.btnAddImage.setOnClickListener { imageCoordinator.pickImages() }
        binding.btnToggleQuality.setOnClickListener { imageCoordinator.toggleQuality(binding.btnToggleQuality, this) }
        binding.btnRecordVoice.setOnClickListener { audioHelper.handleRecordVoiceClick(requestAudioPermissionLauncher) }
        binding.btnStopRecording.setOnClickListener { audioHelper.stopVoiceRecording() }
        binding.btnVoicePlayPause.setOnClickListener { audioHelper.playVoiceMemo() }
        binding.btnVoiceDelete.setOnClickListener { audioHelper.deleteVoiceMemo() }

        EditorColorPickerHelper.buildColorRow(this, binding.colorRow, prefs, selectedColor) { color ->
            selectedColor = color
            EditorColorPickerHelper.applyNoteColorTheme(this, binding.root, binding.noteTitle, binding.noteContent, color)
            triggerAutoSave()
        }
    }

    private fun loadInitialNote() {
        noteId?.let { id ->
            lifecycleScope.launch {
                val note = repository.getById(id)
                if (note != null) {
                    binding.noteTitle.setText(note.title)
                    binding.noteContent.setText(if (note.content.contains("<") && note.content.contains(">")) Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT) else note.content)
                    selectedFolder = note.folder; selectedTags = note.tags.toMutableList(); isLocalNote = note.isLocal
                    attachedImages.clear(); attachedImages.addAll(note.images)
                    attachedAudioUri = note.audioUri; attachedAudioDuration = note.audioDuration
                    audioHelper.setInitialAudio(note.audioUri, note.audioDuration)
                    updateFolderChipDisplay(); imageCoordinator.render()
                    selectedColor = note.color
                    EditorColorPickerHelper.applyNoteColorTheme(this@EditorActivity, binding.root, binding.noteTitle, binding.noteContent, selectedColor)
                    EditorColorPickerHelper.highlightSelectedColor(binding.colorRow, this@EditorActivity, selectedColor)
                    updateWordCount(); undoRedoManager.pushState(binding.noteContent.text?.toString().orEmpty())
                    isEditMode = false; applyModeUi()
                } else {
                    noteId = null; undoRedoManager.pushState(""); isEditMode = true; applyModeUi()
                }
                loaded = true
            }
        } ?: run {
            selectedFolder = intent.getStringExtra(EXTRA_FOLDER).orEmpty()
            isLocalNote = intent.getBooleanExtra(EXTRA_IS_LOCAL, false)
            loaded = true; isEditMode = true
            updateFolderChipDisplay(); updateWordCount(); undoRedoManager.pushState(""); applyModeUi()
            binding.noteTitle.post {
                binding.noteTitle.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(binding.noteTitle, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun setupFolderChip() {
        updateFolderChipDisplay()
        binding.chipFolder.setOnClickListener {
            EditorDialogHelper.showFolderPickerDialog(this, lifecycleScope, repository, { f -> selectedFolder = f; updateFolderChipDisplay(); triggerAutoSave() },
                { EditorDialogHelper.showCreateFolderDialog(this) { name -> selectedFolder = name; updateFolderChipDisplay(); triggerAutoSave() } })
        }
    }

    private fun updateFolderChipDisplay() {
        binding.chipFolder.text = if (selectedFolder.isBlank()) getString(R.string.folder_all) else selectedFolder
        binding.chipLocal.visibility = View.GONE
    }

    private fun setupWordCounter() {
        binding.noteContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateWordCount(); undoRedoManager.pushState(s?.toString().orEmpty())
            }
        })
    }

    private fun updateWordCount() {
        val text = binding.noteContent.text?.toString().orEmpty().trim()
        val words = if (text.isBlank()) 0 else text.split(Regex("\\s+")).count { it.isNotBlank() }
        binding.tvWordCount.text = "$words words • ${text.length} chars"
    }

    private fun attachAutoSave(view: EditText) {
        view.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { triggerAutoSave() }
        })
    }

    private fun triggerAutoSave() {
        if (loaded && prefs.autoSave) {
            saveHandler.removeCallbacks(autoSaveRunnable)
            saveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS)
        }
    }

    private fun applyModeUi() {
        EditorTouchHelper.applyModeUi(this, binding, isEditMode) { ft, off ->
            EditorTouchHelper.switchToEditMode(this, binding, ft, off) { isEditMode = true }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        EditorMenuHelper.inflateMenu(this, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return EditorMenuHelper.handleOptionsItemSelected(item,
            onShare = { EditorDialogHelper.shareNote(this, binding.noteTitle.text.toString().trim(), binding.noteContent.text.toString()) },
            onRemind = { handleRemindAction() },
            onArchive = { lifecycleScope.launch { if (noteId == null) persist(); EditorDialogHelper.archiveNote(this@EditorActivity, lifecycleScope, repository, prefs, cloudSyncManager, noteId) { finish() } } },
            onDrop = { EditorDropHelper.showDropDialog(this, lifecycleScope, prefs, { buildCurrentNote() }, { EditorDropHelper.launchQrScanner(qrScannerLauncher) }) },
            onExport = { EditorExportHelper.showExportDialog(this, buildCurrentNote(), cacheDir) },
            onDelete = { EditorDialogHelper.confirmDeleteNote(this, lifecycleScope, repository, prefs, cloudSyncManager, noteId, isLocalNote) { isDeleted = true; saveHandler.removeCallbacksAndMessages(null); finish() } }
        ) || super.onOptionsItemSelected(item)
    }

    private fun handleRemindAction() {
        EditorDialogHelper.pickReminder(this) { at ->
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REMINDER_PERMISSION_CODE)
            }
            lifecycleScope.launch {
                if (noteId == null) persist()
                noteId?.let { EditorDialogHelper.scheduleNoteReminder(this@EditorActivity, lifecycleScope, repository, it, binding.noteTitle.text.toString().trim(), at) }
            }
        }
    }

    fun buildCurrentNote(): Note =
        EditorPersistenceDelegate.buildNote(noteId, binding.noteTitle.text?.toString().orEmpty(), binding.noteContent.text?.toString().orEmpty(),
            selectedFolder, selectedColor, attachedImages, attachedAudioUri, attachedAudioDuration, isLocalNote)

    private fun saveAndFinish() {
        if (isDeleted) { finish(); return }
        saveHandler.removeCallbacks(autoSaveRunnable)
        if (EditorPersistenceDelegate.isNoteEmpty(binding.noteTitle.text, binding.noteContent.text, attachedImages, attachedAudioUri) && noteId == null) {
            finish(); return
        }
        lifecycleScope.launch {
            persist()
            if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
                CoroutineScope(Dispatchers.IO).launch {
                    try { cloudSyncManager.syncNow() } catch (e: Exception) { Log.d("EditorActivity", "Auto-sync skipped: ${e.message}") }
                }
            }
            finish()
        }
    }

    private fun saveSilently() {
        if (isDeleted || (EditorPersistenceDelegate.isNoteEmpty(binding.noteTitle.text, binding.noteContent.text, attachedImages, attachedAudioUri) && noteId == null)) return
        lifecycleScope.launch { persist() }
    }
    override fun onPause() {
        super.onPause()
        audioHelper.onPauseCleanup()
        if (!isDeleted && loaded && !EditorPersistenceDelegate.isNoteEmpty(binding.noteTitle.text, binding.noteContent.text, attachedImages, attachedAudioUri)) {
            lifecycleScope.launch { persist() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveHandler.removeCallbacksAndMessages(null)
        audioHelper.onDestroyCleanup()
    }

    private suspend fun persist() {
        noteId = EditorPersistenceDelegate.persistNote(
            repository, noteId, binding.noteTitle.text?.toString(), binding.noteContent.text,
            selectedFolder, selectedTags, selectedColor, isLocalNote, attachedImages, attachedAudioUri, attachedAudioDuration
        )
    }
}
