package com.funnelbase.demo.trading.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * The determinism these assert is the whole reason the demo can have accounts without an auth
 * backend. If it breaks, every sign-in becomes a new person and retention numbers silently
 * become meaningless rather than wrong-looking.
 */
class UserIdTest {

    @Test
    fun `same username always yields the same id`() {
        assertEquals(UserId.forUsername("ada"), UserId.forUsername("ada"))
    }

    @Test
    fun `id is stable across runs, not just within one`() {
        // Pinned to a literal on purpose: comparing two live calls would still pass if the
        // algorithm changed, and changing it invalidates every user_id already sent.
        assertEquals("u_fdee430d40bd57de", UserId.forUsername("ada"))
    }

    @Test
    fun `case and surrounding whitespace do not make a different person`() {
        val expected = UserId.forUsername("ada")
        assertEquals(expected, UserId.forUsername("  ADA  "))
        assertEquals(expected, UserId.forUsername("Ada"))
    }

    @Test
    fun `different usernames yield different ids`() {
        assertNotEquals(UserId.forUsername("ada"), UserId.forUsername("grace"))
    }

    @Test
    fun `id is prefixed and 16 hex characters wide`() {
        val id = UserId.forUsername("grace")
        assertEquals(18, id.length)
        assert(id.startsWith("u_")) { "expected a u_ prefix, got $id" }
        assert(id.drop(2).all { it in "0123456789abcdef" }) { "expected lowercase hex, got $id" }
    }
}
