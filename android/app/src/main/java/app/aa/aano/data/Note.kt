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
    val reminderAt: Long = 0L,
    val archived: Boolean = false,
    val isLocal: Boolean = false,
    val images: List<String> = emptyList(),
    val audioUri: String? = null,
    val audioDuration: Long = 0L,
    val purged: Boolean = false,
    val type: Int = TYPE_TEXT,
    val colorIndex: Int = 0,
    val colorLabel: String = "",
    val importance: Int = 0,
    val reminderType: Int = REMINDER_NONE,
    val reminderDate: Long = 0L,
    val minorModifiedDate: Long = 0L,
    val noteExt: String = ""
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
        put("archived", archived)
        put("isLocal", isLocal)
        put("images", JSONArray(images))
        if (audioUri != null) put("audioUri", audioUri)
        put("audioDuration", audioDuration)
        put("purged", purged)
        put("type", type)
        put("colorIndex", colorIndex)
        put("colorLabel", colorLabel)
        put("importance", importance)
        put("reminderType", reminderType)
        put("reminderDate", reminderDate)
        put("minorModifiedDate", minorModifiedDate)
        put("noteExt", noteExt)
    }

    companion object {
        const val COLOR_DEFAULT = 0
        const val TYPE_TEXT = 0
        const val TYPE_CHECKLIST = 1
        const val REMINDER_NONE = 0
        const val REMINDER_ALL_DAY = 16
        const val REMINDER_TIME = 32
        const val REMINDER_REPEAT = 64
        const val REMINDER_STATUS_PIN = 128

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
            reminderAt = obj.optLong("reminderAt", 0),
            archived = obj.optBoolean("archived", false),
            isLocal = obj.optBoolean("isLocal", false),
            images = obj.optJSONArray("images")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            audioUri = if (obj.has("audioUri") && !obj.isNull("audioUri")) obj.optString("audioUri") else null,
            audioDuration = obj.optLong("audioDuration", 0L),
            purged = obj.optBoolean("purged", false),
            type = obj.optInt("type", TYPE_TEXT),
            colorIndex = obj.optInt("colorIndex", 0),
            colorLabel = obj.optString("colorLabel", ""),
            importance = obj.optInt("importance", 0),
            reminderType = obj.optInt("reminderType", REMINDER_NONE),
            reminderDate = obj.optLong("reminderDate", 0L),
            minorModifiedDate = obj.optLong("minorModifiedDate", 0L),
            noteExt = obj.optString("noteExt", "")
        )
    }
}