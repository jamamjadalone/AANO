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
    val deleted: Boolean = false,
    val deletedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChecklistItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false,
    val orderIndex: Int = 0,
    val isSubTask: Boolean = false
) {
    fun toMarkdown(): String {
        val indent = if (isSubTask) "  " else ""
        val check = if (isChecked) "- [x] " else "- [ ] "
        return "$indent$check$text"
    }

    companion object {
        fun fromMarkdown(line: String, order: Int = 0): ChecklistItem {
            val isSub = line.startsWith("  ") || line.startsWith("\t")
            val trimmed = line.trim()
            val isChecked = trimmed.startsWith("- [x] ", ignoreCase = true) || trimmed.startsWith("☑") || trimmed.startsWith("[x] ", ignoreCase = true)
            val cleanText = trimmed.replaceFirst(Regex("^(?:-\\s*\\[[xX ]\\]\\s*|☐\\s*|☑\\s*|\\[[xX ]\\]\\s*)"), "")
            return ChecklistItem(text = cleanText, isChecked = isChecked, orderIndex = order, isSubTask = isSub)
        }
    }
}