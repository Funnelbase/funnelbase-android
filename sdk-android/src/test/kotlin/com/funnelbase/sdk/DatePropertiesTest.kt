package com.funnelbase.sdk

import com.funnelbase.sdk.internal.toJsonProperties
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Date

/**
 * Dates in traits and properties (docs/event-contract.md, "Dates"): ISO-8601 text, a calendar
 * day as `YYYY-MM-DD` and an exact moment in UTC with `Z` — what the catalog's `date` type reads.
 * Mirrored by the iOS SDK's `DatePropertiesTests`.
 */
class DatePropertiesTest {

    @Test
    fun `a calendar day goes out as YYYY-MM-DD`() {
        val json = mapOf("plan_renews_on" to LocalDate.of(2026, 11, 3)).toJsonProperties()
        assertEquals(JsonPrimitive("2026-11-03"), json["plan_renews_on"])
    }

    @Test
    fun `an instant goes out in UTC with Z, to the millisecond`() {
        val json = mapOf("at" to Instant.parse("2026-11-03T09:00:00.123456789Z")).toJsonProperties()
        assertEquals(JsonPrimitive("2026-11-03T09:00:00.123Z"), json["at"])
    }

    @Test
    fun `a whole-second instant has no fraction`() {
        val json = mapOf("at" to Instant.parse("2026-11-03T09:00:00Z")).toJsonProperties()
        assertEquals(JsonPrimitive("2026-11-03T09:00:00Z"), json["at"])
    }

    @Test
    fun `a java util Date is the same moment`() {
        val date = Date.from(Instant.parse("2026-11-03T06:30:00Z"))
        assertEquals(JsonPrimitive("2026-11-03T06:30:00Z"), mapOf("at" to date).toJsonProperties()["at"])
    }

    @Test
    fun `other types keep their old encoding`() {
        val json = mapOf("s" to "x", "n" to 42, "b" to true, "none" to null).toJsonProperties()
        assertEquals(mapOf("s" to JsonPrimitive("x"), "n" to JsonPrimitive(42), "b" to JsonPrimitive(true)), json)
    }
}
