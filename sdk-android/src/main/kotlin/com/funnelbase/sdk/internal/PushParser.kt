package com.funnelbase.sdk.internal

import com.funnelbase.sdk.PushButton
import com.funnelbase.sdk.PushPayload
import com.funnelbase.sdk.SdkJson
import kotlinx.serialization.builtins.ListSerializer

/**
 * Reads the `data` map of a message the delivery layer sent (docs/push.md, "The
 * payload"). Every value there is a string, buttons included, because that is all Firebase
 * carries.
 *
 * A message without both ids is not ours: there would be nothing to report a receipt against, and
 * another sender's push may well arrive through the same transport. Anything else malformed costs
 * only the part that could not be read — a broken button list still leaves a notification worth
 * showing.
 */
internal object PushParser {

    /** Keys the delivery layer itself uses; everything else in the map is the campaign's. */
    private val RESERVED = setOf(
        "delivery_id", "run_id", "title", "body", "image_url", "deep_link", "collapse_key",
        "android_channel_id", "buttons",
    )

    /** Android shows three at most, and a fourth in the payload is a sender's mistake, not ours. */
    private const val MAX_BUTTONS = 3

    fun parse(data: Map<String, String>): PushPayload? {
        val deliveryId = data["delivery_id"]?.trim().orEmpty()
        val runId = data["run_id"]?.trim().orEmpty()
        if (deliveryId.isEmpty() || runId.isEmpty()) return null

        return PushPayload(
            deliveryId = deliveryId,
            runId = runId,
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty(),
            imageUrl = data["image_url"]?.takeIf { it.isNotBlank() },
            deepLink = data["deep_link"]?.takeIf { it.isNotBlank() },
            collapseKey = data["collapse_key"]?.takeIf { it.isNotBlank() },
            channelId = data["android_channel_id"]?.takeIf { it.isNotBlank() },
            buttons = buttons(data["buttons"]),
            data = data.filterKeys { it !in RESERVED },
        )
    }

    private fun buttons(encoded: String?): List<PushButton> {
        if (encoded.isNullOrBlank()) return emptyList()
        val parsed = runCatching { SdkJson.decodeFromString(ListSerializer(PushButton.serializer()), encoded) }
            .getOrElse { return emptyList() }
        return parsed
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .take(MAX_BUTTONS)
    }
}
