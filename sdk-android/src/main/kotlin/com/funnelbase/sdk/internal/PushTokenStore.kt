package com.funnelbase.sdk.internal

import android.content.SharedPreferences
import com.funnelbase.sdk.TokenStatuses

/**
 * The last push registration this install sent, so the next one is sent only if it says something
 * new.
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

    fun remember(token: String, kind: String, status: String, region: DeviceRegion = DeviceRegion()) {
        preferences.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_KIND, kind)
            .putString(KEY_STATUS, status)
            .putString(KEY_LOCALE, region.locale)
            .putString(KEY_TIMEZONE, region.timezone)
            .commit()
    }

    /**
     * Forgets the registration without forgetting the token itself is gone from the registry's
     * point of view: a reset means the next person's rows must not inherit it, so the same token
     * is registered again under the new identity.
     */
    fun forget() {
        preferences.edit()
            .remove(KEY_TOKEN).remove(KEY_KIND).remove(KEY_STATUS).remove(KEY_LOCALE).remove(KEY_TIMEZONE)
            .commit()
    }

    companion object {
        fun statusFor(enabled: Boolean): String = if (enabled) TokenStatuses.ACTIVE else TokenStatuses.DISABLED

        private const val KEY_TOKEN = "push_token"
        private const val KEY_KIND = "push_token_kind"
        private const val KEY_STATUS = "push_token_status"
        private const val KEY_LOCALE = "push_token_locale"
        private const val KEY_TIMEZONE = "push_token_timezone"
    }
}
