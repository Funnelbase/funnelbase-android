package com.funnelbase.sdk

import com.funnelbase.sdk.internal.SessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionManagerTest {

    private val timeout = 30L * 60 * 1000
    private val sessions = SessionManager(FakeSharedPreferences(), timeoutMillis = timeout)

    @Test
    fun `the first event opens a session and the next one within the timeout joins it`() {
        val first = sessions.current(now = 1_000)
        val second = sessions.current(now = 1_000 + timeout)

        assertTrue(first.isNew)
        assertFalse(second.isNew)
        assertEquals(first.id, second.id)
    }

    @Test
    fun `an event past the timeout opens a new session`() {
        val first = sessions.current(now = 1_000)
        val later = sessions.current(now = 1_000 + timeout + 1)

        assertTrue(later.isNew)
        assertNotEquals(first.id, later.id)
    }

    @Test
    fun `a reset starts a new session however recent the last event`() {
        val before = sessions.current(now = 1_000)

        sessions.clear()
        val after = sessions.current(now = 1_001)

        assertTrue(after.isNew, "one visit never spans two people")
        assertNotEquals(before.id, after.id)
    }
}
