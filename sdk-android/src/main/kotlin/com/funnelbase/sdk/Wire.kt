package com.funnelbase.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * The `/v1/events` wire format, as docs/event-contract.md describes it.
 *
 * Intentionally a private copy rather than a dependency on the backend's shared DTO module: the
 * SDK ships inside someone else's APK and must not drag server code along, and it has to keep
 * talking to an older backend after an app update anyway. JSON is the contract; these classes
 * are just one end of it.
 */
/**
 * One event. The batch envelope around these — `{sdk_name, sdk_version, sent_at, events[]}` —
 * is assembled directly as text in `AnalyticsClient.batchEnvelope`, because the events reach it
 * already serialized from the on-disk queue.
 */
@Serializable
internal data class RawEvent(
    @SerialName("event_id") val eventId: String,
    @SerialName("event_name") val eventName: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("session_id") val sessionId: String,
    /** One stretch of use between resets: restarts at every sign-out and account switch. */
    @SerialName("anonymous_id") val anonymousId: String,
    /** One install: made on the first launch and never changed. A reinstall makes a new one. */
    @SerialName("device_id") val deviceId: String,
    /** The account, while signed in. Absent rather than null when anonymous. */
    @SerialName("user_id") val userId: String? = null,
    val properties: Map<String, JsonPrimitive> = emptyMap(),
    val traits: Map<String, JsonPrimitive> = emptyMap(),
    val device: DeviceContext,
)

@Serializable
internal data class DeviceContext(
    val platform: String = "android",
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("device_manufacturer") val deviceManufacturer: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    @SerialName("network_type") val networkType: String? = null,
)

internal const val SDK_NAME = "android"

/**
 * Bumped with the wire format: 0.2.0 is the first version to send `device_id` and `$first_open`,
 * 0.3.0 the first with the push events, 0.4.0 the first with `$topic_preference`.
 */
internal const val SDK_VERSION = "0.4.0"

internal val SdkJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Event names the backend reserves; the SDK emits these and rejects them from callers.
 *
 * The same set the backend reserves, and docs/event-contract.md says when each is
 * sent. There is no `$alias`: the first event carrying both an anonymous id and a user id — the
 * `$identify` a sign-in or sign-up sends — is the link between them, so a separate event
 * announcing it would be a second record of the same fact, and one a caller could forget.
 */
object ReservedEvents {
    const val SCREEN_VIEW = "\$screen_view"
    const val SESSION_START = "\$session_start"
    const val IDENTIFY = "\$identify"

    /** Once per install, in front of its first event. Carries [FIRST_OPEN_INSTALLED_AT_MS]. */
    const val FIRST_OPEN = "\$first_open"

    /** This install's push token, on issue and refresh, at every identify and reset, and when the permission changes. */
    const val PUSH_TOKEN = "\$push_token"

    /** A push reached the device and the SDK rendered it. */
    const val PUSH_RECEIVED = "\$push_received"

    /** The person tapped a push, or one of its buttons. */
    const val PUSH_OPENED = "\$push_opened"

    /**
     * The person switched one of the app's notification topics on or off. Carries
     * [TOPIC_PREFERENCE_TOPIC] and [TOPIC_PREFERENCE_SUBSCRIBED].
     */
    const val TOPIC_PREFERENCE = "\$topic_preference"

    /**
     * The events nobody acted on: they carry no session, start none and extend none
     * (docs/event-contract.md, "Passive events"). The backend never stores them in `events` —
     * the token becomes a row of the push registry, the receipt a row of the delivery, a topic
     * choice a row of the person's topic preferences.
     *
     * A topic choice is passive although someone did act: it is a setting, not use of the
     * product, so it must not open a visit or keep one alive.
     */
    val PASSIVE: Set<String> = setOf(PUSH_TOKEN, PUSH_RECEIVED, TOPIC_PREFERENCE)

    /** Properties on [PUSH_TOKEN]: the token, its kind ([TokenKinds]) and its status ([TokenStatuses]). */
    const val PUSH_TOKEN_TOKEN = "token"
    const val PUSH_TOKEN_KIND = "token_kind"
    const val PUSH_TOKEN_STATUS = "status"

    /** Properties on [PUSH_RECEIVED] and [PUSH_OPENED]: which delivery of which run. */
    const val PUSH_DELIVERY_ID = "delivery_id"
    const val PUSH_RUN_ID = "run_id"

    /** Property on [PUSH_OPENED]: the button tapped, absent when the notification itself was. */
    const val PUSH_ACTION_ID = "action_id"

    /** Properties on [TOPIC_PREFERENCE]: the topic's key, and whether the person now wants it. */
    const val TOPIC_PREFERENCE_TOPIC = "topic"
    const val TOPIC_PREFERENCE_SUBSCRIBED = "subscribed"

    /** Property on [SCREEN_VIEW]: the screen's name. */
    const val SCREEN_VIEW_SCREEN_NAME = "screen_name"

    /**
     * Property on [FIRST_OPEN]: when the operating system says the app was installed, in Unix
     * epoch milliseconds. Older than the event itself when the app was installed before it had
     * the SDK — which is what keeps every existing user from counting as new on the day the SDK
     * arrives.
     */
    const val FIRST_OPEN_INSTALLED_AT_MS = "installed_at_ms"
}

/**
 * The `/v1/events` response.
 *
 * Only the fields the SDK acts on; `ignoreUnknownKeys` covers the rest, so the server can add
 * to this response without breaking older app versions.
 */
@Serializable
internal data class IngestResponse(
    val accepted: Int = 0,
    val rejected: List<RejectedEventResponse> = emptyList(),
)

@Serializable
internal data class RejectedEventResponse(
    val eventId: String = "",
    /** Stable code — safe to branch on, unlike the prose reason. */
    val code: String = "",
    val reason: String = "",
)

/** The `GET /v1/topics` response: the app's active topics and where the calling person stands. */
@Serializable
internal data class TopicsResponse(val topics: List<TopicChoice> = emptyList())
