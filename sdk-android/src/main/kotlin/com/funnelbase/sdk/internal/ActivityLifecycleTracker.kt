package com.funnelbase.sdk.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.funnelbase.sdk.ReservedEvents

/**
 * Automatic screen-view and background-flush tracking.
 *
 * Activity-level only. Fragment and Compose destinations are invisible from here, so apps with
 * a single Activity should call `Analytics.screen()` themselves — the KDoc on that method says
 * so. Auto-tracking is a starting point, not a replacement.
 *
 * The two behaviours are separately switchable because they are wanted separately. A
 * single-Activity app needs the background flush and must *not* have the screen views: there is
 * one Activity, so every foreground would report the same name and drown the real screens.
 * [trackScreenViews] is the flag for that; the flush is unconditional.
 */
internal class ActivityLifecycleTracker(
    private val client: AnalyticsClient,
    private val trackScreenViews: Boolean,
    /**
     * The app came to the foreground: no Activity was started, and now one is. Where a setting
     * changed in the system's screens while the app was away — the notification switch, a
     * channel blocked — is noticed.
     */
    private val onForeground: () -> Unit = {},
) : Application.ActivityLifecycleCallbacks {

    // Counts started Activities. A rotation stops one and starts another, so "went to
    // background" is the count reaching zero, not any single onStop.
    private var startedActivities = 0

    override fun onActivityStarted(activity: Activity) {
        // Counted whether or not screen views are reported: the count is what detects
        // backgrounding, and tying it to the screen-view flag would disable the flush with it.
        startedActivities++
        if (startedActivities == 1) onForeground()
        if (!trackScreenViews) return

        client.track(
            ReservedEvents.SCREEN_VIEW,
            properties = mapOf(ReservedEvents.SCREEN_VIEW_SCREEN_NAME to activity.screenName()),
        )
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities <= 0) {
            // Backgrounding is the last reliable moment to upload — the process may be killed
            // at any point afterwards without another callback.
            client.flush()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    /** The simple class name, not the fully-qualified one — dashboards read better without the package. */
    private fun Activity.screenName(): String = javaClass.simpleName
}
