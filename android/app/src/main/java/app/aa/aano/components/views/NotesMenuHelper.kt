// NotesMenuHelper: Options menu inflation and item selection dispatch for Notes tab
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.views

import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import app.aa.aano.R
import app.aa.aano.components.dialogs.NoteActionDialogHelper
import app.aa.aano.data.Prefs
import app.aa.aano.net.NotesSyncCoordinator
import app.aa.aano.pages.ArchiveActivity
import app.aa.aano.pages.SettingsActivity
import app.aa.aano.pages.TrashActivity

object NotesMenuHelper {

    fun applyLayoutManager(recyclerView: androidx.recyclerview.widget.RecyclerView, context: Context, isGridView: Boolean) {
        if (isGridView) {
            recyclerView.layoutManager = androidx.recyclerview.widget.StaggeredGridLayoutManager(2, androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy = androidx.recyclerview.widget.StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        } else {
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }
    }

    fun inflateMenu(menu: Menu, inflater: MenuInflater, prefs: Prefs) {
        inflater.inflate(R.menu.menu_main, menu)
        val viewModeItem = menu.findItem(R.id.action_view_mode)
        if (prefs.isGridView) {
            viewModeItem?.setIcon(R.drawable.ic_view_list)
            viewModeItem?.title = "List View"
        } else {
            viewModeItem?.setIcon(R.drawable.ic_view_grid)
            viewModeItem?.title = "Grid View"
        }
    }

    fun handleItemSelected(
        item: MenuItem,
        context: Context,
        prefs: Prefs,
        syncCoordinator: NotesSyncCoordinator,
        onSearch: () -> Unit,
        onToggleLayout: () -> Unit,
        onSortNotes: () -> Unit,
        onDropHub: () -> Unit
    ): Boolean {
        return when (item.itemId) {
            R.id.action_search -> { onSearch(); true }
            R.id.action_view_mode -> {
                prefs.isGridView = !prefs.isGridView
                onToggleLayout()
                true
            }
            R.id.action_sort -> {
                NoteActionDialogHelper.showSortDialog(context, prefs) { onSortNotes() }
                true
            }
            R.id.action_drop_hub -> { onDropHub(); true }
            R.id.action_sync -> { syncCoordinator.startSyncFlow(); true }
            R.id.action_archive -> { context.startActivity(Intent(context, ArchiveActivity::class.java)); true }
            R.id.action_trash -> { context.startActivity(Intent(context, TrashActivity::class.java)); true }
            R.id.action_settings -> { context.startActivity(Intent(context, SettingsActivity::class.java)); true }
            else -> false
        }
    }
}
