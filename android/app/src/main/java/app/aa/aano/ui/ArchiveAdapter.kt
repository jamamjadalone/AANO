// Adapter for archived notes.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.aa.aano.R
import app.aa.aano.data.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchiveAdapter(
    private val onClick: (Note) -> Unit,
    private val onUnarchive: (Note) -> Unit,
    private val onDelete: (Note) -> Unit
) : RecyclerView.Adapter<ArchiveAdapter.ArchiveViewHolder>() {

    private var items: List<Note> = emptyList()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

    class ArchiveViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.archiveCard)
        val title: TextView = view.findViewById(R.id.archiveTitle)
        val date: TextView = view.findViewById(R.id.archiveDate)
        val folderBadge: TextView = view.findViewById(R.id.archiveFolderBadge)
        val btnUnarchive: ImageButton = view.findViewById(R.id.btnUnarchive)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArchiveViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_archive, parent, false)
        return ArchiveViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ArchiveViewHolder, position: Int) {
        val note = items[position]
        holder.title.text = note.title.ifBlank { "(No title)" }
        holder.date.text = "Updated: ${dateFormat.format(Date(note.updatedAt))}"

        val cardBg = EditorActivity.resolveNoteColor(holder.itemView.context, note.color)
        holder.card.setCardBackgroundColor(cardBg)

        val isDark = androidx.core.graphics.ColorUtils.calculateLuminance(cardBg) < 0.35
        val textColor = if (isDark) 0xFFFFFFFF.toInt() else androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.on_surface)
        val dateColor = if (isDark) 0xAAFFFFFF.toInt() else androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
        holder.title.setTextColor(textColor)
        holder.date.setTextColor(dateColor)

        if (note.folder.isNotBlank()) {
            holder.folderBadge.visibility = View.VISIBLE
            holder.folderBadge.text = note.folder
        } else {
            holder.folderBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(note) }
        holder.btnUnarchive.setOnClickListener { onUnarchive(note) }
        holder.btnDelete.setOnClickListener { onDelete(note) }
    }

    fun submitList(newItems: List<Note>) {
        items = newItems
        notifyDataSetChanged()
    }
}
