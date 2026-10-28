package com.funnelbase.sdk.internal

import com.funnelbase.sdk.AnalyticsConfig
import com.funnelbase.sdk.PushButton
import com.funnelbase.sdk.PushPayload

/**
 * Every decision about a notification, taken before any framework object exists: which channel it
 * lands on, which notification it replaces, whether there is an image worth fetching, and which
 * buttons to build. [AndroidNotificationPresenter] only builds what this says, which is what keeps
 * the decisions testable on a plain JVM.
 */
internal data class PushRenderPlan(
    val payload: PushPayload,
    val channelId: String,
    /** Android replaces a notification only when the new one shares its tag and id. */
    val tag: String,
    val notificationId: Int,
    val title: String,
    val body: String,
    /** The body again: a message long enough to be cut off collapsed is readable expanded. */
    val expandedText: String,
    /** Only ever an http or https url, because it is the SDK that has to open it. */
    val imageUrl: String?,
    val buttons: List<PushButton>,
) {
    companion object {
        fun of(payload: PushPayload, config: AnalyticsConfig): PushRenderPlan {
            // A collapse key is the sender saying "this replaces the last one of these"; without
            // one, every delivery is a notification of its own.
            val tag = payload.collapseKey ?: payload.deliveryId
            return PushRenderPlan(
                payload = payload,
                channelId = payload.channelId ?: config.pushChannelId,
                tag = tag,
                notificationId = tag.hashCode(),
                title = payload.title,
                body = payload.body,
                expandedText = payload.body,
                imageUrl = payload.imageUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
                buttons = payload.buttons,
            )
        }
    }
}
