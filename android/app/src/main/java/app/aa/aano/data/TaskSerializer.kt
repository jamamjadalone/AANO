// JSON serializer & deserializer for Tasks with AES-256-GCM encryption.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object TaskSerializer {

    fun deserializeTasks(json: String, cipher: NoteCipher): List<Task> {
        val tasks = mutableListOf<Task>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            tasks.add(deserializeTask(array.getJSONObject(i), cipher))
        }
        return tasks
    }

    fun deserializeTask(obj: JSONObject, cipher: NoteCipher): Task {
        val encrypted = obj.optBoolean("enc", false)
        fun field(name: String): String {
            val raw = obj.optString(name, "")
            return if (encrypted && raw.isNotEmpty()) {
                try {
                    cipher.decrypt(raw)
                } catch (e: Exception) {
                    Log.e("AANO", "Failed to decrypt task field '': ")
                    raw
                }
            } else raw
        }

        val subs = obj.optJSONArray("subtasks") ?: JSONArray()
        val subtasks = mutableListOf<SubTask>()
        for (i in 0 until subs.length()) {
            val s = subs.getJSONObject(i)
            val titleRaw = s.optString("title", "")
            val decTitle = if (encrypted && titleRaw.isNotEmpty()) {
                try {
                    cipher.decrypt(titleRaw)
                } catch (e: Exception) {
                    Log.e("AANO", "Failed to decrypt subtask title: ")
                    titleRaw
                }
            } else titleRaw
            subtasks.add(
                SubTask(
                    id = s.getString("id"),
                    title = decTitle,
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
            deleted = obj.optBoolean("deleted", false),
            deletedAt = obj.optLong("deletedAt", 0L),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    fun serializeTask(task: Task, cipher: NoteCipher): JSONObject = JSONObject().apply {
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
        put("deleted", task.deleted)
        put("deletedAt", task.deletedAt)
        put("createdAt", task.createdAt)
        put("updatedAt", task.updatedAt)
        put("enc", true)
    }
}
