# Push on Android

The SDK owns three things about push: registering this install's token, rendering a message
Funnelbase sent and reporting that it arrived, and reporting a tap. It does not depend on Firebase.
Whoever holds the token and the payload hands them over: `sdk-android-fcm` does it in one service
for an app on Firebase, and an app on another gateway does it itself.

## With Firebase

Add the glue module and initialise the SDK in `Application.onCreate`. That is all: its
`AnalyticsMessagingService` is merged into the app's manifest, registers the Firebase token, and
hands every data message to the SDK.

```kotlin
dependencies {
    implementation("com.funnel-base:funnelbase-android-fcm:0.4.0") // brings funnelbase-android with it
}
```

Initialise in `Application.onCreate`, not in an Activity: a push starts the process through
`Application` before the messaging service, and one arriving before `Analytics.initialize` is
neither rendered nor recorded.

In the dashboard, an integration of kind Firebase is bound to the app's Android platform. Apple
devices on Firebase are served by the same integration.

## With another gateway

Hand the token over with kind `opaque` (or `apns` / `fcm` when that is what it is), and every
message's `data` map as it arrives:

```kotlin
Analytics.push.setToken(token, TokenKinds.OPAQUE)

// wherever your transport delivers a message
val wasOurs = Analytics.push.handleMessage(data)   // false: another sender's, left alone
```

`setToken` is safe on every launch: `$push_token` is sent only when something it carries differs
from what was last sent.

## The token

The token is sent as the passive `$push_token` ([event-contract.md](event-contract.md#passive-events))
on issue and refresh, at every `identify` and `reset` (so the install is targeted as whoever is
signed in now), when the notification permission changes, and when the phone's language or time
zone changes, since a message is written in the person's language and sent by their clock. Its
`status` is `active`, or `disabled` while the OS permission is off or the person has switched off
the SDK's own notification channel. Like every event it queues offline and retries until accepted.

`Analytics.push.registeredToken()` returns what was last registered, for a settings or debug
screen. `Analytics.push.notificationsEnabled()` says whether a push can reach the person now.

## Notification permission

From Android 13 a notification needs `POST_NOTIFICATIONS`. The SDK neither declares nor requests
it: declaring it would put it in every host app by manifest merge, including apps that send no
push, and asking for it needs an Activity the SDK does not have. The app declares it and prompts,
then calls `Analytics.push.refreshToken()`; the SDK also checks at startup, on a configuration
change and, with lifecycle tracking on, on returning to the foreground.

## The payload

Funnelbase sends a Firebase **data message** — everything in `data`, no `notification` block — so
the SDK draws the notification whether the app is in the foreground or the background, and can
report that it arrived. Every value is a string, because that is all Firebase carries:

| Key | Meaning |
|---|---|
| `delivery_id`, `run_id` | Required. A message without both is not Funnelbase's, and `handleMessage` returns false. |
| `title`, `body` | The text. The body is shown again expanded, so a long one is readable. |
| `image_url` | An `http(s)` image for a rich notification, fetched off the caller's thread, skipped past `AnalyticsConfig.pushMaxImageBytes`. |
| `deep_link` | Where a tap should go; handed to the app (below). |
| `collapse_key` | A message with the same key replaces the last one; without it every delivery is its own notification. |
| `android_channel_id` | The channel to post on. Absent, or a channel the app has not created: `AnalyticsConfig.pushChannelId`, which the SDK creates. |
| `buttons` | JSON text: up to three `{"id", "title", "deep_link"?}`. A broken list costs only the buttons. |
| anything else | The campaign's own values, in `PushPayload.data`. |

## A tap

A tap — on the notification or one of its buttons — goes to the SDK's own `PushTapActivity`,
which reports `$push_opened` (with `action_id` for a button) and then hands the push to the app.
Navigation is the app's: only the app knows its own screens.

```kotlin
Analytics.push.setTapHandler { payload, actionId ->
    router.open(payload.buttons.firstOrNull { it.id == actionId }?.deepLink ?: payload.deepLink)
    true   // routed; false lets the SDK open the launcher activity with the push attached
}
```

Without a handler, or when it returns false, the SDK starts the app's launcher activity with the
push on the intent: `Analytics.push.payloadFrom(intent)` and `Analytics.push.actionIdFrom(intent)`
read it back there. The open has already been reported by then.

## Trying push without a provider

The demo app's Notifications screen hands the SDK the same `data` map Funnelbase sends, from three
buttons — basic, rich with an image and two buttons, and collapsing — so the parse, the
notification, the receipt and the tap can all be seen on an emulator with no Firebase project
([demo-app.md](demo-app.md#push)). It can also register a token of your own as kind `opaque`, which
a custom HTTP integration in the dashboard can target.
