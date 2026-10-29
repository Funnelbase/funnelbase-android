# funnelbase-android

The Funnelbase Android SDK: `sdk-android` (the SDK), `sdk-android-fcm` (optional Firebase Cloud
Messaging glue, one service long) and `demo-android` (a Compose trading app that proves both on a
device). Published to Maven Central as `com.funnel-base:funnelbase-android` and
`com.funnel-base:funnelbase-android-fcm`. Public repository, Apache 2.0.

Read before changing an area: [docs/event-contract.md](docs/event-contract.md) (what goes on the
wire), [docs/push.md](docs/push.md) (tokens, payload, rendering, taps),
[docs/demo-app.md](docs/demo-app.md) (the demo and its screen-view taxonomy),
[RELEASING.md](RELEASING.md).

## Rules

- **The wire and the device are a contract.** Never change `sdk_name`, the JSON field names, the
  reserved `$` events, the SharedPreferences file (`PREFERENCES_NAME = "com.analytics.sdk"`), the
  push intent extras or the notification channel id as a side effect: a renamed stored key forgets
  every install's ids. A deliberate wire change ships in the backend first, which must keep
  accepting what released SDKs send, then here and in funnelbase-ios together, then in
  funnelbase-flutter if a native method changed. The iOS SDK mirrors this one method for method.
- **Public names stay.** `Analytics`, `AnalyticsConfig`, `AnalyticsMessagingService`… are API.
  Renaming one is a breaking release.
- **No dependency budget to spend.** `sdk-android` takes `kotlinx-serialization-json` and nothing
  else (no OkHttp, no androidx): every transitive dependency is a conflict handed to a customer's
  app. `sdk-android/build.gradle.kts` explains it.
- **Consumers on Kotlin 2.2.** The SDK is compiled with `languageVersion` / `apiVersion` 2.2 and
  stdlib 2.2.0 (`kotlinConsumer*` in `gradle/libs.versions.toml`) so an app on AGP 9.1's built-in
  Kotlin can read it. Raise those only on purpose.
- **Versions are capped by the IDE.** AGP, Compose, androidx and Firebase are pinned below the
  versions that need `compileSdk 37`; the catalog's comments say why. Check the set together.
- **No secrets in the repo.** Ingest keys and app ids come from `local.properties`
  (`ANALYTICS_API_KEY_DEBUG`, `ANALYTICS_APP_ID_DEBUG`, …); `google-services.json` and
  `local.properties` are gitignored. Never commit either.
- **A new `Analytics.track()` call in the demo** goes into `analytics/Events.kt` and
  docs/demo-app.md.

## Checks

```bash
./gradlew --continue :sdk-android:testDebugUnitTest :sdk-android-fcm:testDebugUnitTest \
  :sdk-android:assembleRelease :sdk-android-fcm:assembleRelease \
  :demo-android:testDebugUnitTest :demo-android:assembleDebug
./gradlew publishToMavenLocal   # unsigned, into ~/.m2, for funnelbase-flutter to build against
```

`local.properties` needs `sdk.dir` (CI uses `ANDROID_HOME`).

## Working here

- Every change on its own branch (`feature/`, `fix/`, `change-request/`, `refactor/`, `chore/`,
  `docs/` + a slug) cut from `origin/main`, through a pull request. Nothing is committed until
  the maintainer approves opening the PR, and nothing merged until they approve that separately.
- Ask before pushing anything to GitHub: this repository is public.
- **A release is irreversible.** A version on Maven Central can never be changed or deleted. The
  `v<version>` tag uploads to the Central Portal; the maintainer presses Publish there. Never tag
  without being asked, and follow RELEASING.md (`VERSION_NAME`, `SDK_VERSION` and CHANGELOG
  agree, or the workflow refuses the tag).
- Commits are authored as `Funnelbase <349443583+Funnelbase@users.noreply.github.com>`.
