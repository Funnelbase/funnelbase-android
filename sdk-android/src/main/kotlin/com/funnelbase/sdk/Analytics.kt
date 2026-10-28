package com.funnelbase.sdk

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.funnelbase.sdk.internal.ActivityLifecycleTracker
import com.funnelbase.sdk.internal.AnalyticsClient
import com.funnelbase.sdk.internal.AndroidNotificationPresenter
import com.funnelbase.sdk.internal.DeviceInfo
import com.funnelbase.sdk.internal.HttpTopicSource
import com.funnelbase.sdk.internal.NotificationPermission
import com.funnelbase.sdk.internal.PushController
import com.funnelbase.sdk.internal.PushTokenStore
import com.funnelbase.sdk.internal.TopicChoiceStore
import com.funnelbase.sdk.internal.TopicController
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The SDK's entire public surface.
 *
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Analytics.initialize(
 *             this,
 *             AnalyticsConfig(apiKey = BuildConfig.ANALYTICS_KEY, endpoint = "https://ingest.example.com"),
 *         )
 *     }
 * }
 *
 * Analytics.track("checkout_completed", mapOf("cart_total" to 42.50, "currency" to "EUR"))
 * ```
 *
 * Every method is safe to call from any thread and safe to call before [initialize] — an
 * uninitialized SDK logs and drops the call rather than crashing the host app. Analytics is
 * never worth taking someone's app down for.
 *
 * What the SDK sends, and when, is docs/event-contract.md: every event carries a `device_id`
 * (the install), an `anonymous_id` (one stretch of use between resets) and, while signed in, a
 * `user_id`. `$session_start` goes in front of every session's first event, and `$first_open`
 * once per install, in front of its first event, with the install time.
 */
object Analytics {

    @Volatile
    private var client: AnalyticsClient? = null

    /**
     * The push side, built with the client. Internal rather than private because a notification
     * tap arrives at [PushTapActivity], which is not this object.
     */
    @Volatile
    internal var pushController: PushController? = null
        private set

    /**
     * Push: registering this install's token, rendering a message the platform sent, and
     * reporting the tap. See [AnalyticsPush].
     */
    @JvmStatic
    val push: AnalyticsPush get() = AnalyticsPush

    /** The topic side, built with the client; reached through [topics]. */
    @Volatile
    internal var topicController: TopicController? = null
        private set

    /**
     * Notification topics: switching one on or off for the current person, and reading them back.
     * See [AnalyticsTopics].
     */
    @JvmStatic
    val topics: AnalyticsTopics get() = AnalyticsTopics

    /**
     * Wires up the SDK. Call once, from `Application.onCreate`.
     *
     * Passing an [Application] additionally enables background flushing, and — unless
     * [AnalyticsConfig.autoTrackScreenViews] is off — a screen view per Activity start, when
     * [AnalyticsConfig.trackApplicationLifecycle] is on.
     */
    @JvmStatic
    @Synchronized
    fun initialize(context: Context, config: AnalyticsConfig) {
        if (client != null) {
            Log.w(TAG, "Analytics.initialize called more than once — ignoring the later call")
            return
        }

        val instance = AnalyticsClient(context, config)
        client = instance
        pushController = PushController(
            recorder = instance,
            tokens = PushTokenStore(instance.preferences()),
            presenter = AndroidNotificationPresenter(instance.context(), config, pushImages),
            config = config,
            notificationsEnabled = { NotificationPermission.read(instance.context(), config.pushChannelId) },
            region = { DeviceInfo.region(instance.context()) },
        )
        val topics = TopicController(
            pipeline = instance,
            store = TopicChoiceStore(instance.preferences()),
            source = HttpTopicSource(config.topicsUrl, config.apiKey),
            network = topicReads,
            deliver = { mainThread.post(it) },
        )
        topicController = topics
        instance.onBatchRejected = topics::onBatchRejected
        instance.onFreshStart = topics::onIdentityReset

        // The registry holds the phone's language and time zone, and a run picks a template's copy
        // by them — so a change to either is registered at the next launch without the app asking,
        // and while the app runs, on the configuration change that carries a new language.
        pushController?.refreshToken()
        instance.context().registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                pushController?.refreshToken()
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })

        if (config.trackApplicationLifecycle) {
            val application = context.applicationContext as? Application
            if (application == null) {
                Log.w(TAG, "Automatic lifecycle tracking needs an Application context — disabled")
            } else {
                application.registerActivityLifecycleCallbacks(
                    ActivityLifecycleTracker(
                        instance,
                        trackScreenViews = config.autoTrackScreenViews,
                        // The notification switch and the channel are changed in the system's
                        // screens, with the app away: back in front is when a change is seen.
                        onForeground = { pushController?.refreshToken() },
                    ),
                )
            }
        }
    }

    /**
     * Records a custom event.
     *
     * @param properties values may be [String], [Number], [Boolean], a date, or null (dropped).
     *   Types are preserved end to end, so send `42` rather than `"42"` if you ever want to
     *   filter on a range. A date is sent as ISO-8601 text, what a property declared `date`
     *   accepts: a `java.time.LocalDate` as a calendar day (`2026-11-03`), a `java.time.Instant`
     *   or `java.util.Date` as an exact moment in UTC (`2026-11-03T09:00:00Z`).
     */
    @JvmStatic
    @JvmOverloads
    fun track(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        if (eventName.startsWith('$')) {
            // The server rejects these anyway; failing here points at the actual call site.
            Log.w(TAG, "Event names starting with '$' are reserved — ignoring '$eventName'")
            return
        }
        withClient("track") { it.track(eventName, properties) }
    }

    /**
     * Records a screen view.
     *
     * Automatic tracking reports Activity names. Call this yourself for Compose destinations
     * and Fragments, which an Activity-level callback cannot see.
     */
    @JvmStatic
    @JvmOverloads
    fun screen(screenName: String, properties: Map<String, Any?> = emptyMap()) {
        withClient("screen") {
            it.track(ReservedEvents.SCREEN_VIEW, properties + (ReservedEvents.SCREEN_VIEW_SCREEN_NAME to screenName))
        }
    }

    /**
     * Associates subsequent events with a known user, and updates their stored traits.
     *
     * Call at sign-in, at sign-up, and on app start for an already-signed-in user — before the
     * first screen view of that launch, so the session's start carries the account. The
     * `$identify` this sends carries the device's anonymous id and the user id together, and
     * that is what links the anonymous activity before it — the onboarding someone did before
     * signing up — to the account. There is no alias call to make, at sign-up or anywhere.
     *
     * Identifying a *different* user than the one currently signed in is an account switch,
     * and is treated as a [reset] first: the anonymous id and the session restart, so nothing
     * the new account does is attributed to the old one, and a session never spans two people.
     *
     * @param traits the same value types as [track]'s properties, dates included — a
     *   `LocalDate` such as a plan's renewal day goes out as `2026-11-03`, what a trait
     *   declared `date` on `$identify` accepts.
     */
    @JvmStatic
    @JvmOverloads
    fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
        withClient("identify") {
            it.identify(userId, traits)
            // The registry keys a token by install, but a run's audience is people: the same
            // device is now a different person, so the registration is sent again to be joined
            // to them.
            pushController?.onIdentityChanged()
        }
    }

    /** Uploads queued events now instead of waiting for the next interval. */
    @JvmStatic
    fun flush() {
        withClient("flush") { it.flush() }
    }

    /**
     * Clears the stored identity, starts a fresh anonymous id and a new session. Call at
     * sign-out — without it, the next person to use the device inherits the previous user's
     * identity. The device id is untouched: it names the install, not the person.
     */
    @JvmStatic
    fun reset() {
        withClient("reset") {
            it.reset()
            pushController?.onIdentityChanged()
        }
    }

    /** One thread for reading topics, so a slow read never sits in front of an event upload. */
    private val topicReads: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "analytics-topics").apply { isDaemon = true }
    }

    private val mainThread: Handler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * One thread for the images a rich notification needs. Its own rather than the client's, so a
     * slow download never sits in front of an event upload.
     */
    private val pushImages: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "analytics-push-images").apply { isDaemon = true }
    }

    private inline fun withClient(operation: String, block: (AnalyticsClient) -> Unit) {
        val instance = client
        if (instance == null) {
            Log.w(TAG, "Analytics.$operation called before initialize() — ignoring")
            return
        }
        block(instance)
    }

    private const val TAG = "Analytics"
}
