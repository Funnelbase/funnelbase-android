package com.funnelbase.sdk

import com.funnelbase.sdk.internal.PushParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What Funnelbase puts in a message's `data` map, read back (docs/push.md, "The payload"). Every value there is a string, buttons included, because that is
 * all Firebase carries — so the parser is where a malformed payload stops being our problem.
 */
class PushParserTest {

    private fun data(vararg pairs: Pair<String, String>) = mapOf(
        "delivery_id" to "del-1",
        "run_id" to "run-1",
        "title" to "Your order shipped",
        "body" to "It arrives tomorrow",
        *pairs,
    )

    @Test
    fun `a message carries the delivery, the run and the text`() {
        val payload = PushParser.parse(data())!!

        assertEquals("del-1", payload.deliveryId)
        assertEquals("run-1", payload.runId)
        assertEquals("Your order shipped", payload.title)
        assertEquals("It arrives tomorrow", payload.body)
        assertNull(payload.imageUrl)
        assertNull(payload.deepLink)
        assertTrue(payload.buttons.isEmpty())
        assertTrue(payload.data.isEmpty())
    }

    /**
     * Another sender's message reaching the same app must be left alone: without our two ids
     * there is nothing to report a receipt against, so it is not ours to render.
     */
    @Test
    fun `a message without our ids is not ours`() {
        assertNull(PushParser.parse(emptyMap()))
        assertNull(PushParser.parse(mapOf("delivery_id" to "del-1")))
        assertNull(PushParser.parse(mapOf("run_id" to "run-1")))
        assertNull(PushParser.parse(data("delivery_id" to " ")))
    }

    @Test
    fun `the image, the deep link and the channel come through, and the app's own keys stay apart`() {
        val payload = PushParser.parse(
            data(
                "image_url" to "https://example.com/a.png",
                "deep_link" to "trade://instrument/AAPL",
                "collapse_key" to "orders",
                "android_channel_id" to "orders",
                "order_id" to "42",
            ),
        )!!

        assertEquals("https://example.com/a.png", payload.imageUrl)
        assertEquals("trade://instrument/AAPL", payload.deepLink)
        assertEquals("orders", payload.collapseKey)
        assertEquals("orders", payload.channelId)
        // Only the keys the app sent, never the ones the delivery layer added.
        assertEquals(mapOf("order_id" to "42"), payload.data)
    }

    @Test
    fun `buttons arrive as one json string and keep their order`() {
        val buttons = """[{"id":"view","title":"View order","deep_link":"trade://orders/42"},{"id":"later","title":"Later"}]"""
        val payload = PushParser.parse(data("buttons" to buttons))!!

        assertEquals(listOf("view", "later"), payload.buttons.map { it.id })
        assertEquals("View order", payload.buttons[0].title)
        assertEquals("trade://orders/42", payload.buttons[0].deepLink)
        assertNull(payload.buttons[1].deepLink)
    }

    /**
     * A payload we cannot read in part is still a notification worth showing: the text is the
     * message, the buttons are a bonus. A broken button list therefore costs the buttons, not
     * the push.
     */
    @Test
    fun `malformed, nameless or excess buttons are dropped rather than failing the message`() {
        assertTrue(PushParser.parse(data("buttons" to "not json"))!!.buttons.isEmpty())
        assertTrue(PushParser.parse(data("buttons" to """{"id":"view"}"""))!!.buttons.isEmpty())
        assertTrue(PushParser.parse(data("buttons" to """[{"id":"","title":"No id"},{"id":"x","title":" "}]"""))!!.buttons.isEmpty())

        val four = (1..4).joinToString(",", "[", "]") { """{"id":"b$it","title":"B$it"}""" }
        assertEquals(listOf("b1", "b2", "b3"), PushParser.parse(data("buttons" to four))!!.buttons.map { it.id })
    }
}
