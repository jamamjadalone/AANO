// QuickNoteActivity: Lightweight floating dialog for instant note capturing.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.aa.aano.R
import app.aa.aano.data.Note
import app.aa.aano.data.NoteRepository
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.UUID

class QuickNoteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_note)

        val etTitle = findViewById<EditText>(R.id.etQuickTitle)
        val etContent = findViewById<EditText>(R.id.etQuickContent)
        val btnClose = findViewById<ImageButton>(R.id.btnCloseQuick)
        val btnDiscard = findViewById<MaterialButton>(R.id.btnQuickDiscard)
        val btnSave = findViewById<MaterialButton>(R.id.btnQuickSave)

        btnClose.setOnClickListener { finish() }
        btnDiscard.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val content = etContent.text.toString().trim()

            if (title.isEmpty() && content.isEmpty()) {
                finish()
                return@setOnClickListener
            }

            val repo = NoteRepository(this)
            val now = System.currentTimeMillis()
            val note = Note(
                id = UUID.randomUUID().toString(),
                title = if (title.isNotEmpty()) title else "Untitled Note",
                content = content,
                folder = "General",
                color = 0,
                pinned = false,
                isLocal = false,
                createdAt = now,
                updatedAt = now
            )
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                repo.upsertNote(note)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@QuickNoteActivity, "Note saved to AANO", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
