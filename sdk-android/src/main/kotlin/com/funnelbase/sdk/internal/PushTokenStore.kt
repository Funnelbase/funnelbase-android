package com.funnelbase.sdk.internal

import android.content.SharedPreferences
import com.funnelbase.sdk.TokenStatuses

/**
 * The last push registration this install sent, so the next one is sent only if it says something
 * new — or if the last never arrived: it is confirmed only by a batch the server accepted.
 *
 * The token is an event, and the contract has it sent on issue and refresh, at every identify and
 * reset, and whenever the notification permission changes — which on its own would be an event per
 * launch. The registry is a row per install and token kind, so repeating an unchanged registration
 * tells it nothing.
 *
 * Written with `commit()` like identity: the registration must be on disk before the event that
 * carries it, or a crash in between loses the token and re-sends it as if it were new.
 */
/**
 * Where the phone is set to: the language a template's copy is chosen by, and the clock its dates
 * are shown in. Part of a registration because the registry is where a run reads them from.
 */
internal data class DeviceRegion(val locale: String = "", val timezone: String = "")

internal class PushTokenStore(private val preferences: SharedPreferences) {

    val token: String? get() = preferences.getString(KEY_TOKEN, null)
    val kind: String? get() = preferences.getString(KEY_KIND, null)
    private val status: String? get() = preferences.getString(KEY_STATUS, null)

    /**
     * A new token, another kind, a flipped permission — or a phone now set to another language or
     * time zone, which the registry would otherwise keep sending the old copy by.
     */
    fun shouldSend(token: String, kind: String, status: String, region: DeviceRegion = DeviceRegion()): Boolean {
        if (token.isBlank()) return false
        return token != this.token || kind != this.kind || status != this.status ||
            region.locale != preferences.getString(KEY_LOCALE, null) ||
            region.timezone != preferences.getString(KEY_TIMEZONE, null)
    }

    /**
     * Whether the server has accepted a batch carrying the last registration (a 2xx). Missing — an
     * install that remembered its token before this flag existed — reads as not confirmed, so it is
     * registered once more after the update, which also heals one lost before.
     */
    val confirmed: Boolean get() = preferences.getBoolean(KEY_CONFIRMED, false)

    /** The `$push_token` event carrying the last registration; [IN_FLIGHT] until the queue has it, null once refused. */
    val eventId: String? get() = preferences.getString(KEY_EVENT_ID, null)

    /** Remembers a registration about to be sent: unconfirmed, and in flight until [sentAs] names its event. */
    fun remember(token: String, kind: String, status: String, region: DeviceRegion = DeviceRegion()) {
        preferences.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_KIND, kind)
            .putString(KEY_STATUS, status)
            .putString(KEY_LOCALE, region.locale)
            .putString(KEY_TIMEZONE, region.timezone)
            .putString(KEY_EVENT_ID, IN_FLIGHT)
            .putBoolean(KEY_CONFIRMED, false)
            .commit()
    }

    /** The registration's event is in the queue, as [eventId]. */
    @Synchronized
    fun sentAs(eventId: String) {
        if (this.eventId == IN_FLIGHT) preferences.edit().putString(KEY_EVENT_ID, eventId).commit()
    }

    /** An accepted batch: confirmed if it carried the registration's event. */
    @Synchronized
    fun confirmIfAmong(eventLines: List<String>) {
        if (carries(eventLines)) preferences.edit().putBoolean(KEY_CONFIRMED, true).commit()
    }

    /** A refused batch: if it carried the registration's event, that copy is gone and the next check resends it. */
    @Synchronized
    fun releaseIfAmong(eventLines: List<String>) {
        if (carries(eventLines)) preferences.edit().remove(KEY_EVENT_ID).commit()
    }

    private fun carries(eventLines: List<String>): Boolean {
        val id = eventId?.takeIf { it != IN_FLIGHT && !isStaleMarker(it) } ?: return false
        val needle = "\"event_id\":\"$id\""
        return eventLines.any { needle in it }
    }

    /**
     * Forgets the registration without forgetting the token itself is gone from the registry's
     * point of view: a reset means the next person's rows must not inherit it, so the same token
     * is registered again under the new identity.
     */
    fun forget() {
        preferences.edit()
            .remove(KEY_TOKEN).remove(KEY_KIND).remove(KEY_STATUS).remove(KEY_LOCALE).remove(KEY_TIMEZONE)
            .remove(KEY_EVENT_ID).remove(KEY_CONFIRMED)
            .commit()
    }

    companion object {
        fun statusFor(enabled: Boolean): String = if (enabled) TokenStatuses.ACTIVE else TokenStatuses.DISABLED

        private const val KEY_TOKEN = "push_token"
        private const val KEY_KIND = "push_token_kind"
        private const val KEY_STATUS = "push_token_status"
        private const val KEY_LOCALE = "push_token_locale"
        private const val KEY_TIMEZONE = "push_token_timezone"
        private const val KEY_EVENT_ID = "push_token_event_id"
        private const val KEY_CONFIRMED = "push_token_confirmed"

        private const val IN_FLIGHT_PREFIX = "in-flight:"

        /**
         * Sent, but not yet in the queue: counts as queued, so a check right after a send does not
         * repeat it. Unique to this process, so a marker left by one that died before its event was
         * queued reads as lost at the next launch, and is sent again.
         */
        val IN_FLIGHT: String = IN_FLIGHT_PREFIX + java.util.UUID.randomUUID()

        /** A marker from an earlier process: its event may never have reached the queue. */
        fun isStaleMarker(eventId: String): Boolean = eventId.startsWith(IN_FLIGHT_PREFIX) && eventId != IN_FLIGHT
    }
}
