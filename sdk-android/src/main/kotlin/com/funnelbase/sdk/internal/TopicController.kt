package com.funnelbase.sdk.internal

import android.content.SharedPreferences
import com.funnelbase.sdk.ReservedEvents
import com.funnelbase.sdk.SdkJson
import com.funnelbase.sdk.TopicChoice
import com.funnelbase.sdk.TopicsException
import com.funnelbase.sdk.TopicsResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executor

/** What the topic side needs from the event pipeline, so it can be exercised without a device. */
internal interface TopicPipeline {
    /**
     * Sends a passive event, then calls [enqueued] on the worker with its event id, once the event
     * is in the on-disk queue.
     */
    fun trackPassive(eventName: String, properties: Map<String, Any?>, enqueued: (eventId: String) -> Unit)

    /** Uploads what is queued now rather than at the next interval. */
    fun flush()

    /**
     * Runs [block] on the worker with the identity as it is *now* — after any sign-in, account
     * switch or sign-out already queued ahead of it.
     */
    fun withIdentity(block: (anonymousId: String, userId: String?) -> Unit)
}

/** Reads the person's topics from the server. Throws a [TopicsException] on failure. */
internal fun interface TopicSource {
    fun fetch(anonymousId: String, userId: String?): List<TopicChoice>
}

/**
 * Notification topics: send a choice, read the list back with this install's pending choices laid
 * over it.
 *
 * Callbacks are handed to [deliver] (the main thread, in the app) and the network runs on
 * [network], never on the event worker, so a slow read never sits in front of an upload.
 */
internal class TopicController(
    private val pipeline: TopicPipeline,
    private val store: TopicChoiceStore,
    private val source: TopicSource,
    private val network: Executor,
    private val deliver: (Runnable) -> Unit,
) {

    /**
     * Sends the choice and flushes at once: a person who has just flipped a switch expects it to
     * hold, and a choice has no session to wait for the end of.
     */
    fun set(key: String, subscribed: Boolean) {
        pipeline.trackPassive(
            ReservedEvents.TOPIC_PREFERENCE,
            mapOf(
                ReservedEvents.TOPIC_PREFERENCE_TOPIC to key,
                ReservedEvents.TOPIC_PREFERENCE_SUBSCRIBED to subscribed,
            ),
        ) { eventId -> store.remember(key, PendingTopicChoice(subscribed, eventId)) }
        pipeline.flush()
    }

    fun fetch(callback: (Result<List<TopicChoice>>) -> Unit) {
        pipeline.withIdentity { anonymousId, userId ->
            network.execute {
                val result = runCatching { store.overlay(source.fetch(anonymousId, userId)) }
                    .recoverCatching { throw it as? TopicsException ?: TopicsException.Network(it) }
                deliver(Runnable { callback(result) })
            }
        }
    }

    /** The server refused a batch for good: a choice in it will never arrive, so it stops pending. */
    fun onBatchRejected(eventLines: List<String>) = store.releaseRejected(eventLines)

    /** A sign-out or account switch: the choices pending so far were the last person's. */
    fun onIdentityReset() = store.clear()
}

/** A choice this install sent and the server has not yet been seen to hold; [eventId] names its `$topic_preference`. */
@Serializable
internal data class PendingTopicChoice(
    val subscribed: Boolean,
    @SerialName("event_id") val eventId: String,
)

/**
 * This install's pending choice per topic: sent, and not yet seen held by the server.
 *
 * A pending choice overrides the server's answer, as the person's own (`chosen`), until one of:
 * - a fetch shows the server holding it — `chosen` and the same `subscribed`; a default that
 *   happens to match does not count, since the choice may still be in flight behind it;
 * - the upload carrying its event is refused for good (a 4xx) and dropped from the queue;
 * - a reset — a sign-out or a different account — since the next person's choices are not these;
 * - the server no longer lists the topic.
 *
 * Not on upload success: ingestion queues the event and stores it moments later, so a
 * fetch right after a successful upload can still answer from before it. And no time limit: a
 * choice made offline waits in the queue as long as the phone does.
 *
 * On disk, because the queue it waits in is too, across process deaths. Synchronized: the worker
 * writes it and a fetch reads it from another thread. Mirrors the iOS SDK's `TopicPreferenceStore`.
 */
internal class TopicChoiceStore(private val preferences: SharedPreferences) {

    @Synchronized
    fun remember(key: String, choice: PendingTopicChoice) = write(read() + (key to choice))

    /** Releases every pending choice whose event is among [eventLines], the queue's own serialized events. */
    @Synchronized
    fun releaseRejected(eventLines: List<String>) {
        val pending = read()
        if (pending.isEmpty()) return
        val kept = pending.filterValues { choice -> eventLines.none { "\"event_id\":\"${choice.eventId}\"" in it } }
        if (kept.size != pending.size) write(kept)
    }

    /** The server's answer with the pending choices laid over it, releasing those it has settled. */
    @Synchronized
    fun overlay(topics: List<TopicChoice>): List<TopicChoice> {
        val pending = read()
        if (pending.isEmpty()) return topics
        val (overlaid, kept) = overlay(topics, pending)
        if (kept.size != pending.size) write(kept)
        return overlaid
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_PENDING).commit()
    }

    @Synchronized
    fun pending(): Map<String, PendingTopicChoice> = read()

    private fun read(): Map<String, PendingTopicChoice> {
        val encoded = preferences.getString(KEY_PENDING, null) ?: return emptyMap()
        return runCatching { SdkJson.decodeFromString(SERIALIZER, encoded) }.getOrDefault(emptyMap())
    }

    private fun write(pending: Map<String, PendingTopicChoice>) {
        if (pending.isEmpty()) {
            preferences.edit().remove(KEY_PENDING).commit()
        } else {
            preferences.edit().putString(KEY_PENDING, SdkJson.encodeToString(SERIALIZER, pending)).commit()
        }
    }

    companion object {
        private const val KEY_PENDING = "topic_choices_pending"
        private val SERIALIZER = MapSerializer(String.serializer(), PendingTopicChoice.serializer())

        /**
         * The pure rule: the topics as the person should see them, and the pending choices still
         * pending afterwards.
         */
        fun overlay(
            topics: List<TopicChoice>,
            pending: Map<String, PendingTopicChoice>,
        ): Pair<List<TopicChoice>, Map<String, PendingTopicChoice>> {
            val listed = topics.associateBy { it.key }
            val kept = pending.filter { (key, choice) ->
                val topic = listed[key] ?: return@filter false
                !(topic.chosen && topic.subscribed == choice.subscribed)
            }
            val overlaid = topics.map { topic ->
                val choice = kept[topic.key] ?: return@map topic
                topic.copy(subscribed = choice.subscribed, chosen = true)
            }
            return overlaid to kept
        }
    }
}

/**
 * `GET /v1/topics` over `HttpURLConnection`, with the ingest key the uploader sends. No gzip: the
 * request has no body and the answer is a few topics long.
 */
internal class HttpTopicSource(
    private val url: String,
    private val apiKey: String,
) : TopicSource {

    override fun fetch(anonymousId: String, userId: String?): List<TopicChoice> {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(requestUrl(url, anonymousId, userId)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Api-Key", apiKey)
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                throw TopicsException.Http(status, error.take(200))
            }
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            return parse(body)
        } catch (known: TopicsException) {
            throw known
        } catch (transport: IOException) {
            throw TopicsException.Network(transport)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 20_000

        /** The read route's address; `user_id` is left out entirely while signed out. */
        fun requestUrl(base: String, anonymousId: String, userId: String?): String = buildString {
            append(base).append("?anonymous_id=").append(encode(anonymousId))
            if (!userId.isNullOrEmpty()) append("&user_id=").append(encode(userId))
        }

        fun parse(body: String): List<TopicChoice> =
            try {
                SdkJson.decodeFromString(TopicsResponse.serializer(), body).topics
            } catch (unreadable: Exception) {
                throw TopicsException.Malformed(unreadable)
            }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
