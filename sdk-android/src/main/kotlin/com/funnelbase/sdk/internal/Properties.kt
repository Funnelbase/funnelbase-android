package com.funnelbase.sdk.internal

import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * Converts host-app property maps to JSON primitives.
 *
 * The public API takes `Map<String, Any?>` because that is what calling code naturally has.
 * Types are preserved rather than stringified — the backend splits properties into typed
 * columns, so a number sent as `"42"` is permanently a string and unfilterable by range.
 *
 * Dates travel as ISO-8601 text, which is what a property or trait declared `date` in the
 * catalog accepts (docs/event-contract.md): a [LocalDate] is a calendar day, `2026-11-03`,
 * read in whichever zone asks; an [Instant] or a [Date] is an exact moment, sent in UTC with
 * `Z` — `2026-11-03T09:00:00Z`. Any other date type (a `LocalDateTime` has no zone, so it
 * is not a date to the server) falls to its string form like every other object.
 */
internal fun Map<String, Any?>.toJsonProperties(): Map<String, JsonPrimitive> =
    buildMap(size) {
        for ((key, value) in this@toJsonProperties) {
            when (value) {
                // Dropped rather than encoded: the backend discards nulls anyway, and sending
                // them wastes bytes on a mobile connection.
                null -> Unit
                is String -> put(key, JsonPrimitive(value))
                is Boolean -> put(key, JsonPrimitive(value))
                is Number -> put(key, JsonPrimitive(value))
                is LocalDate -> put(key, JsonPrimitive(isoDay(value)))
                is Instant -> put(key, JsonPrimitive(isoMoment(value)))
                is Date -> put(key, JsonPrimitive(isoMoment(value.toInstant())))
                // Enums and other objects become their string form. Better than silently
                // dropping a property the caller expected to see in the dashboard.
                else -> put(key, JsonPrimitive(value.toString()))
            }
        }
    }

/** `YYYY-MM-DD`. */
internal fun isoDay(day: LocalDate): String = day.toString()

/**
 * ISO-8601 in UTC with `Z`, to the millisecond — what a `Date` holds. `Instant.toString`
 * already ends in `Z`; truncating keeps a nanosecond clock from sending nine fraction digits.
 */
internal fun isoMoment(moment: Instant): String = moment.truncatedTo(ChronoUnit.MILLIS).toString()
