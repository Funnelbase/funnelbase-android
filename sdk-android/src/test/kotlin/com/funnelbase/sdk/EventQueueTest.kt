package com.funnelbase.sdk

import com.funnelbase.sdk.internal.EventQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class EventQueueTest {

    @TempDir
    lateinit var tempDir: Path

    private fun queue(maxEvents: Int = 100) =
        EventQueue(File(tempDir.toFile(), "queue.jsonl"), maxEvents)

    @Test
    fun `peeks in insertion order and leaves the queue intact`() {
        val queue = queue()
        queue.enqueue("""{"n":1}""")
        queue.enqueue("""{"n":2}""")

        assertEquals(listOf("""{"n":1}""", """{"n":2}"""), queue.peek(10))
        assertEquals(2, queue.size(), "peek must not consume")
    }

    @Test
    fun `a failed upload leaves events queued for retry`() {
        val queue = queue()
        repeat(3) { queue.enqueue("""{"n":$it}""") }

        queue.peek(3)
        // No removeFirst — this is what an upload failure looks like.
        assertEquals(3, queue.size())
    }

    @Test
    fun `removeFirst drops only the uploaded prefix`() {
        val queue = queue()
        repeat(5) { queue.enqueue("""{"n":$it}""") }

        queue.removeFirst(2)

        assertEquals(3, queue.size())
        assertEquals(listOf("""{"n":2}""", """{"n":3}""", """{"n":4}"""), queue.peek(10))
    }

    @Test
    fun `drops the oldest events once the cap is reached`() {
        val queue = queue(maxEvents = 3)
        repeat(5) { queue.enqueue("""{"n":$it}""") }

        assertEquals(3, queue.size())
        assertEquals(
            listOf("""{"n":2}""", """{"n":3}""", """{"n":4}"""),
            queue.peek(10),
            "the newest events are the ones worth keeping",
        )
    }

    @Test
    fun `rejects an event containing a newline, which would break line framing`() {
        assertThrows(IllegalArgumentException::class.java) {
            queue().enqueue("{\"n\":1}\n{\"injected\":true}")
        }
    }

    @Test
    fun `an empty queue peeks empty and reports zero`() {
        val queue = queue()
        assertTrue(queue.peek(10).isEmpty())
        assertEquals(0, queue.size())
        // Removing from an empty queue is a no-op, not a crash.
        queue.removeFirst(5)
        assertEquals(0, queue.size())
    }

    @Test
    fun `survives being drained completely and refilled`() {
        val queue = queue()
        queue.enqueue("""{"n":1}""")
        queue.removeFirst(1)
        assertEquals(0, queue.size())

        queue.enqueue("""{"n":2}""")
        assertEquals(listOf("""{"n":2}"""), queue.peek(10))
    }
}
