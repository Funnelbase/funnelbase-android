package com.funnelbase.sdk

import com.funnelbase.sdk.internal.DeviceRegion
import com.funnelbase.sdk.internal.PushTokenStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The token is sent as an event, so it is sent again on every refresh, identify, reset and
 * permission change (docs/push.md, "The token"). This store is what keeps that
 * from becoming one event per app launch: the registry only needs to hear what changed.
 */
class PushTokenStoreTest {

    private val preferences = FakeSharedPreferences()
    private val store = PushTokenStore(preferences)

    @Test
    fun `the first registration is worth sending, the same one again is not`() {
        assertTrue(store.shouldSend("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE))
        store.remember("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE)
        assertFalse(store.shouldSend("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE))
    }

    @Test
    fun `a new token, another kind, or a flipped permission is worth sending`() {
        store.remember("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE)

        assertTrue(store.shouldSend("tok-2", TokenKinds.FCM, TokenStatuses.ACTIVE), "the provider refreshed it")
        assertTrue(store.shouldSend("tok-1", TokenKinds.OPAQUE, TokenStatuses.ACTIVE), "another provider's token")
        assertTrue(store.shouldSend("tok-1", TokenKinds.FCM, TokenStatuses.DISABLED), "notifications were turned off")
    }

    @Test
    fun `a phone set to another language or time zone is worth sending`() {
        val here = DeviceRegion("en-US", "America/New_York")
        store.remember("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE, here)

        assertFalse(store.shouldSend("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE, here))
        assertTrue(store.shouldSend("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE, here.copy(locale = "tr-TR")), "a new language")
        assertTrue(store.shouldSend("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE, here.copy(timezone = "Europe/Istanbul")), "a new time zone")
    }

    /**
     * A reset means the next person's events must not be joined to this one's registration, so
     * the store forgets and the next call re-registers the same token under the new identity.
     */
    @Test
    fun `forgetting makes the same token worth sending again`() {
        store.remember("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE)
        store.forget()

        assertTrue(store.shouldSend("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE))
        assertNull(store.token)
    }

    @Test
    fun `the last token survives a restart, so a permission change alone can re-register it`() {
        store.remember("tok-1", TokenKinds.FCM, TokenStatuses.ACTIVE)

        val afterRestart = PushTokenStore(preferences)
        assertEquals("tok-1", afterRestart.token)
        assertEquals(TokenKinds.FCM, afterRestart.kind)
        assertTrue(afterRestart.shouldSend("tok-1", TokenKinds.FCM, TokenStatuses.DISABLED))
    }

    @Test
    fun `a blank token is never worth sending`() {
        assertFalse(store.shouldSend("", TokenKinds.FCM, TokenStatuses.ACTIVE))
        assertFalse(store.shouldSend("  ", TokenKinds.FCM, TokenStatuses.ACTIVE))
    }

    @Test
    fun `a status is active while notifications are allowed and disabled once they are not`() {
        assertEquals(TokenStatuses.ACTIVE, PushTokenStore.statusFor(enabled = true))
        assertEquals(TokenStatuses.DISABLED, PushTokenStore.statusFor(enabled = false))
    }
}
