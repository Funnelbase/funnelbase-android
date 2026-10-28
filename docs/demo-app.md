# The demo Android app

`demo-android` is a trading app that consumes `sdk-android` the way a customer's app would: real
screens, a sign-up, sign-in and sign-out, an instrument page, a settings screen for push. It builds
against the SDK's source in this repository, so a change to the SDK is tried on a device before it
is released.

Module: `demo-android`. Code package: `com.funnelbase.demo.trading`. `applicationId`:
`com.analytics.demo.trading`, older than the package and kept, because a Firebase project is
registered against it.

It has two ports, screen for screen and with the same taxonomy: the iOS demo in
[funnelbase-ios](https://github.com/Funnelbase/funnelbase-ios) and the Flutter demo in
[funnelbase-flutter](https://github.com/Funnelbase/funnelbase-flutter).

## Configuration

The app needs an ingest key from your Funnelbase workspace:

1. **Manage → Apps → New app.** Any name; the id is an opaque `app_…`, shown under the name once
   created.
2. Enable the **Android** platform and generate its key. The plaintext is shown **once**.
3. Put it in `local.properties` at the repository root (gitignored, already used for `sdk.dir`):

```properties
ANALYTICS_APP_ID_DEBUG=app_…
ANALYTICS_API_KEY_DEBUG=ana_and_…
# Optional. Defaults to http://10.0.2.2:8080, the emulator's route to a server on the host.
ANALYTICS_ENDPOINT_DEBUG=https://ingest.example.com
```

Release builds read the same keys with a `_RELEASE` suffix. Environment variables of the same
names work too, which is what a CI build would use. No key is committed to this repository.

**With no key configured the app runs normally and initialises no SDK** —
`TradingDemoApp.onCreate` logs a warning and returns. That is deliberate: a demo that crashes on
a fresh clone is worse than one that quietly collects nothing.

`ANALYTICS_APP_ID` is carried but never sent. The server resolves the app from the key and the
client has no field for it; the app id is here only so the debug footer can show which app a build
points at.

The three settings are read through `providers.fileContents`, not `File.inputStream()`. The
configuration cache is on, and an untracked file read serves a stale key from cache after you
edit it — which fails as a 401 several minutes later, in the SDK, on a device.

## Running it

```bash
./gradlew :demo-android:installDebug
adb shell am start -n com.analytics.demo.trading/com.funnelbase.demo.trading.MainActivity
adb logcat -s Analytics:D TradeDemo:W        # queue and upload activity
```

If your only AVD is a tablet, it boots landscape and ignores `user_rotation`. `adb shell wm size
2400x1080` gives a phone-shaped portrait viewport (the arguments read swapped because the device's
natural orientation is landscape); `adb shell wm size reset` puts it back.

Debug builds allow cleartext to `10.0.2.2`, `localhost` and `127.0.0.1` only, via a
`src/debug` manifest overlay and `res/xml/network_security_config.xml`, for a server running on
your own machine. It is in the debug source set so a release build cannot inherit it. On a
physical device, point `ANALYTICS_ENDPOINT_DEBUG` at the host's LAN address instead — `10.0.2.2`
is an emulator-only alias.

## What it sends

**`$screen_view`, and one custom event.** Screen views carry `screen_name` and nothing else —
they say where the user is. What the user is looking at is a custom event: the instrument page
sends `instrument_viewed` with the ticker in `symbol`. `analytics/Events.kt` is the list of
custom events, as `Screens.kt` is the list of screens.

The reserved events — `$session_start`, `$first_open`, `$identify`, the screen views and the push
events — need **nothing declared**: every app has them in its catalog from the start. The custom
event does. Before running the demo against a fresh app, declare it under Data → Event catalog, or
the server refuses every one with `unknown_event`:

| Event | Properties | Fires |
|---|---|---|
| `instrument_viewed` | `symbol` — string, required | when an instrument's detail page is shown |

Declaring `symbol` is also what makes it appear in the Events page's "Break down by" list, so
"which instruments do people look at" is one selection away.

| Screen | `screen_name` |
|---|---|
| Welcome | `welcome` |
| Sign in | `sign_in` |
| Sign-up intro, pages 1–3 | `signup_intro_1`, `signup_intro_2`, `signup_intro_3` |
| Sign-up username step | `signup_username` |
| The four tabs | `dashboard`, `markets`, `orders`, `news` |
| Instrument detail | `instrument_detail` |
| Notifications, from the dashboard's bell | `push_settings` |
| Notification topics, from the Notifications screen | `notification_settings` |

The names live in `analytics/Screens.kt`, deliberately separate from the navigation routes in
`ui/nav/Destinations.kt`. They look almost identical and they are not the same thing: routes are
addresses the navigation library resolves, screen names are a dimension the dashboard groups by.
Sharing one string means a routing refactor silently renames a chart's series.

The Orders chips (Pending / Cancelled / Filled) filter within a screen and emit nothing. Only
screen and tab changes are screen changes.

The app also sends the three push events the SDK owns — `$push_token`, `$push_received` and
`$push_opened` — and `$topic_preference` from its topics screen ([event-contract.md](event-contract.md)).

**Every `$identify` carries `username` and `plan`, and a pro account's also `plan_renews_on`.**
Declare them as optional properties of the built-in `$identify` (`plan_renews_on` as `date`) so the
dashboard's trait pickers list them.

## Push

The dashboard's bell opens a Notifications screen, which exists to make the parts of push that
usually need a provider visible on an emulator ([push.md](push.md)):

- **The permission.** From Android 13 a notification needs it. The SDK never asks — it has no
  screen to ask from — so the app prompts and the SDK reads the answer, re-registering the token
  because its `status` *is* the permission.
- **The token.** With a Firebase project configured the SDK registers one at startup through
  `:sdk-android-fcm`. Without one, the screen registers a token of your own as kind `opaque`,
  which is enough for the dashboard to target this install through a custom HTTP integration.
- **Three simulate buttons.** Each hands the SDK the same `data` map Funnelbase sends, so
  everything after the transport is real: the parse, the notification, its image and buttons, the
  `$push_received` event, and the tap that reports `$push_opened` with the button's `action_id`.
  The image is fetched from `http://10.0.2.2:9099/media/sample.png`, a server on the host; without
  one the notification still shows, without its picture.
- **Topics.** A "Choose topics" card opens the notification settings screen: the app's topics as
  `Analytics.topics.fetch()` reads them — none is hard-coded, so a topic added in the dashboard
  appears on the next open — with a switch each calling `Analytics.topics.set`. When
  notifications are off, or the SDK's own channel is switched off in the phone's settings, it
  says so and opens the system notification settings.

A tap goes to the SDK's own activity, which records the open and then starts this app's launcher
with the push attached. `push/PushState.kt` turns the campaign's deep link into a route:
`trade://instrument/AAPL` and the four tab links are understood, and anything else leaves the app
where it is, because a campaign is written long after a build ships.

**Firebase, when you have a project.** Put its `google-services.json` in `demo-android/`
(gitignored). The Google services plugin is applied only when that file is there, so the repo
builds without it, and `AnalyticsMessagingService` then registers a real token and receives real
pushes.

## Identity

There is no auth backend — any username and any password sign you in, as intended. The username
is not throwaway though: `identity/UserId.kt` hashes it into the `user_id` every subsequent event
carries.

```
"ada"  →  u_fdee430d40bd57de   (SHA-256 of the trimmed, lowercased name, first 64 bits)
```

Same name, same id, every launch and every device. A random id would make each sign-in a new
person and every retention number meaningless. The sign-up screen shows the derived id live as
you type, so you can look the person up in the dashboard.

The demo follows the event contract ([event-contract.md](event-contract.md)), and `AppState` is
the one place it touches identity:

- Sign-in → `identify`, with `username` and `plan`. There is no billing either: the sign-in
  screen's **Pro plan** switch says which plan the account is on, and a pro sign-in also sends
  `plan_renews_on`, a `LocalDate` 30 days ahead that the SDK sends as `YYYY-MM-DD`
  ([event-contract.md](event-contract.md#dates)). The day is kept with the username, so a relaunch
  sends the same one, moved on by 30 days at a time once it has passed. A sign-up is free.
- Sign-up → the same `identify`, and nothing before it. The `$identify` carries the device's
  anonymous id and the new user id together, and that one event is the link: the welcome and
  intro screens viewed before the account existed become the account's. There is no alias call.
- Sign-in as a **different** account than the last one → the SDK resets first, on its own: a
  fresh anonymous id and a new session, so the second account's visit is never stitched to the
  first. The demo has nothing to do for this beyond calling `identify`.
- Sign-out → `reset`, which uploads the queue before clearing identity, restarts the anonymous
  id, and starts a new session. The welcome screen afterwards is an anonymous stretch of its own.
- Every launch while signed in → `identify` again, from `rememberAppState`, during composition —
  so it is the launch's first event, ahead of the screen views, and the session start and the
  install's `$first_open` carry the account.

`$first_open` is the SDK's, not the app's: once per install, in front of its first event, with
`installed_at_ms` from `PackageInfo.firstInstallTime`. `adb install -r` keeps the install time; an
uninstall resets it and makes a new `device_id` with it.

## Why `autoTrackScreenViews` exists

`AnalyticsConfig.trackApplicationLifecycle` used to control two unrelated things: a screen view
per Activity start, and the flush when the app goes to background. This app is single-Activity,
like every Compose app — with both on, every foreground reports `screen_name = "MainActivity"`
and buries the real names; with both off, the background flush goes too, and that is the last
reliable moment to upload before the process is killed.

So the SDK has `autoTrackScreenViews`, defaulting to `true`. The demo sets it `false` and reports
screen names itself through `analytics/TrackScreenView.kt`.

Deliberately *not* done: a Compose or Navigation integration inside the SDK.
`sdk-android/build.gradle.kts` explains why that module takes no androidx dependency, and that
reasoning does not stop applying at `navigation-compose`.

## How screens are tracked

One composable, called at the top of each screen:

```kotlin
@Composable
fun TrackScreenView(name: String, properties: Map<String, Any?> = emptyMap()) {
    LaunchedEffect(name) { Analytics.screen(name, properties) }
}
```

`LaunchedEffect` keyed on the name fires when a screen enters composition and again when it
re-enters, so arriving, going back, and switching tabs each produce exactly one event while a
recomposition produces none.

A single `NavController.OnDestinationChangedListener` would have been the obvious alternative and
was not taken: the sign-up intro is one destination showing three screens, so a destination
listener cannot see two of them, and running both mechanisms double-counts everything else.
`SignUpFlow` therefore tracks its pager itself — from `settledPage`, not `currentPage`, because
Compose composes the neighbouring page before it is on screen and `currentPage` moves mid-drag.
Either would report screens nobody looked at.

## The data

Fifty S&P 500 constituents, priced randomly **at every launch**. Prices are drawn from a
per-ticker band so the list stays plausible (Berkshire never trades below Ford), and the day's
move is bounded to ±5%.

Everything is generated once into `MarketData.current` and shared, rather than per screen: the
portfolio, the orders and the news feed all reference instruments, and regenerating per screen
would show three different prices for AAPL depending on which tab you were on. Derived figures
are computed, never stored — `Order.cost` is `quantity × price`, and the portfolio's day change
is summed from its holdings, so nothing on screen can contradict the arithmetic a reader does.

Money is formatted `en-US`/USD throughout (`ui/format/Money.kt`), fixed rather than device-locale:
the instruments are dollar-denominated, and formatting them with the phone's locale would render
a dollar amount with European grouping.

Colour never carries meaning alone — every green/red price move is paired with a ▲/▼ glyph.

## Tests

`./gradlew :demo-android:testDebugUnitTest` — JVM only, no emulator.

`MarketDataTest` asserts the *shape* of data that is random by design, and it exists because two
bugs got through review and only showed up on a device:

- An `abs()` in `Instruments.round2`, added to guard prices, also stripped the sign off every
  change — the entire market rendered green. `changes go both ways` is that test. Across fifty
  instruments a fair coin gives all-one-direction odds of about 1 in 5.6×10¹⁴, so a failure is a
  lost sign, not bad luck.
- News `minutesAgo` was `(index + 1) * random(9, 70)`, which is not monotonic: article 1 could
  draw 69 and article 2 draw 18, so the feed showed "1h ago" above "12m ago" while claiming to be
  most-recent-first. It accumulates now.

`UserIdTest` pins the derived id to a literal rather than comparing two live calls — comparing
calls still passes if the algorithm changes, and changing it invalidates every `user_id` already
sent. `MoneyTest` sets the default locale to `tr-TR` and asserts the output is still
`$1,234.56`, which is the regression that reintroducing a device-locale formatter would cause.

## Known gaps

- **No ViewModels, no DI, no repository interfaces.** State is a `remember`ed `AppState`. There is
  no async work and nothing outlives the Activity except two `SharedPreferences` keys.
- **Buy and Sell on the detail screen are disabled.** There is no order placement, and a button
  that silently does nothing is worse than one that says it is unavailable.
- **No instrumentation test for the screen-view taxonomy.** The unit tests cover the pure logic,
  but nothing fails the build when a screen loses its `TrackScreenView` call. A Compose UI test
  walking the graph and asserting the emitted names against `Screens` is the missing piece.
- **Prices are static within a launch.** No simulated ticking, so the portfolio figure never
  moves while you watch it.
- **androidx is pinned one generation back.** Compose 1.12 / core 1.19 / navigation 2.10 require
  `compileSdk 37`, and AGP is capped at 36 — see the comment in `gradle/libs.versions.toml`.
