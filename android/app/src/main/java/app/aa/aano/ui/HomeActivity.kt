// HomeActivity: hosts the 4 main tabs (Notes, Tasks, Focus, More).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import app.aa.aano.R
import app.aa.aano.data.AppLock

class HomeActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var previousTab: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        bottomNav = findViewById(R.id.bottomNav)

        // When app lock is enabled, keep content out of screenshots/recents.
        if (AppLock(this).pinEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        if (savedInstanceState == null) {
            switchTo(R.id.nav_notes)
        }

        bottomNav.setOnItemSelectedListener { item ->
            switchTo(item.itemId)
            true
        }

        // Start AANO Drop background Wi-Fi receiver server
        val dropMgr = app.aa.aano.drop.AanoDropManager.getInstance(this)
        dropMgr.startServer()
        dropMgr.onNotesReceivedListener = { count, images ->
            android.widget.Toast.makeText(this, "⚡ AANO Drop: Received $count notes ($images images)!", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun startLockActivity(mode: String) {
        startActivity(
            Intent(this, LockActivity::class.java)
                .putExtra(LockActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private var pendingFolderForNotes: String? = null

    fun consumePendingFolder(): String? {
        val folder = pendingFolderForNotes
        pendingFolderForNotes = null
        return folder
    }

    fun openNotesInFolder(folderName: String) {
        pendingFolderForNotes = folderName
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (fragment is NotesFragment) {
            fragment.filterByFolder(folderName)
            pendingFolderForNotes = null
        } else {
            bottomNav.selectedItemId = R.id.nav_notes
        }
    }

    private fun switchTo(itemId: Int) {
        if (itemId == previousTab) return
        previousTab = itemId
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragmentFor(itemId))
            .commit()
    }

    private fun fragmentFor(itemId: Int): Fragment = when (itemId) {
        R.id.nav_folders -> FoldersFragment()
        R.id.nav_local -> LocalFragment()
        R.id.nav_more -> MoreFragment()
        else -> NotesFragment()
    }
}