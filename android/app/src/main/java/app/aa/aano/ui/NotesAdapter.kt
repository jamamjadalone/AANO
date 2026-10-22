// RecyclerView adapter for the notes list.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.aa.aano.R
import app.aa.aano.data.Note
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotesAdapter(
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note) -> Unit,
    var onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    companion object {
        fun formatFriendlyDate(timestamp: Long): String {
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = timestamp }
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val fullFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val monthFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

            val isToday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

            val isYesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1

            return when {
                isToday -> "Today, ${timeFormat.format(Date(timestamp))}"
                isYesterday -> "Yesterday, ${timeFormat.format(Date(timestamp))}"
                now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> "${monthFormat.format(Date(timestamp))}, ${timeFormat.format(Date(timestamp))}"
                else -> fullFormat.format(Date(timestamp))
            }
        }
    }

    private var notes: List<Note> = emptyList()
    private val selectedIds = mutableSetOf<String>()
    var isSelectionMode: Boolean = false
        set(value) {
            field = value
            if (!value) {
                selectedIds.clear()
            }
            notifyDataSetChanged()
        }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        if (selectedIds.isEmpty()) {
            isSelectionMode = false
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(notes.map { it.id })
        isSelectionMode = true
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.noteCard)
        val title: TextView = itemView.findViewById(R.id.noteTitle)
        val preview: TextView = itemView.findViewById(R.id.notePreview)
        val date: TextView = itemView.findViewById(R.id.noteDate)
        val pinned: ImageView = itemView.findViewById(R.id.notePinned)
        val checkbox: ImageView = itemView.findViewById(R.id.noteCheckbox)
        val folderBadge: TextView? = itemView.findViewById(R.id.noteFolderBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun getItemCount(): Int = notes.size

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.title.text = note.title.ifBlank { "(No title)" }
        val rawContent = note.content
        holder.preview.text = if (rawContent.contains("<") && rawContent.contains(">")) {
            android.text.Html.fromHtml(rawContent, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim().ifBlank { "(Empty note)" }
        } else {
            rawContent.ifBlank { "(Empty note)" }
        }
        holder.date.text = formatFriendlyDate(note.updatedAt)
        holder.pinned.visibility = if (note.pinned) View.VISIBLE else View.GONE

        // Folder pill badge
        if (note.folder.isNotBlank()) {
            holder.folderBadge?.visibility = View.VISIBLE
            holder.folderBadge?.text = note.folder
        } else {
            holder.folderBadge?.visibility = View.GONE
        }

        // Note pastel card background color (0 = default/surface).
        val colorRes = if (note.color in 1 until EditorActivity.NOTE_COLORS.size) {
            EditorActivity.NOTE_COLORS[note.color]
        } else R.color.card_surface

        val cardBg = androidx.core.content.ContextCompat.getColor(
            holder.itemView.context, colorRes
        )
        holder.card.setCardBackgroundColor(cardBg)

        // Selection state
        val isSelected = selectedIds.contains(note.id)
        if (isSelectionMode) {
            holder.checkbox.visibility = View.VISIBLE
            if (isSelected) {
                holder.checkbox.alpha = 1.0f
                holder.card.strokeColor = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.brand)
                holder.card.strokeWidth = (2.5f * holder.itemView.resources.displayMetrics.density).toInt()
            } else {
                holder.checkbox.alpha = 0.3f
                holder.card.strokeColor = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.card_stroke)
                holder.card.strokeWidth = (1 * holder.itemView.resources.displayMetrics.density).toInt()
            }
        } else {
            holder.checkbox.visibility = View.GONE
            holder.card.strokeColor = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.card_stroke)
            holder.card.strokeWidth = (1 * holder.itemView.resources.displayMetrics.density).toInt()
        }

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(note.id)
            } else {
                onClick(note)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (isSelectionMode) {
                toggleSelection(note.id)
            } else {
                onLongClick(note)
            }
            true
        }
    }

    fun submitList(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }
}