// Note data model for AANO.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import org.json.JSONArray
import org.json.JSONObject

data class Note(
    val id: String,
    var title: String,
    var content: String,
    val createdAt: Long,
    var updatedAt: Long,
    val deleted: Boolean = false,
    val deletedAt: Long = 0L,
    val pinned: Boolean = false,
    val color: Int = COLOR_DEFAULT,
    val folder: String = "",
    val tags: List<String> = emptyList(),
    val reminderAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("deleted", deleted)
        put("deletedAt", deletedAt)
        put("pinned", pinned)
        put("color", color)
        put("folder", folder)
        put("tags", JSONArray(tags))
        put("reminderAt", reminderAt)
    }

    companion object {
        const val COLOR_DEFAULT = 0

        fun fromJson(obj: JSONObject): Note = Note(
            id = obj.getString("id"),
            title = obj.optString("title", ""),
            content = obj.optString("content", ""),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            deleted = obj.optBoolean("deleted", false),
            deletedAt = obj.optLong("deletedAt", 0),
            pinned = obj.optBoolean("pinned", false),
            color = obj.optInt("color", COLOR_DEFAULT),
            folder = obj.optString("folder", ""),
            tags = obj.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            reminderAt = obj.optLong("reminderAt", 0)
        )
    }
}