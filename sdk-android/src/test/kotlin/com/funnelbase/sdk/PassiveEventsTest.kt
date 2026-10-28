package com.funnelbase.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The passive rule, on the wire (docs/event-contract.md, "Passive events"). A token refresh and a
 * push arriving in the background happen with nobody acting, so they carry no session — and the
 * backend reads an empty `session_id` as exactly that, refusing the same emptiness on any other
 * event.
 */
class PassiveEventsTest {

    private fun event(name: String, sessionId: String) = RawEvent(
        eventId = "e-1",
        eventName = name,
        occurredAt = "2026-09-20T10:00:00Z",
        sessionId = sessionId,
        anonymousId = "anon-1",
        deviceId = "dev-1",
        properties = emptyMap(),
        device = DeviceContext(),
    )

    @Test
    fun `a passive event goes out with an empty session id`() {
        val json = SdkJson.encodeToString(RawEvent.serializer(), event(ReservedEvents.PUSH_TOKEN, ""))

        assertTrue(json.contains("\"session_id\":\"\""), json)
        assertTrue(json.contains("\"event_name\":\"\$push_token\""), json)
        assertFalse(json.contains("\"user_id\""), "an anonymous install sends no user id at all")
    }

    @Test
    fun `the passive events are the token, the receipt and a topic choice, and an open is not one`() {
        assertEquals(setOf(ReservedEvents.PUSH_TOKEN, ReservedEvents.PUSH_RECEIVED, ReservedEvents.TOPIC_PREFERENCE), ReservedEvents.PASSIVE)
        assertFalse(ReservedEvents.PUSH_OPENED in ReservedEvents.PASSIVE, "a tap is someone acting")
    }

    /** The names the SDK sends have to be the ones the backend reserves, character for character. */
    @Test
    fun `the push event names match the contract`() {
        assertEquals("\$push_token", ReservedEvents.PUSH_TOKEN)
        assertEquals("\$push_received", ReservedEvents.PUSH_RECEIVED)
        assertEquals("\$push_opened", ReservedEvents.PUSH_OPENED)
        assertEquals("token", ReservedEvents.PUSH_TOKEN_TOKEN)
        assertEquals("token_kind", ReservedEvents.PUSH_TOKEN_KIND)
        assertEquals("status", ReservedEvents.PUSH_TOKEN_STATUS)
        assertEquals("delivery_id", ReservedEvents.PUSH_DELIVERY_ID)
        assertEquals("run_id", ReservedEvents.PUSH_RUN_ID)
        assertEquals("action_id", ReservedEvents.PUSH_ACTION_ID)
        assertEquals(setOf("fcm", "apns", "opaque"), setOf(TokenKinds.FCM, TokenKinds.APNS, TokenKinds.OPAQUE))
        assertEquals(setOf("active", "disabled"), setOf(TokenStatuses.ACTIVE, TokenStatuses.DISABLED))
    }
}
