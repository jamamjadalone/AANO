// Local task storage for AANO (JSON file in app-private storage),
// encrypted the same way as notes (AES-256-GCM, Android Keystore).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import app.aa.aano.net.TaskReminderReceiver

class TaskRepository(private val context: Context) {

    private val tasksFile: File = File(context.filesDir, "tasks.json")
    private val cipher = NoteCipher()

    /** Active tasks, incomplete first (by priority then due date), completed last. */
    suspend fun getActive(): List<Task> = withContext(Dispatchers.IO) {
        val nonDeleted = readTasks().filterNot { it.deleted }
        val active = nonDeleted.filterNot { it.completed }
        val done = nonDeleted.filter { it.completed }
        active.sortedWith(activeComparator) + done.sortedByDescending { it.updatedAt }
    }

    suspend fun getAll(): List<Task> = withContext(Dispatchers.IO) {
        readTasks().filterNot { it.deleted }.sortedByDescending { it.updatedAt }
    }

    suspend fun getById(id: String): Task? = withContext(Dispatchers.IO) {
        readTasks().find { it.id == id }
    }

    suspend fun create(
        title: String,
        notes: String,
        priority: TaskPriority,
        dueAt: Long?,
        reminderAt: Long?,
        recurrence: Recurrence,
        subtasks: List<SubTask>,
        focusHidden: Boolean = false
    ): Task = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = title,
            notes = notes,
            priority = priority,
            dueAt = dueAt,
            reminderAt = reminderAt,
            recurrence = recurrence,
            subtasks = subtasks,
            focusHidden = focusHidden,
            createdAt = now,
            updatedAt = now
        )
        val tasks = readTasks().toMutableList()
        tasks.add(task)
        writeTasks(tasks)
        task
    }

    suspend fun update(
        id: String,
        title: String,
        notes: String,
        priority: TaskPriority,
        dueAt: Long?,
        reminderAt: Long?,
        recurrence: Recurrence,
        subtasks: List<SubTask>,
        focusHidden: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            val tasks = readTasks().toMutableList()
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) {
                tasks[index] = tasks[index].copy(
                    title = title,
                    notes = notes,
                    priority = priority,
                    dueAt = dueAt,
                    reminderAt = reminderAt,
                    recurrence = recurrence,
                    subtasks = subtasks,
                    focusHidden = focusHidden,
                    updatedAt = System.currentTimeMillis()
                )
                writeTasks(tasks)
            }
        }
    }

    /** Un-hides all tasks so they can show up in Focus again. */
    suspend fun showAllFocusTasks() {
        withContext(Dispatchers.IO) {
            writeTasks(
                readTasks().map { if (it.focusHidden) it.copy(focusHidden = false) else it }
            )
        }
    }

    suspend fun toggleComplete(id: String): Task? = withContext(Dispatchers.IO) {
        val tasks = readTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) return@withContext null
        val task = tasks[index]
        val updated = task.copy(
            completed = !task.completed,
            updatedAt = System.currentTimeMillis()
        )
        if (updated.completed) {
            TaskReminderReceiver.cancel(context, id)
        }
        tasks[index] = updated
        writeTasks(tasks)
        updated
    }

    suspend fun toggleSubtask(taskId: String, subId: String) {
        withContext(Dispatchers.IO) {
            val tasks = readTasks().toMutableList()
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index >= 0) {
                val task = tasks[index]
                val subs = task.subtasks.map {
                    if (it.id == subId) it.copy(completed = !it.completed) else it
                }
                tasks[index] = task.copy(subtasks = subs, updatedAt = System.currentTimeMillis())
                writeTasks(tasks)
            }
        }
    }

    suspend fun setFocusHidden(id: String, hidden: Boolean) {
        withContext(Dispatchers.IO) {
            val tasks = readTasks().toMutableList()
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) {
                tasks[index] = tasks[index].copy(focusHidden = hidden)
                writeTasks(tasks)
            }
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            TaskReminderReceiver.cancel(context, id)
            val now = System.currentTimeMillis()
            val tasks = readTasks().toMutableList()
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) {
                tasks[index] = tasks[index].copy(deleted = true, deletedAt = now, updatedAt = now)
                writeTasks(tasks)
            }
        }
    }

    suspend fun clearCompleted() {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val all = readTasks().map { task ->
                if (task.completed) {
                    TaskReminderReceiver.cancel(context, task.id)
                    task.copy(deleted = true, deletedAt = now, updatedAt = now)
                } else {
                    task
                }
            }
            writeTasks(all)
        }
    }

    suspend fun replaceAll(remote: List<Task>) {
        withContext(Dispatchers.IO) {
            val local = readTasks()
            val merged = merge(local, remote)
            writeTasks(merged)
        }
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        JSONArray().apply {
            readTasks().forEach { put(serializeTaskForCloud(it)) }
        }.toString()
    }

    suspend fun importJson(json: String) {
        withContext(Dispatchers.IO) {
            val remote = TaskSerializer.deserializeTasks(json, cipher)
            writeTasks(merge(readTasks(), remote))
        }
    }

    private fun serializeTaskForCloud(task: Task): JSONObject = JSONObject().apply {
        put("id", task.id)
        put("title", task.title)
        put("notes", task.notes)
        put("completed", task.completed)
        put("priority", task.priority.name)
        if (task.dueAt != null) put("dueAt", task.dueAt)
        if (task.reminderAt != null) put("reminderAt", task.reminderAt)
        put("recurrence", task.recurrence.name)
        put("subtasks", JSONArray().apply {
            task.subtasks.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("completed", it.completed)
                })
            }
        })
        put("focusHidden", task.focusHidden)
        put("deleted", task.deleted)
        put("deletedAt", task.deletedAt)
        put("createdAt", task.createdAt)
        put("updatedAt", task.updatedAt)
        put("enc", false)
    }

    private fun merge(local: List<Task>, remote: List<Task>): List<Task> =
        TaskMergeEngine.merge(local, remote)

    private val activeComparator = compareBy<Task>(
        { task -> task.dueAt == null },
        { task -> task.dueAt ?: Long.MAX_VALUE },
        { task -> -task.priority.ordinal },
        { task -> task.createdAt }
    )

    private fun readTasks(): List<Task> {
        if (!tasksFile.exists()) return emptyList()
        return try {
            TaskSerializer.deserializeTasks(tasksFile.readText(), cipher)
        } catch (e: Exception) {
            Log.e("AANO", "Failed to deserialize tasks. Preserving corrupted backup file.", e)
            try {
                val corruptBackup = File(tasksFile.parentFile, "tasks.json.corrupt.${System.currentTimeMillis()}.bak")
                tasksFile.copyTo(corruptBackup, overwrite = true)
            } catch (be: Exception) {
                Log.e("AANO", "Failed to create corrupt tasks backup", be)
            }
            emptyList()
        }
    }

    private fun writeTasks(tasks: List<Task>): Boolean {
        return try {
            val array = JSONArray()
            tasks.forEach { array.put(TaskSerializer.serializeTask(it, cipher)) }
            val tmpFile = File(tasksFile.parentFile, "tasks.json.tmp")
            tmpFile.writeText(array.toString())
            val renamed = if (tmpFile.renameTo(tasksFile)) {
                true
            } else {
                if (tasksFile.exists()) tasksFile.delete()
                tmpFile.renameTo(tasksFile)
            }
            if (!renamed && tmpFile.exists()) {
                tasksFile.writeText(array.toString())
                tmpFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e("AANO", "writeTasks failed: ${e.message}", e)
            false
        }
    }
}