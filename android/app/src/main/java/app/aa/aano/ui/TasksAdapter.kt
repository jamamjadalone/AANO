// RecyclerView adapter for the tasks list.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.aa.aano.R
import app.aa.aano.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TasksAdapter(
    private val onToggle: (Task) -> Unit,
    private val onClick: (Task) -> Unit,
    private val onLongClick: (Task) -> Unit
) : RecyclerView.Adapter<TasksAdapter.TaskViewHolder>() {

    private var tasks: List<Task> = emptyList()

    private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val check: CheckBox = itemView.findViewById(R.id.taskCheck)
        val title: TextView = itemView.findViewById(R.id.taskTitle)
        val prio: TextView = itemView.findViewById(R.id.taskPrio)
        val due: TextView = itemView.findViewById(R.id.taskDue)
        val subtasks: TextView = itemView.findViewById(R.id.taskSubtasks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.title.text = task.title.ifBlank { "(Untitled)" }
        holder.check.isChecked = task.completed
        if (task.completed) {
            holder.title.paintFlags = holder.title.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.title.paintFlags = holder.title.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        holder.prio.text = when (task.priority) {
            app.aa.aano.data.TaskPriority.HIGH -> holder.itemView.context.getString(R.string.prio_high).uppercase()
            app.aa.aano.data.TaskPriority.LOW -> holder.itemView.context.getString(R.string.prio_low).uppercase()
            else -> holder.itemView.context.getString(R.string.prio_medium).uppercase()
        }
        holder.prio.setTextColor(
            when (task.priority) {
                app.aa.aano.data.TaskPriority.HIGH -> 0xFFD32F2F.toInt()
                app.aa.aano.data.TaskPriority.LOW -> 0xFF388E3C.toInt()
                else -> 0xFFF57C00.toInt()
            }
        )

        if (task.dueAt != null) {
            val overdue = task.dueAt < System.currentTimeMillis() && !task.completed
            holder.due.visibility = View.VISIBLE
            holder.due.text = holder.itemView.context.getString(
                R.string.overdue
            ).let { if (overdue) "$it " else "" } + dateFormat.format(Date(task.dueAt))
            holder.due.setTextColor(if (overdue) 0xFFD32F2F.toInt() else 0xFF757575.toInt())
        } else {
            holder.due.visibility = View.GONE
        }

        val done = task.subtasks.count { it.completed }
        holder.subtasks.visibility =
            if (task.subtasks.isNotEmpty()) View.VISIBLE else View.GONE
        if (task.subtasks.isNotEmpty()) {
            holder.subtasks.text = holder.itemView.context.getString(
                R.string.subtask_done, done, task.subtasks.size
            )
        }

        holder.check.setOnClickListener { holder.check.isChecked = task.completed; onToggle(task) }
        holder.itemView.setOnClickListener { onClick(task) }
        holder.itemView.setOnLongClickListener { onLongClick(task); true }
    }

    fun submitList(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}