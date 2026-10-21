// Task data model for AANO.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

enum class TaskPriority { LOW, MEDIUM, HIGH }

enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY }

data class SubTask(
    val id: String,
    val title: String,
    val completed: Boolean = false
)

data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val completed: Boolean = false,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val subtasks: List<SubTask> = emptyList(),
    val focusHidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)