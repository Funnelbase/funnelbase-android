package com.funnelbase.sdk.internal

import com.funnelbase.sdk.AnalyticsConfig
import com.funnelbase.sdk.PushPayload
import com.funnelbase.sdk.ReservedEvents

/** How the SDK records an event, so the push side can be exercised without a device. */
internal interface EventRecorder {
    fun track(eventName: String, properties: Map<String, Any?>)

    /**
     * An event nobody acted on: it carries no session, starts none and extends none
     * (docs/event-contract.md, "Passive events").
     */
    fun trackPassive(eventName: String, properties: Map<String, Any?>)
}

/** Shows a notification. Separate so every decision above it is testable on a plain JVM. */
internal interface NotificationPresenter {
    fun show(plan: PushRenderPlan)
}

/**
 * The push side of the SDK: register a token, render an arriving message, report a tap.
 *
 * Which of the three is passive is the part that matters. A token refresh and a push arriving in
 * the background happen with nobody acting, so they carry no session; a tap is behaviour like any
 * other event, so it belongs to one.
 */
internal class PushController(
    private val recorder: EventRecorder,
    private val tokens: PushTokenStore,
    private val presenter: NotificationPresenter,
    private val config: AnalyticsConfig,
    private val notificationsEnabled: () -> Boolean,
    /** The phone's language and time zone now; a change to either re-registers the token. */
    private val region: () -> DeviceRegion = { DeviceRegion() },
) {

    fun notificationsEnabled(): Boolean = notificationsEnabled.invoke()

    /** The token last registered, for a host app's settings screen. */
    fun registeredToken(): String? = tokens.token

    /** Registers [token], sending the event only when it says something the registry lacks. */
    fun setToken(token: String, kind: String) {
        val status = PushTokenStore.statusFor(notificationsEnabled())
        val region = region()
        if (!tokens.shouldSend(token, kind, status, region)) return
        tokens.remember(token, kind, status, region)
        recorder.trackPassive(
            ReservedEvents.PUSH_TOKEN,
            mapOf(
                ReservedEvents.PUSH_TOKEN_TOKEN to token,
                ReservedEvents.PUSH_TOKEN_KIND to kind,
                ReservedEvents.PUSH_TOKEN_STATUS to status,
            ),
        )
    }

    /**
     * Re-registers the last token when the notification permission, the phone's language or its
     * time zone has changed under it. The SDK calls it at startup and on a configuration change.
     */
    fun refreshToken() {
        val token = tokens.token ?: return
        setToken(token, tokens.kind ?: com.funnelbase.sdk.TokenKinds.FCM)
    }

    /**
     * A sign-in, an account switch or a sign-out: the same device is now a different person, so
     * the registration is sent again to be joined to them.
     */
    fun onIdentityChanged() {
        val token = tokens.token ?: return
        val kind = tokens.kind ?: com.funnelbase.sdk.TokenKinds.FCM
        tokens.forget()
        setToken(token, kind)
    }

    /**
     * Renders a message and reports it received. False, having done nothing, when the message is
     * not ours.
     *
     * The receipt is sent whether or not the notification can be shown: it did arrive, and a run's
     * numbers should say so. A push arriving while notifications are off also tells us the
     * permission is gone, which the registry has no other way to learn.
     */
    fun handleMessage(data: Map<String, String>): Boolean {
        val payload = PushParser.parse(data) ?: return false
        reportDelivery(ReservedEvents.PUSH_RECEIVED, payload, actionId = null, passive = true)

        if (notificationsEnabled()) {
            presenter.show(PushRenderPlan.of(payload, config))
        } else {
            refreshToken()
        }
        return true
    }

    /** A tap on the notification, or on one of its buttons. */
    fun reportOpen(payload: PushPayload, actionId: String?) {
        reportDelivery(ReservedEvents.PUSH_OPENED, payload, actionId, passive = false)
    }

    private fun reportDelivery(eventName: String, payload: PushPayload, actionId: String?, passive: Boolean) {
        val properties = buildMap<String, Any?> {
            put(ReservedEvents.PUSH_DELIVERY_ID, payload.deliveryId)
            put(ReservedEvents.PUSH_RUN_ID, payload.runId)
            actionId?.takeIf { it.isNotBlank() }?.let { put(ReservedEvents.PUSH_ACTION_ID, it) }
        }
        if (passive) recorder.trackPassive(eventName, properties) else recorder.track(eventName, properties)
    }
}
