package com.funnelbase.sdk

import android.content.Intent
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What kind of address a push token is, as the registry records it. */
object TokenKinds {
    /** Firebase Cloud Messaging, on Android and on Apple devices through Firebase. */
    const val FCM = "fcm"

    /** An Apple device token, for a deployment that talks to Apple directly. */
    const val APNS = "apns"

    /** An id some other gateway understands, for a custom integration. */
    const val OPAQUE = "opaque"
}

/**
 * What a client may say about a token. `invalid` exists too, but it is the provider's verdict
 * about a token it refused, so only the server ever writes it.
 */
object TokenStatuses {
    const val ACTIVE = "active"
    const val DISABLED = "disabled"
}

/** One action button under a notification. [id] is what the open reports as `action_id`. */
@Serializable
data class PushButton(
    val id: String,
    val title: String,
    /** Where this button leads, overriding the message's own deep link. */
    @SerialName("deep_link") val deepLink: String? = null,
)

/**
 * A push the platform sent, as it arrived. The host app sees one when a notification is tapped,
 * and routes on [deepLink] or on its own keys in [data].
 */
@Serializable
data class PushPayload(
    @SerialName("delivery_id") val deliveryId: String,
    @SerialName("run_id") val runId: String,
    val title: String = "",
    val body: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("deep_link") val deepLink: String? = null,
    @SerialName("collapse_key") val collapseKey: String? = null,
    /** The Android notification channel the message asked for, if any. */
    @SerialName("channel_id") val channelId: String? = null,
    val buttons: List<PushButton> = emptyList(),
    /** Whatever the campaign added, without the keys the delivery layer uses. */
    val data: Map<String, String> = emptyMap(),
)

/**
 * A tap, handed to the host app: the push, and which button was tapped, or null for the
 * notification itself. Return true from [PushTapHandler.onPushOpened] to say the app has routed
 * it; otherwise the SDK opens the app's launcher screen with the push attached to the intent.
 */
fun interface PushTapHandler {
    fun onPushOpened(payload: PushPayload, actionId: String?): Boolean
}

/**
 * Push, as the SDK sees it: a token to register, a message to render, and a tap to report
 * (docs/push.md).
 *
 * The SDK knows nothing about Firebase. Whoever holds the token and the payload hands them over —
 * `sdk-android-fcm` does it in one service, and an app on another gateway does it itself with an
 * `opaque` token. Reached as `Analytics.push`.
 */
object AnalyticsPush {

    @Volatile
    private var handler: PushTapHandler? = null

    /**
     * Registers this install's push token, sending `$push_token` when it differs from the last
     * one sent or the notification permission has changed since. Safe to call on every launch.
     */
    @JvmStatic
    @JvmOverloads
    fun setToken(token: String, kind: String = TokenKinds.FCM) {
        withController("setToken") { it.setToken(token, kind) }
    }

    /**
     * Re-registers the last token if the notification permission, or the SDK channel's blocked
     * state, has changed. Call it after the permission prompt. The SDK calls it itself at startup,
     * on a configuration change and, with lifecycle tracking on, on returning to the foreground.
     */
    @JvmStatic
    fun refreshToken() {
        withController("refreshToken") { it.refreshToken() }
    }

    /**
     * Renders a message the SDK sent and reports `$push_received`. Returns false, having done
     * nothing, when the message is not ours — another sender's push through the same transport.
     *
     * Safe from any thread: an image, if there is one, is fetched off the caller's.
     */
    @JvmStatic
    fun handleMessage(data: Map<String, String>): Boolean {
        val controller = Analytics.pushController
        if (controller == null) {
            // Not a dropped event we can queue: without initialize there is no queue, no api key
            // and no identity. Initialising in Application.onCreate is what keeps this from
            // happening, because a push starts the process through Application first.
            Log.w(TAG, "A push arrived before Analytics.initialize — nothing was rendered or recorded")
            return false
        }
        return controller.handleMessage(data)
    }

    /**
     * Whether a push can reach the person now: the OS allows this app to post notifications, and
     * the person has not blocked the SDK's own channel ([AnalyticsConfig.pushChannelId]).
     */
    @JvmStatic
    fun notificationsEnabled(): Boolean = Analytics.pushController?.notificationsEnabled() ?: false

    /**
     * The token this install last registered, or null. For a settings or debug screen: the SDK
     * needs no one to hand it back.
     */
    @JvmStatic
    fun registeredToken(): String? = Analytics.pushController?.registeredToken()

    /** Registers the app's router for taps, or clears it with null. */
    @JvmStatic
    fun setTapHandler(handler: PushTapHandler?) {
        this.handler = handler
    }

    /**
     * The push behind the intent that started an activity, or null when it was not a push. The
     * SDK has already reported the open by the time the app reads this; what is left is routing.
     */
    @JvmStatic
    fun payloadFrom(intent: Intent?): PushPayload? {
        val encoded = intent?.getStringExtra(EXTRA_PAYLOAD) ?: return null
        return runCatching { SdkJson.decodeFromString(PushPayload.serializer(), encoded) }.getOrNull()
    }

    /** Which button was tapped, for an intent [payloadFrom] returns a push for. */
    @JvmStatic
    fun actionIdFrom(intent: Intent?): String? = intent?.getStringExtra(EXTRA_ACTION_ID)

    internal fun tapHandler(): PushTapHandler? = handler

    internal fun encode(payload: PushPayload): String = SdkJson.encodeToString(PushPayload.serializer(), payload)

    private inline fun withController(operation: String, block: (com.funnelbase.sdk.internal.PushController) -> Unit) {
        val controller = Analytics.pushController
        if (controller == null) {
            Log.w(TAG, "Analytics.push.$operation called before initialize() — ignoring")
            return
        }
        block(controller)
    }

    /**
     * Extras the SDK puts on the intent it starts when a notification is tapped. Their values
     * predate the package rename and are kept, so a notification shown before an upgrade still
     * routes after it.
     */
    const val EXTRA_PAYLOAD = "com.analytics.sdk.push.PAYLOAD"
    const val EXTRA_ACTION_ID = "com.analytics.sdk.push.ACTION_ID"

    private const val TAG = "Analytics"
}
