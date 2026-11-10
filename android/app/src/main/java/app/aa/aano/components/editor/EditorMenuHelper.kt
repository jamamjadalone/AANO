// EditorMenuHelper: Menu inflation and item action routing for EditorActivity
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import app.aa.aano.R

object EditorMenuHelper {

    fun inflateMenu(activity: Activity, menu: Menu) {
        activity.menuInflater.inflate(R.menu.menu_editor, menu)
    }

    fun handleOptionsItemSelected(
        item: MenuItem,
        onShare: () -> Unit,
        onRemind: () -> Unit,
        onArchive: () -> Unit,
        onDrop: () -> Unit,
        onExport: () -> Unit,
        onDelete: () -> Unit
    ): Boolean {
        return when (item.itemId) {
            R.id.action_share -> { onShare(); true }
            R.id.action_remind -> { onRemind(); true }
            R.id.action_archive -> { onArchive(); true }
            R.id.action_drop_wifi -> { onDrop(); true }
            R.id.action_export_note -> { onExport(); true }
            R.id.action_delete -> { onDelete(); true }
            else -> false
        }
    }
}
