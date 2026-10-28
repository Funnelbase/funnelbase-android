# Changelog

Both artifacts, `funnelbase-android` and `funnelbase-android-fcm`, are released together under one
version.

## 0.4.0

The first public release, under the name Funnelbase (`com.funnel-base`, Kotlin package
`com.funnelbase.sdk`). What it sends is unchanged from the versions before it, which were used
privately:

- 0.4.0 — notification topics: `Analytics.topics`, the passive `$topic_preference`, and the read
  back from `GET /v1/topics`.
- 0.3.0 — push: `Analytics.push`, the token as the passive `$push_token`, rendering with images,
  buttons and collapsing, `$push_received` and `$push_opened`.
- 0.2.0 — the event contract: `device_id` on every event, `$first_open` once per install with
  `installed_at_ms`, `identify` as the link, `alias` removed.
