package com.funnelbase.sdk.internal

import android.content.Context
import android.util.Log
import com.funnelbase.sdk.AnalyticsConfig
import com.funnelbase.sdk.DeviceContext
import com.funnelbase.sdk.RawEvent
import com.funnelbase.sdk.ReservedEvents
import com.funnelbase.sdk.SDK_NAME
import com.funnelbase.sdk.SDK_VERSION
import com.funnelbase.sdk.SdkJson
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Everything the SDK actually does. [com.funnelbase.sdk.Analytics] is a thin facade over one
 * instance of this.
 *
 * All work happens on a single background thread. `track` from the UI thread does nothing but
 * capture when the event happened and hand it over, so the host app never pays for
 * serialization, disk, or network on a frame.
 *
 * Identity and session are resolved on that thread too, in queue order. `reset()` is queued
 * like everything else, so an event tracked right after a sign-out — the welcome screen the
 * app navigates to — must see the cleared identity, not the one the calling thread still
 * had. Reading it at call time attributed those events to the user who had just left, under
 * the anonymous id that was minted for the next person.
 */
internal class AnalyticsClient(
    context: Context,
    private val config: AnalyticsConfig,
) : EventRecorder, TopicPipeline {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val identity = IdentityStore(preferences)
    private val sessions = SessionManager(
        preferences,
        timeoutMillis = TimeUnit.MINUTES.toMillis(config.sessionTimeoutMinutes),
    )
    private val queue = EventQueue(File(appContext.filesDir, QUEUE_FILE_NAME), config.maxQueuedEvents)
    private val uploader = EventUploader(config.eventsUrl, config.apiKey, config.debugLogging)
    private val device: DeviceContext = DeviceInfo.collect(appContext)
    private val installedAtMillis: Long? = DeviceInfo.installedAtMillis(appContext)

    // Single thread, so queue writes and uploads are serialized without further locking, and a
    // slow upload can never overlap itself.
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "analytics-sdk").apply { isDaemon = true }
        }

    /**
     * Told of every batch the server refused for good (a 4xx) and the queue dropped, with its
     * serialized events. Worker thread. How a pending topic choice learns it will never arrive.
     */
    @Volatile
    internal var onBatchRejected: ((List<String>) -> Unit)? = null

    /** Told of every reset — a sign-out or an account switch — once it is done. Worker thread. */
    @Volatile
    internal var onFreshStart: (() -> Unit)? = null

    init {
        executor.scheduleWithFixedDelay(
            { runCatching { drainQueue() }.onFailure { log("Scheduled flush failed: ${it.message}") } },
            config.flushIntervalSeconds,
            config.flushIntervalSeconds,
            TimeUnit.SECONDS,
        )
    }

    override fun track(eventName: String, properties: Map<String, Any?>) {
        // Captured on the calling thread, not the background one: `occurred_at` must be when
        // the thing happened, not when the queue got around to it.
        val occurredAt = Instant.now()
        onWorker(eventName) { record(eventName, occurredAt, properties, emptyMap(), identity.userId) }
    }

    /**
     * An event nobody acted on — a refreshed push token, a push arriving in the background
     * (docs/event-contract.md, "Passive events").
     *
     * It carries no session: the session is left untouched, so it opens none and, just as
     * importantly, extends none. A push at three in the morning must not make that a visit.
     * `$first_open` is skipped for the same reason — an install's first sighting is the person
     * opening the app, not the server reaching it.
     */
    override fun trackPassive(eventName: String, properties: Map<String, Any?>) =
        trackPassive(eventName, properties) { }

    override fun trackPassive(eventName: String, properties: Map<String, Any?>, enqueued: (eventId: String) -> Unit) {
        val occurredAt = Instant.now()
        onWorker(eventName) {
            val event = buildEvent(eventName, occurredAt, NO_SESSION, properties.toJsonProperties(), emptyMap(), identity.userId)
            enqueue(event)
            enqueued(event.eventId)
            if (queue.size() >= config.flushBatchSize) drainQueue()
        }
    }

    override fun withIdentity(block: (anonymousId: String, userId: String?) -> Unit) {
        onWorker("identity read") { block(identity.anonymousId, identity.userId) }
    }

    /**
     * Sign-in, sign-up, or the re-identify of a launch while signed in. The `$identify` carries
     * the anonymous id and the user id together, and that is the link between them: there is
     * no alias, and nothing else to call.
     *
     * A different user than the one already signed in is an account switch, which the contract
     * treats as a reset: the anonymous id and the session restart first, so the new account
     * gets a fresh anonymous id that links to it alone, and the old one keeps everything it did.
     */
    fun identify(userId: String, traits: Map<String, Any?>) {
        val occurredAt = Instant.now()
        onWorker(ReservedEvents.IDENTIFY) {
            val current = identity.userId
            if (current != null && current != userId) startFresh()
            identity.userId = userId
            record(ReservedEvents.IDENTIFY, occurredAt, emptyMap(), traits, userId)
        }
    }

    private fun onWorker(what: String, block: () -> Unit) {
        executor.execute {
            runCatching(block).onFailure { log("Failed to record $what: ${it.message}") }
        }
    }

    /** Worker thread only: reads identity and session as they are *now*, after any queued reset. */
    private fun record(
        eventName: String,
        occurredAt: Instant,
        properties: Map<String, Any?>,
        traits: Map<String, Any?>,
        userId: String?,
    ) {
        // Judged at the event's own time, so a slow upload ahead of it in the queue cannot
        // push it across the inactivity timeout.
        val session = sessions.current(occurredAt.toEpochMilli())

        // A new session's `$session_start` has to be enqueued before the event that opened
        // it, or the session's first event precedes its own start.
        if (session.isNew) {
            enqueue(buildEvent(ReservedEvents.SESSION_START, occurredAt, session.id, emptyMap(), emptyMap(), userId))
        }
        // `$first_open` once per install, in front of its first event and after that session's
        // start, at the same instant and with the same identity. An app that restores a
        // signed-in user before its first event sends it signed in, so the account's first
        // sighting moves back to the install time too. Checked on its own rather than under
        // `session.isNew`: an install that updated to this SDK version may still be inside a
        // session the old version persisted.
        if (identity.isFirstOpenPending) {
            val installedAt = installedAtMillis?.let { mapOf(ReservedEvents.FIRST_OPEN_INSTALLED_AT_MS to it) }
            enqueue(buildEvent(ReservedEvents.FIRST_OPEN, occurredAt, session.id, installedAt.orEmpty().toJsonProperties(), emptyMap(), userId))
            // Marked after the enqueue: a process death between the two costs a duplicate
            // `$first_open`, the other order costs the install its only one.
            identity.markFirstOpenRecorded()
        }
        enqueue(
            buildEvent(
                eventName,
                occurredAt,
                session.id,
                properties.toJsonProperties(),
                traits.toJsonProperties(),
                userId,
            ),
        )
        if (queue.size() >= config.flushBatchSize) drainQueue()
    }

    override fun flush() {
        executor.execute { runCatching { drainQueue() }.onFailure { log("Flush failed: ${it.message}") } }
    }

    /**
     * Logout. Uploads what is already queued before clearing identity — those events belong to
     * the user who is leaving, and dropping them would lose the end of their session.
     */
    fun reset() {
        executor.execute {
            runCatching { startFresh() }.onFailure { log("Reset failed: ${it.message}") }
        }
    }

    /**
     * Worker thread only. The reset of the contract: a new anonymous id and a new session, so
     * one visit never spans two people and the next person never inherits the last one's
     * anonymous history. The device id stays — it names the install, not the person.
     */
    private fun startFresh() {
        drainQueue()
        identity.reset()
        sessions.clear()
        onFreshStart?.invoke()
    }

    private fun enqueue(event: RawEvent) {
        queue.enqueue(SdkJson.encodeToString(RawEvent.serializer(), event))
    }

    private fun buildEvent(
        eventName: String,
        occurredAt: Instant,
        sessionId: String,
        properties: Map<String, JsonPrimitive>,
        traits: Map<String, JsonPrimitive>,
        userId: String?,
    ) = RawEvent(
        // Generated client-side so a retried upload carries the same id and the backend can
        // recognize the duplicate.
        eventId = UUID.randomUUID().toString(),
        eventName = eventName,
        occurredAt = ISO_INSTANT.format(occurredAt),
        sessionId = sessionId,
        anonymousId = identity.anonymousId,
        deviceId = identity.deviceId,
        userId = userId,
        properties = properties,
        traits = traits,
        device = DeviceInfo.region(appContext).let { device.copy(locale = it.locale, timezone = it.timezone) },
    )

    /**
     * Uploads queued events one batch at a time until the queue drains or an upload fails.
     *
     * Events are removed only after the server accepts them, so a crash or a dropped connection
     * costs a retry rather than data.
     */
    private fun drainQueue() {
        while (true) {
            val lines = queue.peek(config.flushBatchSize)
            if (lines.isEmpty()) return

            when (val result = uploader.upload(batchEnvelope(lines))) {
                is UploadResult.Success -> {
                    queue.removeFirst(lines.size)
                    log("Uploaded ${lines.size} events, ${queue.size()} still queued")
                }
                is UploadResult.Retryable -> {
                    log("Upload deferred (${result.reason}); ${queue.size()} events still queued")
                    return
                }
                is UploadResult.Permanent -> {
                    // Dropped deliberately: these events will never be accepted, and keeping
                    // them would block every event behind them forever.
                    Log.w(TAG, "Dropping ${lines.size} events the server rejected: ${result.reason}")
                    queue.removeFirst(lines.size)
                    onBatchRejected?.invoke(lines)
                }
            }
        }
    }

    /**
     * Wraps already-serialized event lines in the batch envelope.
     *
     * The events are spliced in as raw JSON rather than parsed back into [RawEvent] and
     * re-encoded — they came off disk in exactly the shape the server expects, and a
     * round trip would cost CPU on a phone to produce identical bytes.
     */
    private fun batchEnvelope(eventLines: List<String>): String = buildString {
        append("{\"sdk_name\":\"").append(SDK_NAME)
        append("\",\"sdk_version\":\"").append(SDK_VERSION)
        append("\",\"sent_at\":\"").append(ISO_INSTANT.format(Instant.now()))
        append("\",\"events\":[").append(eventLines.joinToString(",")).append("]}")
    }

    private fun log(message: String) {
        if (config.debugLogging) Log.d(TAG, message)
    }

    /** The preferences the push side shares with identity: one store per install. */
    internal fun preferences(): android.content.SharedPreferences = preferences

    /** The application context, for the parts of push only the framework can answer. */
    internal fun context(): Context = appContext

    internal companion object {
        const val TAG = "Analytics"
        // The name from before the package was renamed, kept: a new name would forget every
        // install's ids and turn each existing device into a new one.
        const val PREFERENCES_NAME = "com.analytics.sdk"
        const val QUEUE_FILE_NAME = "analytics-queue.jsonl"

        /** A passive event's session id: the wire's way of saying "no session". */
        const val NO_SESSION = ""

        val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
    }
}
