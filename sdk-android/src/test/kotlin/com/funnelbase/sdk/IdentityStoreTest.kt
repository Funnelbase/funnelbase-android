package com.funnelbase.sdk

import com.funnelbase.sdk.internal.IdentityStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The identity rules of docs/event-contract.md, as the store keeps them. */
class IdentityStoreTest {

    private val preferences = FakeSharedPreferences()
    private val store = IdentityStore(preferences)

    @Test
    fun `device id and anonymous id are made once and then stable`() {
        val device = store.deviceId
        val anonymous = store.anonymousId

        assertNotEquals(device, anonymous, "two ids with two jobs must not be one value")
        assertEquals(device, store.deviceId)
        assertEquals(anonymous, store.anonymousId)
        assertEquals(device, IdentityStore(preferences).deviceId, "a second reader sees the persisted id")
    }

    @Test
    fun `reset restarts the anonymous id and clears the user, and keeps the device id`() {
        val device = store.deviceId
        val anonymous = store.anonymousId
        store.userId = "u_ada"

        store.reset()

        assertNull(store.userId)
        assertNotEquals(anonymous, store.anonymousId, "the next person must not inherit the last one's stretch")
        assertEquals(device, store.deviceId, "the device id names the install, not the person")
    }

    @Test
    fun `first open is pending exactly until it is marked recorded`() {
        assertTrue(store.isFirstOpenPending, "a fresh install owes a first open")

        store.markFirstOpenRecorded()

        assertFalse(store.isFirstOpenPending)
        store.reset()
        assertFalse(store.isFirstOpenPending, "a sign-out is not a reinstall")
        assertFalse(IdentityStore(preferences).isFirstOpenPending, "nor is a relaunch")
    }

    @Test
    fun `an install that predates the device id still owes a first open`() {
        // What the preferences of an app updated from SDK 0.1.0 look like: an anonymous id and
        // nothing else. The update sends `$first_open` once, with the older install time.
        preferences.edit().putString("anonymous_id", "old-anonymous").commit()

        assertEquals("old-anonymous", store.anonymousId)
        assertTrue(store.isFirstOpenPending)
        assertTrue(store.deviceId.isNotBlank())
    }
}
