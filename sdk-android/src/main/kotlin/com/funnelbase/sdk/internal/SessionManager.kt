package com.funnelbase.sdk.internal

import android.content.SharedPreferences
import java.util.UUID

/**
 * Client-side session ids with an inactivity timeout.
 *
 * Sessions are generated here rather than server-side because only the device knows about
 * offline gaps, and persisted because a session should survive the app being swapped out of
 * memory for a minute. There is no session-end event — the backend derives session bounds from
 * `min`/`max(occurred_at)` grouped by `session_id`, so a killed app costs nothing.
 */
internal class SessionManager(
    private val preferences: SharedPreferences,
    private val timeoutMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** True when this call started a new session, so the caller can emit `$session_start`. */
    data class Session(val id: String, val isNew: Boolean)

    /** [now] is when the event happened — the caller's clock, not this thread's. */
    @Synchronized
    fun current(now: Long = clock()): Session {
        val existingId = preferences.getString(KEY_SESSION_ID, null)
        val lastActivity = preferences.getLong(KEY_LAST_ACTIVITY, 0L)

        val expired = existingId == null || now - lastActivity > timeoutMillis
        val id = if (existingId == null || expired) UUID.randomUUID().toString() else existingId

        // apply(), not commit(): this runs on every tracked event, and losing the last
        // activity timestamp to a process death costs at most one spurious session boundary.
        preferences.edit()
            .putString(KEY_SESSION_ID, id)
            .putLong(KEY_LAST_ACTIVITY, now)
            .apply()

        return Session(id, isNew = expired)
    }

    @Synchronized
    fun clear() {
        preferences.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_LAST_ACTIVITY)
            .apply()
    }

    private companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_LAST_ACTIVITY = "session_last_activity"
    }
}
