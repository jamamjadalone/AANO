// Local task storage for AANO (JSON file in app-private storage),
// encrypted the same way as notes (AES-256-GCM, Android Keystore).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TaskRepository(private val context: Context) {

    private val tasksFile: File = File(context.filesDir, "tasks.json")
    private val cipher = NoteCipher()

    /** Active tasks, incomplete first (by priority then due date), completed last. */
    suspend fun getActive(): List<Task> = withContext(Dispatchers.IO) {
        val active = readTasks().filterNot { it.completed }
        val done = readTasks().filter { it.completed }
        active.sortedWith(activeComparator) + done.sortedByDescending { it.updatedAt }
    }

    suspend fun getAll(): List<Task> = withContext(Dispatchers.IO) {
        readTasks().sortedByDescending { it.updatedAt }
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
            writeTasks(readTasks().filterNot { it.id == id })
        }
    }

    suspend fun clearCompleted() {
        withContext(Dispatchers.IO) {
            writeTasks(readTasks().filterNot { it.completed })
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
            val remote = deserializeTasks(json)
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
        put("createdAt", task.createdAt)
        put("updatedAt", task.updatedAt)
        put("enc", false)
    }

    private fun merge(local: List<Task>, remote: List<Task>): List<Task> {
        val byId = local.associateBy { it.id }.toMutableMap()
        remote.forEach { task ->
            val existing = byId[task.id]
            when {
                existing == null -> byId[task.id] = task
                task.updatedAt > existing.updatedAt -> byId[task.id] = task
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private val activeComparator = compareBy<Task>(
        { task -> task.dueAt == null },
        { task -> task.dueAt ?: Long.MAX_VALUE },
        { task -> -task.priority.ordinal },
        { task -> task.createdAt }
    )

    private fun readTasks(): List<Task> {
        if (!tasksFile.exists()) return emptyList()
        return try {
            deserializeTasks(tasksFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun deserializeTasks(json: String): List<Task> {
        val tasks = mutableListOf<Task>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            tasks.add(deserializeTask(array.getJSONObject(i)))
        }
        return tasks
    }

    private fun deserializeTask(obj: JSONObject): Task {
        val encrypted = obj.optBoolean("enc", false)
        fun field(name: String): String {
            val raw = obj.getString(name)
            return if (encrypted) {
                try {
                    cipher.decrypt(raw)
                } catch (e: Exception) {
                    ""
                }
            } else raw
        }

        val subs = JSONArray(obj.optString("subtasks", "[]"))
        val subtasks = mutableListOf<SubTask>()
        for (i in 0 until subs.length()) {
            val s = subs.getJSONObject(i)
            val titleRaw = s.getString("title")
            subtasks.add(
                SubTask(
                    id = s.getString("id"),
                    title = if (encrypted) cipher.decrypt(titleRaw) else titleRaw,
                    completed = s.optBoolean("completed", false)
                )
            )
        }

        return Task(
            id = obj.getString("id"),
            title = field("title"),
            notes = field("notes"),
            completed = obj.optBoolean("completed", false),
            priority = runCatching {
                TaskPriority.valueOf(obj.optString("priority", "MEDIUM"))
            }.getOrDefault(TaskPriority.MEDIUM),
            dueAt = if (obj.has("dueAt") && !obj.isNull("dueAt")) obj.optLong("dueAt") else null,
            reminderAt = if (obj.has("reminderAt") && !obj.isNull("reminderAt")) obj.optLong("reminderAt")
            else null,
            recurrence = runCatching {
                Recurrence.valueOf(obj.optString("recurrence", "NONE"))
            }.getOrDefault(Recurrence.NONE),
            subtasks = subtasks,
            focusHidden = obj.optBoolean("focusHidden", false),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun writeTasks(tasks: List<Task>) {
        val array = JSONArray()
        tasks.forEach { array.put(serializeTask(it)) }
        tasksFile.writeText(array.toString())
    }

    private fun serializeTask(task: Task): JSONObject = JSONObject().apply {
        put("id", task.id)
        put("title", cipher.encrypt(task.title))
        put("notes", cipher.encrypt(task.notes))
        put("completed", task.completed)
        put("priority", task.priority.name)
        if (task.dueAt != null) put("dueAt", task.dueAt)
        if (task.reminderAt != null) put("reminderAt", task.reminderAt)
        put("recurrence", task.recurrence.name)
        put("subtasks", JSONArray().apply {
            task.subtasks.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("title", cipher.encrypt(it.title))
                    put("completed", it.completed)
                })
            }
        })
        put("focusHidden", task.focusHidden)
        put("createdAt", task.createdAt)
        put("updatedAt", task.updatedAt)
        put("enc", true)
    }
}