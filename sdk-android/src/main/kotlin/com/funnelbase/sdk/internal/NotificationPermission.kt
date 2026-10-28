package com.funnelbase.sdk.internal

import android.app.NotificationManager
import android.content.Context

/**
 * Whether a push from us can reach the person, which is what a token's status reports.
 *
 * Two switches can stop one: the app's own (the runtime permission from Android 13 on, and the
 * person's switch on every version), and the SDK's notification channel, which the person can
 * block alone from the system settings while leaving the app's other channels on. Only the SDK's
 * own channel counts: it is where a message lands unless it names another, so a blocked one means
 * the campaigns are not seen.
 */
internal object NotificationPermission {

    /**
     * [appEnabled] is `areNotificationsEnabled()`; [channelImportance] is the SDK channel's
     * importance, or null while the channel does not exist yet — a channel the SDK creates on the
     * first push, at the default importance, so its absence blocks nothing.
     */
    fun allowed(appEnabled: Boolean, channelImportance: Int?): Boolean =
        appEnabled && channelImportance != NotificationManager.IMPORTANCE_NONE

    /** Reads both switches; false when the system will not say. */
    fun read(context: Context, channelId: String): Boolean = runCatching {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        allowed(manager.areNotificationsEnabled(), manager.getNotificationChannel(channelId)?.importance)
    }.getOrDefault(false)
}
