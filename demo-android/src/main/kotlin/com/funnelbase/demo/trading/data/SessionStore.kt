package com.funnelbase.demo.trading.data

import android.content.Context
import java.time.LocalDate

/**
 * The only thing this app persists: who is signed in (and on which plan), and which symbols they
 * favourited.
 *
 * `SharedPreferences`, not DataStore. Two keys read once at startup do not need a coroutine API
 * or another dependency, and the SDK next door makes the same call for the same reason.
 */
class SessionStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var username: String?
        get() = preferences.getString(KEY_USERNAME, null)
        set(value) {
            preferences.edit()
                .apply { if (value == null) remove(KEY_USERNAME) else putString(KEY_USERNAME, value) }
                .apply()
        }

    /**
     * The day the signed-in account's pro plan next renews, or null on the free plan. Kept so a
     * relaunch's re-identify sends the same day the sign-in did (`AppState.identify`).
     */
    var planRenewsOn: LocalDate?
        get() = preferences.getString(KEY_PLAN_RENEWS_ON, null)?.let(LocalDate::parse)
        set(value) {
            preferences.edit()
                .apply { if (value == null) remove(KEY_PLAN_RENEWS_ON) else putString(KEY_PLAN_RENEWS_ON, value.toString()) }
                .apply()
        }

    var favourites: Set<String>
        // Copied out: SharedPreferences explicitly forbids mutating the returned set, and the
        // instance it hands back is shared with its own cache.
        get() = preferences.getStringSet(KEY_FAVOURITES, null)?.toSet() ?: emptySet()
        set(value) {
            preferences.edit().putStringSet(KEY_FAVOURITES, value).apply()
        }

    /** True until the first sign-up or sign-in, so favourites can be seeded exactly once. */
    val isFirstRun: Boolean get() = !preferences.contains(KEY_FAVOURITES)

    fun clearUser() {
        username = null
        planRenewsOn = null
    }

    private companion object {
        const val NAME = "trade-demo"
        const val KEY_USERNAME = "username"
        const val KEY_FAVOURITES = "favourites"
        const val KEY_PLAN_RENEWS_ON = "plan_renews_on"
    }
}
