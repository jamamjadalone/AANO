// Application class for AANO: global lifecycle-aware AppLock management.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import app.aa.aano.data.AppLock
import app.aa.aano.ui.LockActivity

class AanoApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        var isUnlockedThisSession = false
        private var activeActivityCount = 0
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is LockActivity) return

        val lock = AppLock(activity)
        if (lock.pinEnabled && !isUnlockedThisSession && activeActivityCount == 0) {
            val intent = Intent(activity, LockActivity::class.java).apply {
                putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_UNLOCK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            activity.startActivity(intent)
        }
        activeActivityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is LockActivity) return
        activeActivityCount--
        if (activeActivityCount <= 0) {
            activeActivityCount = 0
            // Entire app was minimized or device locked
            isUnlockedThisSession = false
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
