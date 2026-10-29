package com.myanmar.ledger2d.feature.live

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Activity *visibility*, not process existence: WorkManager itself can start
 * an Application without displaying its Activity.
 */
internal object LiveAppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile private var startedActivities = 0

    val isVisible: Boolean
        get() = startedActivities > 0

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
