// SharedPreferences wrapper for AANO settings.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.Context

class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("aano_prefs", Context.MODE_PRIVATE)

    var autoSync: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    var autoSave: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SAVE, value).apply()

    var sortBy: SortBy
        get() = runCatching { SortBy.valueOf(prefs.getString(KEY_SORT, "UPDATED_DESC") ?: "") }
            .getOrDefault(SortBy.UPDATED_DESC)
        set(value) = prefs.edit().putString(KEY_SORT, value.name).apply()

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var lockTimeoutMinutes: Long
        get() = prefs.getLong(KEY_LOCK_TIMEOUT_MIN, 1)
        set(value) = prefs.edit().putLong(KEY_LOCK_TIMEOUT_MIN, value).apply()

    var focusCount: Int
        get() = prefs.getInt(KEY_FOCUS_COUNT, 3)
        set(value) = prefs.edit().putInt(KEY_FOCUS_COUNT, value).apply()

    var syncPending: Boolean
        get() = prefs.getBoolean(KEY_SYNC_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_PENDING, value).apply()

    var cloudToken: String?
        get() = prefs.getString(KEY_CLOUD_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_CLOUD_TOKEN, value).apply()

    var cloudEmail: String?
        get() = prefs.getString(KEY_CLOUD_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_CLOUD_EMAIL, value).apply()

    var cloudUserId: String?
        get() = prefs.getString(KEY_CLOUD_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_CLOUD_USER_ID, value).apply()

    var lastCloudSyncTime: Long
        get() = prefs.getLong(KEY_LAST_CLOUD_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CLOUD_SYNC, value).apply()

    var isGridView: Boolean
        get() = prefs.getBoolean(KEY_GRID_VIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_GRID_VIEW, value).apply()

    var customFolders: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM_FOLDERS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_CUSTOM_FOLDERS, value).apply()

    companion object {
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_AUTO_SAVE = "auto_save"
        private const val KEY_SORT = "sort_by"
        private const val KEY_GRID_VIEW = "is_grid_view"
        private const val KEY_CUSTOM_FOLDERS = "custom_folders"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_LOCK_TIMEOUT_MIN = "lock_timeout_min"
        private const val KEY_FOCUS_COUNT = "focus_count"
        private const val KEY_SYNC_PENDING = "sync_pending"
        private const val KEY_CLOUD_TOKEN = "cloud_token"
        private const val KEY_CLOUD_EMAIL = "cloud_email"
        private const val KEY_CLOUD_USER_ID = "cloud_user_id"
        private const val KEY_LAST_CLOUD_SYNC = "last_cloud_sync"
    }
}