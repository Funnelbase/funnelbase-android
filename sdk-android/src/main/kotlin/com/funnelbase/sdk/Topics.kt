package com.funnelbase.sdk

import android.util.Log
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * One of the app's notification topics, and where this person stands on it.
 *
 * @property key what the app and the campaigns name the topic by (`promotions`); never changes.
 * @property name what to show the person, as the dashboard names it.
 * @property description a line under the name, or null when the dashboard gave none.
 * @property subscribed whether the person gets messages sent under this topic.
 * @property chosen whether [subscribed] is the person's own choice rather than the topic's default.
 */
@Serializable
data class TopicChoice(
    val key: String,
    val name: String,
    val description: String? = null,
    val subscribed: Boolean,
    val chosen: Boolean,
)

/** Why the topics could not be read. The same four cases as the iOS SDK's `TopicsError`. */
sealed class TopicsException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The server answered, and not with a 2xx: `503 topics_unavailable`, a bad key's 401. */
    class Http(val status: Int, val body: String) : TopicsException("HTTP $status: $body")

    /** No answer arrived: offline, a timeout, a refused connection. */
    class Network(cause: Throwable) : TopicsException(cause.message ?: cause.javaClass.simpleName, cause)

    /** An answer arrived that is not the topics response. */
    class Malformed(cause: Throwable) : TopicsException("Unreadable topics response: ${cause.message}", cause)

    /** [Analytics.initialize] has not been called. */
    class NotInitialized : TopicsException("Analytics.initialize has not been called")
}

/**
 * Notification topics: the kinds of message an app sends ("Promotions", "Price alerts"), each of
 * which a person may switch off or on. A campaign sent under a topic skips everyone who switched
 * it off. Reached as `Analytics.topics`.
 *
 * The list is the dashboard's, never the app's: an app shows what [fetch] returns rather than a
 * list of its own, so a topic added in the dashboard appears without a release. A choice belongs
 * to the person, not the install — made before signing in, it carries over to the account, and
 * made on one phone, it holds on the others.
 */
object AnalyticsTopics {

    /**
     * Switches the topic [key] on or off for the current person.
     *
     * Sends `$topic_preference` passively — outside any session, like a push token: changing a
     * setting is not use of the product — and flushes the queue at once. Queued on disk and
     * retried like every event, so a choice made offline is delivered later; until the server is
     * seen to hold it, [fetch] shows it (the rule is there). A key the app does not have is
     * refused by the server (`unknown_topic`) and logged, not thrown.
     */
    @JvmStatic
    fun set(key: String, subscribed: Boolean) {
        if (!TOPIC_KEY.matches(key)) {
            // The server refuses these anyway; failing here points at the actual call site.
            Log.w(TAG, "'$key' is not a topic key (lowercase letters, digits and underscores) — ignoring")
            return
        }
        val controller = Analytics.topicController
        if (controller == null) {
            Log.w(TAG, "Analytics.topics.set called before initialize() — ignoring")
            return
        }
        controller.set(key, subscribed)
    }

    /**
     * Reads the app's topics and the person's choice on each, on a background thread, and hands
     * the answer to [callback] on the main thread.
     *
     * A choice [set] on this install stays *pending* until the server is seen to hold it, and
     * while pending it overrides the server's answer, with `chosen = true`. So a switch the
     * person just flipped never flips back on the next fetch, however long the event waits in
     * the queue or on its way from ingestion into storage. A pending choice is released when:
     * - a fetch shows the server holding it — `chosen` and the same `subscribed` (a default that
     *   happens to match does not count);
     * - the upload carrying it is refused for good (a 4xx), so it will never arrive;
     * - the person signs out or a different account signs in;
     * - the server no longer lists the topic.
     *
     * Not on upload success alone, and never by time.
     *
     * Fails with a [TopicsException]: [TopicsException.Http] for a non-2xx answer (`503
     * topics_unavailable` among them), [TopicsException.Network] when none arrived,
     * [TopicsException.Malformed] for an unreadable one, and [TopicsException.NotInitialized]
     * before [Analytics.initialize].
     */
    @JvmStatic
    fun fetch(callback: (Result<List<TopicChoice>>) -> Unit) {
        val controller = Analytics.topicController
        if (controller == null) {
            Log.w(TAG, "Analytics.topics.fetch called before initialize()")
            callback(Result.failure(TopicsException.NotInitialized()))
            return
        }
        controller.fetch(callback)
    }

    /** [fetch], for a coroutine. Resumes on the main thread. */
    suspend fun fetch(): Result<List<TopicChoice>> = suspendCoroutine { continuation ->
        fetch { result -> continuation.resume(result) }
    }

    /** The server's rule for a key, so a typo is caught where it was made. */
    private val TOPIC_KEY = Regex("^[a-z][a-z0-9_]{0,63}$")

    private const val TAG = "Analytics"
}
