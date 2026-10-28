package com.funnelbase.sdk

import android.app.NotificationManager
import com.funnelbase.sdk.internal.HttpTopicSource
import com.funnelbase.sdk.internal.PendingTopicChoice
import com.funnelbase.sdk.internal.NotificationPermission
import com.funnelbase.sdk.internal.TopicChoiceStore
import com.funnelbase.sdk.internal.TopicController
import com.funnelbase.sdk.internal.TopicPipeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Notification topics on the device: the choice goes out as a passive event, the list comes back
 * from the server, and a choice the server may not have yet is laid over its answer.
 */
class TopicsTest {

    private class Sent(val name: String, val properties: Map<String, Any?>, val eventId: String)

    /** Runs everything inline: the worker, the network thread and the main thread are one here. */
    private class FakePipeline : TopicPipeline {
        val sent = mutableListOf<Sent>()
        var anonymousId = "anon-1"
        var userId: String? = null
        var flushes = 0

        override fun flush() {
            flushes += 1
        }

        override fun trackPassive(eventName: String, properties: Map<String, Any?>, enqueued: (eventId: String) -> Unit) {
            val id = "evt-${sent.size + 1}"
            sent += Sent(eventName, properties, id)
            enqueued(id)
        }

        override fun withIdentity(block: (anonymousId: String, userId: String?) -> Unit) = block(anonymousId, userId)
    }

    private val pipeline = FakePipeline()
    private val store = TopicChoiceStore(FakeSharedPreferences())
    private var serverTopics = listOf(
        TopicChoice("promotions", "Promotions", "Offers and discounts", subscribed = true, chosen = false),
        TopicChoice("price_alerts", "Price alerts", null, subscribed = true, chosen = false),
    )
    private val asked = mutableListOf<Pair<String, String?>>()
    private val controller = TopicController(
        pipeline = pipeline,
        store = store,
        source = { anonymousId, userId ->
            asked += anonymousId to userId
            serverTopics
        },
        network = { it.run() },
        deliver = { it.run() },
    )

    private fun fetch(): List<TopicChoice> {
        var result: Result<List<TopicChoice>>? = null
        controller.fetch { result = it }
        return result!!.getOrThrow()
    }

    private fun line(eventId: String) =
        """{"event_id":"$eventId","event_name":"${'$'}topic_preference","session_id":"","anonymous_id":"anon-1"}"""

    @Test
    fun `a choice is the passive topic preference carrying the key and the boolean`() {
        controller.set("promotions", false)

        val event = pipeline.sent.single()
        assertEquals("\$topic_preference", event.name)
        assertEquals(mapOf("topic" to "promotions", "subscribed" to false), event.properties)
    }

    @Test
    fun `the topic preference is passive and goes out with no session`() {
        assertTrue(ReservedEvents.TOPIC_PREFERENCE in ReservedEvents.PASSIVE)
        val event = RawEvent(
            eventId = "e-1",
            eventName = ReservedEvents.TOPIC_PREFERENCE,
            occurredAt = "2026-09-29T10:00:00Z",
            sessionId = "",
            anonymousId = "anon-1",
            deviceId = "dev-1",
            userId = "u-1",
            properties = mapOf("topic" to kotlinx.serialization.json.JsonPrimitive("promotions"), "subscribed" to kotlinx.serialization.json.JsonPrimitive(false)),
            device = DeviceContext(),
        )
        val json = SdkJson.encodeToString(RawEvent.serializer(), event)

        assertTrue(json.contains("\"session_id\":\"\""), json)
        assertTrue(json.contains("\"properties\":{\"topic\":\"promotions\",\"subscribed\":false}"), json)
        assertTrue(json.contains("\"user_id\":\"u-1\""), json)
        assertTrue(json.contains("\"device_id\":\"dev-1\""), json)
    }

    @Test
    fun `the read route names the person, and leaves the user out while signed out`() {
        assertEquals(
            "https://i.example.com/v1/topics?anonymous_id=a+b%2F1",
            HttpTopicSource.requestUrl("https://i.example.com/v1/topics", "a b/1", null),
        )
        assertEquals(
            "https://i.example.com/v1/topics?anonymous_id=a1&user_id=trader%40x",
            HttpTopicSource.requestUrl("https://i.example.com/v1/topics", "a1", "trader@x"),
        )
    }

    @Test
    fun `the response parses, and an unreadable one is an unavailable error`() {
        val topics = HttpTopicSource.parse(
            """{"topics":[{"key":"promotions","name":"Promotions","description":"Offers","subscribed":false,"chosen":true,"extra":1}]}""",
        )
        assertEquals(listOf(TopicChoice("promotions", "Promotions", "Offers", subscribed = false, chosen = true)), topics)

        assertThrows<TopicsException.Malformed> { HttpTopicSource.parse("<html>502</html>") }
        assertEquals(null, HttpTopicSource.parse("""{"topics":[{"key":"a","name":"A","subscribed":true,"chosen":false}]}""").single().description)
    }

    @Test
    fun `fetch asks for the current person and returns the server's answer as it is`() {
        pipeline.userId = "u-1"

        assertEquals(serverTopics, fetch())
        assertEquals(listOf("anon-1" to "u-1"), asked)
    }

    @Test
    fun `a choice is flushed at once rather than at the next interval`() {
        controller.set("promotions", false)

        assertEquals(1, pipeline.flushes)
    }

    @Test
    fun `a pending choice is laid over the server's answer, as the person's own`() {
        controller.set("promotions", false)

        val promotions = fetch().first { it.key == "promotions" }
        assertFalse(promotions.subscribed)
        assertTrue(promotions.chosen, "it is the person's own choice")
        assertTrue(fetch().first { it.key == "price_alerts" }.subscribed, "untouched topics are the server's")
        assertEquals(setOf("promotions"), store.pending().keys, "and it stays pending: nothing has confirmed it")
    }

    @Test
    fun `a pending choice is released once the server is seen holding it`() {
        controller.set("promotions", false)
        serverTopics = serverTopics.map { if (it.key == "promotions") it.copy(subscribed = false, chosen = true) else it }

        assertFalse(fetch().first { it.key == "promotions" }.subscribed)
        assertTrue(store.pending().isEmpty())

        // From here the server decides: a choice made on another device shows up.
        serverTopics = serverTopics.map { if (it.key == "promotions") it.copy(subscribed = true) else it }
        assertTrue(fetch().first { it.key == "promotions" }.subscribed)
    }

    @Test
    fun `a default that happens to match does not release it`() {
        controller.set("promotions", true)

        fetch()
        assertEquals(setOf("promotions"), store.pending().keys, "the default is not the server holding the choice")
    }

    @Test
    fun `a server holding the opposite choice does not release it`() {
        controller.set("promotions", false)
        serverTopics = serverTopics.map { if (it.key == "promotions") it.copy(subscribed = true, chosen = true) else it }

        assertFalse(fetch().first { it.key == "promotions" }.subscribed, "an older choice of the person's, still in flight behind ours")
        assertEquals(setOf("promotions"), store.pending().keys)
    }

    @Test
    fun `an upload refused for good releases the choices in it, and no other`() {
        controller.set("promotions", false)
        controller.set("price_alerts", false)
        controller.onBatchRejected(listOf(line("evt-1")))

        assertEquals(setOf("price_alerts"), store.pending().keys)
        assertTrue(fetch().first { it.key == "promotions" }.subscribed)
    }

    @Test
    fun `the newest choice for a topic is the one pending`() {
        controller.set("promotions", false)
        controller.set("promotions", true)

        assertTrue(fetch().first { it.key == "promotions" }.subscribed)
        assertEquals("evt-2", store.pending().getValue("promotions").eventId)
    }

    @Test
    fun `a reset forgets the last person's choices`() {
        controller.set("promotions", false)
        controller.onIdentityReset()

        assertEquals(serverTopics, fetch())
    }

    @Test
    fun `a choice for a topic the server no longer lists is released`() {
        controller.set("old_topic", false)

        assertEquals(listOf("promotions", "price_alerts"), fetch().map { it.key })
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `a failed read is an error, delivered through the callback`() {
        val failing = TopicController(
            pipeline = pipeline,
            store = store,
            source = { _, _ -> throw TopicsException.Http(503, """{"error":"topics_unavailable"}""") },
            network = { it.run() },
            deliver = { it.run() },
        )
        var result: Result<List<TopicChoice>>? = null
        failing.fetch { result = it }

        val error = result!!.exceptionOrNull()
        assertTrue(error is TopicsException.Http)
        assertEquals(503, (error as TopicsException.Http).status)
    }

    @Test
    fun `anything else thrown while reading is a network error`() {
        val failing = TopicController(
            pipeline = pipeline,
            store = store,
            source = { _, _ -> throw java.net.SocketTimeoutException("timed out") },
            network = { it.run() },
            deliver = { it.run() },
        )
        var result: Result<List<TopicChoice>>? = null
        failing.fetch { result = it }

        assertTrue(result!!.exceptionOrNull() is TopicsException.Network)
    }

    @Test
    fun `the overlay rule on its own`() {
        val (overlaid, kept) = TopicChoiceStore.overlay(
            serverTopics,
            mapOf("price_alerts" to PendingTopicChoice(false, "e"), "gone" to PendingTopicChoice(true, "f")),
        )

        assertEquals(TopicChoice("price_alerts", "Price alerts", null, subscribed = false, chosen = true), overlaid[1])
        assertEquals(serverTopics[0], overlaid[0])
        assertEquals(setOf("price_alerts"), kept.keys)
    }

    @Test
    fun `the topic names match the contract`() {
        assertEquals("\$topic_preference", ReservedEvents.TOPIC_PREFERENCE)
        assertEquals("topic", ReservedEvents.TOPIC_PREFERENCE_TOPIC)
        assertEquals("subscribed", ReservedEvents.TOPIC_PREFERENCE_SUBSCRIBED)
    }

    @Test
    fun `a blocked SDK channel disables push as surely as the app switch`() {
        assertTrue(NotificationPermission.allowed(appEnabled = true, channelImportance = null), "no channel yet blocks nothing")
        assertTrue(NotificationPermission.allowed(appEnabled = true, channelImportance = NotificationManager.IMPORTANCE_DEFAULT))
        assertTrue(NotificationPermission.allowed(appEnabled = true, channelImportance = NotificationManager.IMPORTANCE_LOW))
        assertFalse(NotificationPermission.allowed(appEnabled = true, channelImportance = NotificationManager.IMPORTANCE_NONE))
        assertFalse(NotificationPermission.allowed(appEnabled = false, channelImportance = NotificationManager.IMPORTANCE_DEFAULT))
        assertFalse(NotificationPermission.allowed(appEnabled = false, channelImportance = null))
    }
}
