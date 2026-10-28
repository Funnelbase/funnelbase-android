package com.funnelbase.demo.trading.push

import com.funnelbase.sdk.PushPayload

/**
 * What the app does with a push once the SDK has reported it: where to go.
 *
 * The SDK deliberately does not navigate — it cannot know this app's routes — so it hands the
 * payload over and this is the app's half of the contract. Pure, so the routing is testable
 * without a device: given a push, which route.
 */
object PushRouting {

    /** The scheme the demo's own deep links use, as the campaign writes them. */
    const val SCHEME = "trade"

    /**
     * The route a push should open, or null to leave the app where it is. Understands the demo's
     * two link shapes and ignores anything else, because a campaign can always be sent with a
     * link this build does not know yet.
     *
     *     trade://instrument/AAPL  →  instrument/AAPL
     *     trade://markets          →  markets
     */
    fun routeFor(link: String?): String? {
        if (link.isNullOrBlank()) return null
        val withoutScheme = link.removePrefix("$SCHEME://").takeIf { it != link } ?: return null
        val parts = withoutScheme.trim('/').split('/').filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> null
            parts[0] == "instrument" && parts.size >= 2 -> "instrument/${parts[1].uppercase()}"
            parts[0] in TABS -> parts[0]
            else -> null
        }
    }

    /** The link a tap followed: the button's own, if it had one, else the message's. */
    fun linkFor(payload: PushPayload, actionId: String?): String? =
        actionId?.let { id -> payload.buttons.firstOrNull { it.id == id }?.deepLink } ?: payload.deepLink

    private val TABS = setOf("dashboard", "markets", "orders", "news")
}
