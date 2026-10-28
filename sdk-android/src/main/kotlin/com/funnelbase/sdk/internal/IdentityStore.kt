package com.funnelbase.sdk.internal

import android.content.SharedPreferences
import java.util.UUID

/**
 * Who the events belong to, and which install they come from — the three ids of
 * docs/event-contract.md, each with one job.
 *
 * - `deviceId` names the install. Made on the first launch, never changed; a reinstall makes a
 *   new one. It is untouched by [reset] because it does not name a person.
 * - `anonymousId` names one stretch of use between resets. It restarts at every sign-out and
 *   account switch, so the next person on a shared phone starts fresh and never inherits the
 *   history of whoever signed out.
 * - `userId` names the account, set at sign-in and cleared at every reset.
 *
 * There is no alias: the first event carrying both `anonymousId` and `userId` — the `$identify`
 * a sign-in or sign-up sends — is the link the backend reads, and it attaches the anonymous
 * stretch before it to the account.
 *
 * Every write is `commit()`, not `apply()`: identity has to be on disk before the events that
 * reference it, and a process death in between would otherwise mis-attribute them.
 */
internal class IdentityStore(private val preferences: SharedPreferences) {

    val deviceId: String
        get() = preferences.getString(KEY_DEVICE_ID, null) ?: newId(KEY_DEVICE_ID)

    val anonymousId: String
        get() = preferences.getString(KEY_ANONYMOUS_ID, null) ?: newId(KEY_ANONYMOUS_ID)

    var userId: String?
        get() = preferences.getString(KEY_USER_ID, null)
        set(value) {
            preferences.edit()
                .apply { if (value == null) remove(KEY_USER_ID) else putString(KEY_USER_ID, value) }
                .commit()
        }

    /**
     * True until [markFirstOpenRecorded]: the install has not yet sent its `$first_open`.
     *
     * Absent from the preferences of an install that predates this SDK version, so an app that
     * updates to it sends `$first_open` once too — with an install time from before the update,
     * which is what tells the backend it is no newcomer.
     */
    val isFirstOpenPending: Boolean
        get() = !preferences.getBoolean(KEY_FIRST_OPEN_RECORDED, false)

    fun markFirstOpenRecorded() {
        preferences.edit().putBoolean(KEY_FIRST_OPEN_RECORDED, true).commit()
    }

    /**
     * The reset of the contract: clears the account and starts a fresh anonymous id. Called at
     * sign-out, and before a different account signs in on the same device.
     */
    fun reset() {
        preferences.edit()
            .remove(KEY_USER_ID)
            .putString(KEY_ANONYMOUS_ID, UUID.randomUUID().toString())
            .commit()
    }

    private fun newId(key: String): String =
        UUID.randomUUID().toString().also {
            preferences.edit().putString(key, it).commit()
        }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ANONYMOUS_ID = "anonymous_id"
        const val KEY_USER_ID = "user_id"
        const val KEY_FIRST_OPEN_RECORDED = "first_open_recorded"
    }
}
