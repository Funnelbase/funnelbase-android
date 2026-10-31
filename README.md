# Funnelbase Android SDK

Event tracking, sessions, identity and push for Android apps, sending to
[Funnelbase](https://funnel-base.com).

- Custom events with typed properties, checked against your app's event catalog.
- Sessions, screen views and installs (`$first_open`) tracked for you.
- Identity: anonymous until sign-in, one person across the sign-in, a clean reset at sign-out.
- An offline queue on disk, uploaded in batches, gzipped, retried until accepted.
- Push: rendering with images, buttons and collapsing, delivery and open reporting, notification
  topics. Firebase optional.
- One dependency, `kotlinx-serialization-json`. No OkHttp, no androidx.

Android 8.0 (API 26) and up.

## Install

From Maven Central:

```kotlin
dependencies {
    implementation("com.funnel-base:funnelbase-android:0.4.0")

    // Optional: push through Firebase Cloud Messaging, with no push code of your own.
    implementation("com.funnel-base:funnelbase-android-fcm:0.4.0")
}
```

## Quick start

Create the app in your Funnelbase workspace (Manage → Apps), enable the Android platform and copy
its ingest key. The endpoint is shown on Manage → Platforms; a self-hosted install uses its own address. Then initialise once, in `Application.onCreate`:

```kotlin
import com.funnelbase.sdk.Analytics
import com.funnelbase.sdk.AnalyticsConfig

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Analytics.initialize(
            this,
            AnalyticsConfig(apiKey = BuildConfig.FUNNELBASE_KEY, endpoint = "https://ingest.funnel-base.com"),
        )
    }
}
```

Track what people do. An event must be declared in the app's event catalog (Data → Event catalog)
before it is accepted:

```kotlin
Analytics.track("checkout_completed", mapOf("cart_total" to 42.50, "currency" to "EUR"))
Analytics.screen("cart")                          // when autoTrackScreenViews is off
```

Say who they are at sign-in and sign-up, and on each launch while signed in; reset at sign-out:

```kotlin
Analytics.identify(userId = "u_123", traits = mapOf("plan" to "pro", "renews_on" to LocalDate.of(2026, 11, 3)))
Analytics.reset()
```

Every call is safe from any thread and before `initialize`: an uninitialised SDK logs and drops the
call rather than crashing the host app.

## Configuration

`AnalyticsConfig` takes the key and endpoint, and optionally:

| Setting | Default | |
|---|---|---|
| `flushIntervalSeconds` | 30 | How often a partial batch is uploaded anyway. |
| `flushBatchSize` | 20 | Upload as soon as this many events are queued (at most 500). |
| `maxQueuedEvents` | 1,000 | Past it, the oldest queued events are dropped. |
| `sessionTimeoutMinutes` | 30 | Inactivity before a new session. |
| `trackApplicationLifecycle` | true | Uploads the queue when the app goes to the background. Leave it on. |
| `autoTrackScreenViews` | true | A screen view per Activity start. Turn it off in a single-Activity (Compose) app and call `Analytics.screen` per destination. |
| `debugLogging` | false | Queue and upload activity in logcat, under the tag `Analytics`. |
| `pushChannelId`, `pushChannelName` | `analytics-push`, "Notifications" | The channel a push lands on when the message names none; created by the SDK. |
| `pushSmallIconResId` | 0 (the app icon) | The notification's small icon. |
| `pushMaxImageBytes` | 1,000,000 | Past this, a rich notification is shown without its image. |

## Push

With `funnelbase-android-fcm` there is nothing else to write: its messaging service registers the
token and renders every message. Declare `POST_NOTIFICATIONS`, prompt for it, and call
`Analytics.push.refreshToken()` afterwards. Route taps with `Analytics.push.setTapHandler`. On
another gateway, hand the SDK the token (`Analytics.push.setToken(token, TokenKinds.OPAQUE)`) and
each message's `data` map (`Analytics.push.handleMessage(data)`). [docs/push.md](docs/push.md) has
the details, the payload included.

Notification topics: `Analytics.topics.fetch()` lists the app's topics with the person's choice on
each, and `Analytics.topics.set(key, subscribed)` records a change.

## Documentation

- [docs/event-contract.md](docs/event-contract.md) — what goes on the wire, and why: the three ids,
  the reserved events, dates, passive events.
- [docs/push.md](docs/push.md) — the token, the payload, rendering and taps.
- [docs/demo-app.md](docs/demo-app.md) — the demo app.

## The demo app

`demo-android` is a trading app built on the SDK's source here: screens, sign-up, sign-in, push
and topics. Put an ingest key in `local.properties` and install it:

```properties
ANALYTICS_API_KEY_DEBUG=ana_and_…
ANALYTICS_ENDPOINT_DEBUG=https://ingest.funnel-base.com
```

```bash
./gradlew :demo-android:installDebug
```

See [docs/demo-app.md](docs/demo-app.md).

## Building

```bash
./gradlew :sdk-android:testDebugUnitTest :demo-android:assembleDebug
./gradlew publishToMavenLocal      # com.funnel-base:funnelbase-android(-fcm) into ~/.m2
```

JDK 17 or newer and an Android SDK (`sdk.dir` in `local.properties`, or `ANDROID_HOME`).
Releasing: [RELEASING.md](RELEASING.md).

## Other platforms

- iOS: [funnelbase-ios](https://github.com/Funnelbase/funnelbase-ios)
- Flutter: [funnelbase-flutter](https://github.com/Funnelbase/funnelbase-flutter)

## License

Apache 2.0 — see [LICENSE](LICENSE).
