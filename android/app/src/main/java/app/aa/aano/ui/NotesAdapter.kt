// RecyclerView adapter for the notes list.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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
        val badgeAudio: ImageView? = itemView.findViewById(R.id.badgeAudio)
        val badgeImageLayout: View? = itemView.findViewById(R.id.badgeImageLayout)
        val badgeImageCount: TextView? = itemView.findViewById(R.id.badgeImageCount)
        val badgeLocal: ImageView? = itemView.findViewById(R.id.badgeLocal)
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

        // Media and Local badges
        holder.badgeAudio?.visibility = if (!note.audioUri.isNullOrBlank()) View.VISIBLE else View.GONE
        if (note.images.isNotEmpty()) {
            holder.badgeImageLayout?.visibility = View.VISIBLE
            holder.badgeImageCount?.text = note.images.size.toString()
        } else {
            holder.badgeImageLayout?.visibility = View.GONE
        }
        holder.badgeLocal?.visibility = View.GONE

        // Folder pill badge
        if (note.folder.isNotBlank()) {
            holder.folderBadge?.visibility = View.VISIBLE
            holder.folderBadge?.text = note.folder
        } else {
            holder.folderBadge?.visibility = View.GONE
        }

        // Note card background color with resolveNoteColor & contrast adaptation
        val cardBg = EditorActivity.resolveNoteColor(holder.itemView.context, note.color)
        holder.card.setCardBackgroundColor(cardBg)

        val isDark = ColorUtils.calculateLuminance(cardBg) < 0.35
        val titleColor = if (isDark) 0xFFFFFFFF.toInt() else ContextCompat.getColor(holder.itemView.context, R.color.on_surface)
        val previewColor = if (isDark) 0xCCFFFFFF.toInt() else ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
        val dateColor = if (isDark) 0x99FFFFFF.toInt() else ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
        holder.title.setTextColor(titleColor)
        holder.preview.setTextColor(previewColor)
        holder.date.setTextColor(dateColor)

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