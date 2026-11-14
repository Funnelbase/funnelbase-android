package com.funnelbase.sdk

import com.funnelbase.sdk.internal.DeviceRegion
import com.funnelbase.sdk.internal.EventRecorder
import com.funnelbase.sdk.internal.NotificationPresenter
import com.funnelbase.sdk.internal.PushController
import com.funnelbase.sdk.internal.PushRenderPlan
import com.funnelbase.sdk.internal.PushTokenStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the SDK does with a token, an arriving push and a tap — the three facts the delivery layer
 * needs back (docs/push.md). Which of them is passive is
 * the part worth pinning: a token and a receipt happen with nobody acting, so they start no
 * session; a tap is behaviour like any other event.
 */
class PushControllerTest {

    private class Recorded(val name: String, val properties: Map<String, Any?>, val passive: Boolean)

    /** Runs the worker inline; [queued] stands for the on-disk queue, by event id. */
    private class FakeRecorder : EventRecorder {
        val events = mutableListOf<Recorded>()
        val ids = mutableListOf<String>()
        val queued = mutableSetOf<String>()
        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += Recorded(eventName, properties, passive = false)
        }

        override fun trackPassive(eventName: String, properties: Map<String, Any?>) {
            events += Recorded(eventName, properties, passive = true)
        }

        override fun trackPassive(eventName: String, properties: Map<String, Any?>, enqueued: (eventId: String) -> Unit) {
            trackPassive(eventName, properties)
            val id = "evt-${events.size}"
            ids += id
            queued += id
            enqueued(id)
        }

        override fun isQueued(eventId: String): Boolean = eventId in queued
    }

    private class FakePresenter : NotificationPresenter {
        val shown = mutableListOf<PushRenderPlan>()
        override fun show(plan: PushRenderPlan) {
            shown += plan
        }
    }

    private val config = AnalyticsConfig(apiKey = "k", endpoint = "https://ingest.example.com")
    private val recorder = FakeRecorder()
    private val presenter = FakePresenter()
    private var enabled = true
    private var region = DeviceRegion("en-US", "America/New_York")
    private val preferences = FakeSharedPreferences()
    private val controller = PushController(
        recorder = recorder,
        tokens = PushTokenStore(preferences),
        presenter = presenter,
        config = config,
        notificationsEnabled = { enabled },
        region = { region },
    )

    private val message = mapOf(
        "delivery_id" to "del-1",
        "run_id" to "run-1",
        "title" to "Your order shipped",
        "body" to "It arrives tomorrow",
        "deep_link" to "trade://orders/42",
        "buttons" to """[{"id":"view","title":"View"}]""",
    )

    @Test
    fun `a token is a passive event carrying the token, its kind and its status`() {
        controller.setToken("tok-1", TokenKinds.FCM)

        val event = recorder.events.single()
        assertEquals(ReservedEvents.PUSH_TOKEN, event.name)
        assertTrue(event.passive, "nobody acted: a refreshed token starts no session")
        assertEquals("tok-1", event.properties[ReservedEvents.PUSH_TOKEN_TOKEN])
        assertEquals(TokenKinds.FCM, event.properties[ReservedEvents.PUSH_TOKEN_KIND])
        assertEquals(TokenStatuses.ACTIVE, event.properties[ReservedEvents.PUSH_TOKEN_STATUS])
    }

    @Test
    fun `the same token is not sent twice, and a permission change re-registers it`() {
        controller.setToken("tok-1", TokenKinds.FCM)
        controller.setToken("tok-1", TokenKinds.FCM)
        assertEquals(1, recorder.events.size)

        enabled = false
        controller.refreshToken()

        assertEquals(2, recorder.events.size)
        assertEquals(TokenStatuses.DISABLED, recorder.events.last().properties[ReservedEvents.PUSH_TOKEN_STATUS])
    }

    private fun line(eventId: String) =
        """{"event_id":"$eventId","event_name":"${'$'}push_token","session_id":"","anonymous_id":"anon-1"}"""

    /** The batch carrying [eventId] leaves the queue, as the client's drain loop does. */
    private fun uploaded(eventId: String, accepted: Boolean) {
        recorder.queued -= eventId
        if (accepted) controller.onBatchAccepted(listOf(line(eventId))) else controller.onBatchRejected(listOf(line(eventId)))
    }

    @Test
    fun `a registration the server accepted is not sent again`() {
        controller.setToken("tok-1", TokenKinds.FCM)
        uploaded(recorder.ids.single(), accepted = true)

        controller.refreshToken()
        assertEquals(1, recorder.events.size)
    }

    /**
     * A batch refused for good — a `403 plan_limit` while the workspace is over its plan, say — is
     * dropped by the queue. The registration must not be lost with it: the next check sends it again
     * (docs/event-contract.md, "What the answer means").
     */
    @Test
    fun `a registration whose batch was refused is sent again at the next check`() {
        controller.setToken("tok-1", TokenKinds.FCM)
        uploaded(recorder.ids.single(), accepted = false)

        controller.refreshToken()
        assertEquals(2, recorder.events.size)
        assertEquals("tok-1", recorder.events.last().properties[ReservedEvents.PUSH_TOKEN_TOKEN])

        uploaded(recorder.ids.last(), accepted = true)
        controller.refreshToken()
        assertEquals(2, recorder.events.size, "once accepted, it stays sent")
    }

    @Test
    fun `a registration still waiting in the queue is not sent twice`() {
        controller.setToken("tok-1", TokenKinds.FCM)
        controller.refreshToken()
        controller.refreshToken()
        assertEquals(1, recorder.events.size)
    }

    @Test
    fun `a registration that left the queue unconfirmed, trimmed off, is sent again`() {
        controller.setToken("tok-1", TokenKinds.FCM)
        recorder.queued.clear()

        controller.refreshToken()
        assertEquals(2, recorder.events.size)
    }

    /** An install that remembered its token before confirmations existed sends it once after the update. */
    @Test
    fun `a registration remembered by an older SDK is sent once more`() {
        PushTokenStore(preferences).remember("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE, region)
        preferences.edit().remove("push_token_event_id").remove("push_token_confirmed").commit()

        controller.refreshToken()
        assertEquals(1, recorder.events.size)
        uploaded(recorder.ids.single(), accepted = true)
        controller.refreshToken()
        assertEquals(1, recorder.events.size)
    }

    /** A process that died between remembering a registration and queueing it leaves a marker of its own. */
    @Test
    fun `a registration an earlier process never queued is sent again`() {
        preferences.edit()
            .putString("push_token", "tok-1").putString("push_token_kind", TokenKinds.FCM)
            .putString("push_token_status", TokenStatuses.ACTIVE)
            .putString("push_token_locale", region.locale).putString("push_token_timezone", region.timezone)
            .putString("push_token_event_id", "in-flight:an-earlier-process").putBoolean("push_token_confirmed", false)
            .commit()

        controller.refreshToken()
        assertEquals(1, recorder.events.size)
    }

    /**
     * The registry's locale and time zone are what a run picks a template's language and clock by,
     * so a phone switched to Turkish has to say so without waiting for a new token.
     */
    @Test
    fun `a new language or time zone re-registers the same token`() {
        controller.setToken("tok-1", TokenKinds.FCM)
        controller.refreshToken()
        assertEquals(1, recorder.events.size)

        region = region.copy(locale = "tr-TR")
        controller.refreshToken()
        assertEquals(2, recorder.events.size)

        region = region.copy(timezone = "Europe/Istanbul")
        controller.refreshToken()
        assertEquals(3, recorder.events.size)
        controller.refreshToken()
        assertEquals(3, recorder.events.size)
    }

    @Test
    fun `nothing is registered before a token exists, however often the permission changes`() {
        controller.refreshToken()
        assertTrue(recorder.events.isEmpty())
    }

    @Test
    fun `an arriving push is shown and reported received, once`() {
        assertTrue(controller.handleMessage(message))

        val event = recorder.events.single()
        assertEquals(ReservedEvents.PUSH_RECEIVED, event.name)
        assertTrue(event.passive, "it arrived in the background: nobody acted")
        assertEquals("del-1", event.properties[ReservedEvents.PUSH_DELIVERY_ID])
        assertEquals("run-1", event.properties[ReservedEvents.PUSH_RUN_ID])
        assertNull(event.properties[ReservedEvents.PUSH_ACTION_ID])

        val plan = presenter.shown.single()
        assertEquals("Your order shipped", plan.title)
        assertEquals(listOf("view"), plan.buttons.map { it.id })
    }

    @Test
    fun `another sender's message is left alone`() {
        assertFalse(controller.handleMessage(mapOf("some" to "thing")))
        assertTrue(recorder.events.isEmpty())
        assertTrue(presenter.shown.isEmpty())
    }

    /**
     * A push that arrives while notifications are off cannot be shown, but it did arrive, and the
     * registry needs to hear that the permission is gone.
     */
    @Test
    fun `a push arriving with notifications off is still reported, and re-registers the token`() {
        controller.setToken("tok-1", TokenKinds.FCM)
        enabled = false
        recorder.events.clear()

        assertTrue(controller.handleMessage(message))

        assertTrue(presenter.shown.isEmpty(), "nothing can be shown")
        assertEquals(
            listOf(ReservedEvents.PUSH_RECEIVED, ReservedEvents.PUSH_TOKEN),
            recorder.events.map { it.name },
        )
        assertEquals(TokenStatuses.DISABLED, recorder.events.last().properties[ReservedEvents.PUSH_TOKEN_STATUS])
    }

    @Test
    fun `a tap is behaviour, and names the button when there was one`() {
        val payload = PushParser().parse(message)

        controller.reportOpen(payload, actionId = null)
        controller.reportOpen(payload, actionId = "view")

        assertEquals(listOf(ReservedEvents.PUSH_OPENED, ReservedEvents.PUSH_OPENED), recorder.events.map { it.name })
        assertTrue(recorder.events.none { it.passive }, "a tap is someone acting: it belongs to a session")
        assertNull(recorder.events[0].properties[ReservedEvents.PUSH_ACTION_ID])
        assertEquals("view", recorder.events[1].properties[ReservedEvents.PUSH_ACTION_ID])
        assertEquals("del-1", recorder.events[1].properties[ReservedEvents.PUSH_DELIVERY_ID])
    }

    /** A helper so the test reads the payload the way the controller does. */
    private class PushParser {
        fun parse(data: Map<String, String>) = com.funnelbase.sdk.internal.PushParser.parse(data)!!
    }
}
