// RecyclerView adapter for the trash list.
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

class TrashAdapter(
    private val onRestore: (Note) -> Unit,
    private val onPurge: (Note) -> Unit
) : RecyclerView.Adapter<TrashAdapter.TrashViewHolder>() {

    private var notes: List<Note> = emptyList()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    class TrashViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.trashTitle)
        val date: TextView = itemView.findViewById(R.id.trashDate)
        val restore: ImageButton = itemView.findViewById(R.id.btnRestore)
        val purge: ImageButton = itemView.findViewById(R.id.btnPurge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trash, parent, false)
        return TrashViewHolder(view)
    }

    override fun getItemCount(): Int = notes.size

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        val note = notes[position]
        holder.title.text = note.title.ifBlank { "(No title)" }
        holder.date.text = itemViewDate(note.deletedAt)
        holder.restore.setOnClickListener { onRestore(note) }
        holder.purge.setOnClickListener { onPurge(note) }
    }

    fun submitList(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    private fun itemViewDate(ts: Long): String =
        if (ts > 0) dateFormat.format(Date(ts)) else ""
}