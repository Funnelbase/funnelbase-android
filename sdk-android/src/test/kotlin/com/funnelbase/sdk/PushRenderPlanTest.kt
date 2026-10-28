package com.funnelbase.sdk

import com.funnelbase.sdk.internal.PushParser
import com.funnelbase.sdk.internal.PushRenderPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Every decision about the notification, taken before any framework object exists: which channel,
 * which id, whether there is an image to fetch, how many buttons. The platform code that follows
 * only builds what this says.
 */
class PushRenderPlanTest {

    private val config = AnalyticsConfig(apiKey = "k", endpoint = "https://ingest.example.com")

    private fun payload(vararg pairs: Pair<String, String>) = PushParser.parse(
        mapOf("delivery_id" to "del-1", "run_id" to "run-1", "title" to "Hi", "body" to "There", *pairs),
    )!!

    @Test
    fun `a message without a channel of its own lands on the SDK's default`() {
        assertEquals(config.pushChannelId, PushRenderPlan.of(payload(), config).channelId)
        assertEquals("orders", PushRenderPlan.of(payload("android_channel_id" to "orders"), config).channelId)
    }

    /**
     * A collapse key is the sender saying "this replaces the last one of these", and on Android a
     * notification replaces another only when it shares its tag and id. Without one, each delivery
     * is its own notification.
     */
    @Test
    fun `the collapse key decides what a notification replaces, and the delivery id otherwise`() {
        val collapsing = PushRenderPlan.of(payload("collapse_key" to "orders"), config)
        assertEquals("orders", collapsing.tag)
        assertEquals(PushRenderPlan.of(payload("collapse_key" to "orders", "delivery_id" to "del-2"), config).notificationId, collapsing.notificationId)

        val singular = PushRenderPlan.of(payload(), config)
        assertEquals("del-1", singular.tag)
        assertNotEquals(collapsing.notificationId, singular.notificationId)
    }

    /**
     * The image is fetched by the device, so the plan only asks for one when the url is something
     * the SDK will actually open: anything but http or https is dropped rather than attempted.
     */
    @Test
    fun `an image is planned only for an http url`() {
        assertEquals("https://example.com/a.png", PushRenderPlan.of(payload("image_url" to "https://example.com/a.png"), config).imageUrl)
        assertEquals("http://10.0.2.2:9099/media/sample.png", PushRenderPlan.of(payload("image_url" to "http://10.0.2.2:9099/media/sample.png"), config).imageUrl)
        assertNull(PushRenderPlan.of(payload("image_url" to "file:///etc/passwd"), config).imageUrl)
        assertNull(PushRenderPlan.of(payload("image_url" to "content://media/1"), config).imageUrl)
        assertNull(PushRenderPlan.of(payload(), config).imageUrl)
    }

    @Test
    fun `the body is also the expanded text, and buttons keep their order`() {
        val buttons = """[{"id":"view","title":"View"},{"id":"later","title":"Later"}]"""
        val plan = PushRenderPlan.of(payload("buttons" to buttons), config)

        assertEquals("Hi", plan.title)
        assertEquals("There", plan.body)
        assertEquals("There", plan.expandedText, "a long body is unreadable collapsed, so it is always the expanded text too")
        assertEquals(listOf("view", "later"), plan.buttons.map { it.id })
    }

    @Test
    fun `a message with no title falls back to the app's own name`() {
        val plan = PushRenderPlan.of(
            PushParser.parse(mapOf("delivery_id" to "d", "run_id" to "r", "body" to "Body alone"))!!,
            config,
        )
        assertEquals("", plan.title, "the presenter substitutes the app label, which only it can read")
        assertEquals("Body alone", plan.body)
    }
}
