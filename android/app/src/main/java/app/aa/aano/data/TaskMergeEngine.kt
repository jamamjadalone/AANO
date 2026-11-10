// Multi-Device Task Merge Engine for AANO (LWW + deletion precedence)
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

object TaskMergeEngine {

    fun merge(local: List<Task>, remote: List<Task>): List<Task> {
        val byId = local.associateBy { it.id }.toMutableMap()
        remote.forEach { task ->
            val existing = byId[task.id]
            when {
                existing == null -> byId[task.id] = task
                task.updatedAt > existing.updatedAt -> {
                    val isDeleted = if (task.deleted) true else (existing.deleted && existing.deletedAt > task.updatedAt)
                    val delAt = if (isDeleted) maxOf(existing.deletedAt, task.deletedAt, if (existing.deleted) existing.updatedAt else task.updatedAt) else 0L
                    byId[task.id] = task.copy(
                        deleted = isDeleted,
                        deletedAt = delAt
                    )
                }
                task.updatedAt == existing.updatedAt -> {
                    val isDeleted = existing.deleted || task.deleted
                    val delAt = if (isDeleted) maxOf(existing.deletedAt, task.deletedAt) else 0L
                    byId[task.id] = task.copy(
                        completed = existing.completed || task.completed,
                        deleted = isDeleted,
                        deletedAt = delAt
                    )
                }
                else -> {
                    // Local is strictly newer: preserve local task, but honor deletion if remote was deleted AFTER local update
                    if (task.deleted && !existing.deleted && task.deletedAt > existing.updatedAt) {
                        byId[task.id] = existing.copy(
                            deleted = true,
                            deletedAt = task.deletedAt,
                            updatedAt = task.updatedAt
                        )
                    }
                }
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }
}
