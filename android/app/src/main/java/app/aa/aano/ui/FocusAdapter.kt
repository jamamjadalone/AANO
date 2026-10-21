// RecyclerView adapter for the Focus tab.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.aa.aano.R
import app.aa.aano.data.Task

class FocusAdapter(
    private val onToggle: (Task) -> Unit,
    private val onHide: (Task) -> Unit
) : RecyclerView.Adapter<FocusAdapter.FocusViewHolder>() {

    private var tasks: List<Task> = emptyList()

    class FocusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val check: CheckBox = itemView.findViewById(R.id.focusCheck)
        val title: TextView = itemView.findViewById(R.id.focusTitle)
        val hide: ImageButton = itemView.findViewById(R.id.btnFocusHide)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FocusViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_focus, parent, false)
        return FocusViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: FocusViewHolder, position: Int) {
        val task = tasks[position]
        holder.title.text = task.title.ifBlank { "(Untitled)" }
        holder.check.isChecked = false
        holder.check.setOnClickListener { holder.check.isChecked = false; onToggle(task) }
        holder.hide.setOnClickListener { onHide(task) }
    }

    fun submitList(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}