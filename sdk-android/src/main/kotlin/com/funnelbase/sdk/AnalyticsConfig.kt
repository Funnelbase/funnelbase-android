package com.funnelbase.sdk

/**
 * Host-app configuration for the SDK.
 *
 * @param apiKey issued per app in the dashboard. Resolves server-side to a tenant and app —
 *   the SDK never sends either itself.
 * @param endpoint base URL of the ingestion API, e.g. `https://ingest.internal.example.com`.
 * @param flushIntervalSeconds how often a partial batch is uploaded anyway. Trades battery
 *   against how fresh the dashboard is.
 * @param flushBatchSize upload as soon as this many events are queued, without waiting for the
 *   interval.
 * @param maxQueuedEvents hard cap on the offline queue. Past it the oldest events are dropped:
 *   an app that has been offline for weeks should not accumulate an unbounded file.
 * @param sessionTimeoutMinutes inactivity before a new session id is generated.
 * @param trackApplicationLifecycle register lifecycle callbacks. This is what uploads queued
 *   events when the app goes to background — the last reliable moment before the process may
 *   be killed — so leaving it on is almost always right.
 * @param autoTrackScreenViews report a screen view per Activity start. Turn this off in a
 *   single-Activity app (any Compose or Navigation app), where it would emit the host
 *   Activity's name on every foreground and bury the real screen names. Those apps call
 *   [Analytics.screen] per destination instead. Has no effect unless
 *   [trackApplicationLifecycle] is on — the Activity callbacks are what report screen views.
 * @param debugLogging log queue and upload activity to logcat. Leave off in release builds.
 */
data class AnalyticsConfig(
    val apiKey: String,
    val endpoint: String,
    val flushIntervalSeconds: Long = 30,
    val flushBatchSize: Int = 20,
    val maxQueuedEvents: Int = 1_000,
    val sessionTimeoutMinutes: Long = 30,
    val trackApplicationLifecycle: Boolean = true,
    val autoTrackScreenViews: Boolean = true,
    val debugLogging: Boolean = false,
    /**
     * The notification channel a push lands on when the message names none. The SDK creates it if
     * the app has not, so automatic rendering never waits on the app's own channel setup.
     */
    val pushChannelId: String = "analytics-push",
    /** What the person sees in the system's notification settings for [pushChannelId]. */
    val pushChannelName: String = "Notifications",
    /**
     * The small icon every notification needs, as a drawable resource id. Zero means the app's own
     * icon, which is right often enough that push works without any configuration.
     */
    val pushSmallIconResId: Int = 0,
    /**
     * How large an image the SDK will download for a rich notification. Past this the notification
     * is shown without it — a person waiting on a notification is not waiting on a photograph.
     */
    val pushMaxImageBytes: Long = 1_000_000,
) {
    init {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(endpoint.startsWith("http")) { "endpoint must be an absolute http(s) URL" }
        require(flushBatchSize in 1..MAX_BATCH_SIZE) { "flushBatchSize must be between 1 and $MAX_BATCH_SIZE" }
        require(maxQueuedEvents >= flushBatchSize) { "maxQueuedEvents must be at least flushBatchSize" }
    }

    internal val eventsUrl: String get() = "${endpoint.trimEnd('/')}/v1/events"

    /** Where the person's notification topics are read from; see [Analytics.topics]. */
    internal val topicsUrl: String get() = "${endpoint.trimEnd('/')}/v1/topics"

    internal companion object {
        /** Mirrors the ingestion API's own per-batch limit; a larger batch is rejected outright. */
        const val MAX_BATCH_SIZE = 500
    }
}
