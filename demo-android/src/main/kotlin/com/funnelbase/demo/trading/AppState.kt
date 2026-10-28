package com.funnelbase.demo.trading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.funnelbase.demo.trading.data.MarketData
import com.funnelbase.demo.trading.data.SessionStore
import com.funnelbase.demo.trading.identity.UserId
import com.funnelbase.sdk.Analytics
import java.time.LocalDate

/**
 * The app's entire mutable state: who is signed in and what they favourited.
 *
 * A plain class held by `remember`, not a ViewModel. There is no async work, no saved-state
 * handle worth having and nothing that outlives the Activity except what `SessionStore` writes
 * to disk — a ViewModel here would be ceremony around two fields.
 *
 * This is also the one place that touches identity in the SDK. Keeping `identify` and `reset`
 * next to the state change they describe is what stops a screen from flipping the username
 * without telling analytics about it.
 */
class AppState(
    private val store: SessionStore,
    val market: MarketData,
) {
    var username by mutableStateOf(store.username)
        private set

    var favourites by mutableStateOf(
        // First run seeds a handful so the dashboard's favourites widget has something in it.
        // An empty-state screenshot is not what anyone wants from a demo's home tab.
        if (store.isFirstRun) market.defaultFavourites.also { store.favourites = it }
        else store.favourites,
    )
        private set

    val isSignedIn: Boolean get() = username != null

    /**
     * Sign-in with any credentials, as specified — there is no auth backend.
     *
     * One `identify`, and that is the whole of it (docs/event-contract.md): the `$identify` it
     * sends carries the device's anonymous id and the account together, which is the link
     * between them. Signing in as a different account than the one before is an account switch,
     * and the SDK resets the anonymous id and the session itself before identifying.
     */
    fun signIn(username: String, pro: Boolean = false) {
        val name = username.trim()
        persist(name)
        // There is no billing either: the sign-in screen's switch says which plan the account is
        // on, and a pro plan renews 30 days from the sign-in — a calendar day, which is what a
        // journey's "wait until a date" and a segment's "in the next N days" read.
        store.planRenewsOn = if (pro) LocalDate.now().plusDays(PLAN_PERIOD_DAYS) else null
        identify()
    }

    /**
     * Sends `$identify` for whoever is signed in, with every trait the app knows: `username`,
     * `plan`, and on pro `plan_renews_on` as a `LocalDate`, which the SDK sends as `YYYY-MM-DD`
     * (docs/event-contract.md, "Dates").
     */
    fun identify() {
        val name = username ?: return
        Analytics.identify(UserId.forUsername(name), mapOf("username" to name) + planTraits())
    }

    private fun planTraits(): Map<String, Any> {
        var renewsOn = store.planRenewsOn ?: return mapOf("plan" to "free")
        // A relaunch long after the sign-in: the plan has renewed since, as often as it took.
        val today = LocalDate.now()
        while (renewsOn < today) renewsOn = renewsOn.plusDays(PLAN_PERIOD_DAYS)
        store.planRenewsOn = renewsOn
        return mapOf("plan" to "pro", "plan_renews_on" to renewsOn)
    }

    /**
     * Registration. The same `identify` as a sign-in — there is no alias call to make first. The
     * onboarding pages this person viewed before creating the account were sent under the
     * anonymous id the `$identify` now carries beside the new user id, so the backend attaches
     * them to the account from that one event.
     */
    fun signUp(username: String) = signIn(username, pro = false)

    /**
     * Sign-out. `Analytics.reset()` uploads what is queued before clearing the identity, so the
     * end of this person's session is not attributed to whoever signs in next — who starts with
     * a fresh anonymous id and a session of their own.
     */
    fun signOut() {
        store.clearUser()
        username = null
        Analytics.reset()
    }

    fun toggleFavourite(symbol: String) {
        favourites = if (symbol in favourites) favourites - symbol else favourites + symbol
        store.favourites = favourites
    }

    fun isFavourite(symbol: String): Boolean = symbol in favourites

    private fun persist(name: String) {
        store.username = name
        username = name
    }
}

@Composable
fun rememberAppState(): AppState {
    val context = LocalContext.current
    return remember {
        AppState(SessionStore(context), MarketData.current)
            // A relaunch of an already-signed-in app has to re-identify: Analytics keeps the
            // user id in its own store, but the traits and the $identify marking the start of
            // this run would otherwise be missing. It happens here, during composition, so it is
            // the launch's first event — ahead of the screen views, whose effects run after
            // composition — and the session's start, and the install's `$first_open` on the day
            // the SDK arrives, are sent signed in.
            .also { state -> state.identify() }
    }
}

/** How often a pro plan renews. */
private const val PLAN_PERIOD_DAYS = 30L
