package com.funnelbase.demo.trading.push

import com.funnelbase.sdk.PushButton
import com.funnelbase.sdk.PushPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The app's half of a tap: the SDK reports the open and hands the push over, and this decides
 * where to go. A campaign is written long after a build ships, so an unknown link has to be
 * harmless rather than a crash or a blank screen.
 */
class PushRoutingTest {

    private fun payload(deepLink: String? = null, buttons: List<PushButton> = emptyList()) = PushPayload(
        deliveryId = "del-1",
        runId = "run-1",
        title = "Your order filled",
        body = "AAPL, 12 shares",
        deepLink = deepLink,
        buttons = buttons,
    )

    @Test
    fun `an instrument link opens that instrument, whatever case it was written in`() {
        assertEquals("instrument/AAPL", PushRouting.routeFor("trade://instrument/AAPL"))
        assertEquals("instrument/AAPL", PushRouting.routeFor("trade://instrument/aapl"))
        assertEquals("instrument/AAPL", PushRouting.routeFor("trade://instrument/AAPL/"))
    }

    @Test
    fun `a tab link opens that tab`() {
        assertEquals("markets", PushRouting.routeFor("trade://markets"))
        assertEquals("news", PushRouting.routeFor("trade://news"))
    }

    @Test
    fun `a link this build does not know leaves the app where it is`() {
        assertNull(PushRouting.routeFor(null))
        assertNull(PushRouting.routeFor(""))
        assertNull(PushRouting.routeFor("trade://portfolio/rebalance"), "a route a later version may add")
        assertNull(PushRouting.routeFor("https://example.com/promo"), "someone else's link")
        assertNull(PushRouting.routeFor("trade://instrument"), "an instrument link with no instrument")
    }

    /**
     * A button carries its own link, and that is the point of a button: "View order" and "Later"
     * on one push lead to different places.
     */
    @Test
    fun `a button's link wins over the message's, and the message's is the fallback`() {
        val buttons = listOf(
            PushButton("view", "View", "trade://instrument/MSFT"),
            PushButton("later", "Later"),
        )
        val push = payload(deepLink = "trade://orders", buttons = buttons)

        assertEquals("trade://instrument/MSFT", PushRouting.linkFor(push, actionId = "view"))
        assertEquals("trade://orders", PushRouting.linkFor(push, actionId = "later"), "a button with no link of its own")
        assertEquals("trade://orders", PushRouting.linkFor(push, actionId = null), "the notification itself")
        assertNull(PushRouting.linkFor(payload(), actionId = null))
    }
}
