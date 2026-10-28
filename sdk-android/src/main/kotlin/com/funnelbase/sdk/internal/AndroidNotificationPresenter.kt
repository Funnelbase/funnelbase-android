package com.funnelbase.sdk.internal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.util.Log
import com.funnelbase.sdk.AnalyticsConfig
import com.funnelbase.sdk.AnalyticsPush
import com.funnelbase.sdk.PushTapActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor

/**
 * The only part of push that touches the framework: it turns a [PushRenderPlan] into a
 * notification and posts it.
 *
 * Built on the platform's own `Notification.Builder` rather than `NotificationCompat`, because the
 * SDK's dependency budget is kotlinx-serialization and nothing else, and `minSdk 26` already
 * guarantees notification channels. Every tap goes to [PushTapActivity] through a `PendingIntent`
 * on an activity: since Android 12 a notification may not be answered by a service or a receiver
 * that then starts one.
 */
internal class AndroidNotificationPresenter(
    private val context: Context,
    private val config: AnalyticsConfig,
    private val images: Executor,
) : NotificationPresenter {

    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun show(plan: PushRenderPlan) {
        if (manager == null) {
            Log.w(TAG, "No notification manager: nothing shown for delivery ${plan.payload.deliveryId}")
            return
        }
        ensureChannel(plan.channelId)
        // Without an image there is nothing to wait for, so the notification is posted on the
        // caller's thread; with one, the download happens off it and the notification follows.
        if (plan.imageUrl == null) {
            post(plan, image = null)
        } else {
            images.execute {
                val image = runCatching { download(plan.imageUrl) }
                    .onFailure { log("Could not fetch ${plan.imageUrl}: ${it.message}") }
                    .getOrNull()
                post(plan, image)
            }
        }
    }

    /**
     * The channel the message asked for, created if the app has not created it: automatic
     * rendering must never depend on the app having set one up. A channel the app *has* created
     * keeps its own name and importance, because this call cannot change either.
     */
    private fun ensureChannel(channelId: String) {
        if (manager?.getNotificationChannel(channelId) != null) return
        manager?.createNotificationChannel(
            NotificationChannel(channelId, config.pushChannelName, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun post(plan: PushRenderPlan, image: Bitmap?) {
        val builder = Notification.Builder(context, plan.channelId)
            .setSmallIcon(smallIcon())
            .setContentTitle(plan.title.ifBlank { appLabel() })
            .setContentText(plan.body)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(plan, actionId = null))

        if (image != null) {
            builder.style = Notification.BigPictureStyle().bigPicture(image).also {
                // Shown as the thumbnail when collapsed and the picture when expanded, which is
                // what makes one image worth downloading at all.
                builder.setLargeIcon(image)
                it.bigLargeIcon(null as Bitmap?)
            }
        } else if (plan.expandedText.isNotBlank()) {
            builder.style = Notification.BigTextStyle().bigText(plan.expandedText)
        }

        plan.buttons.forEach { button ->
            builder.addAction(
                Notification.Action.Builder(null as Icon?, button.title, tapIntent(plan, button.id)).build(),
            )
        }

        runCatching { manager?.notify(plan.tag, plan.notificationId, builder.build()) }
            .onFailure { log("Could not post the notification: ${it.message}") }
    }

    /**
     * One pending intent per button, and one for the notification itself. The request code has to
     * differ per action or the framework hands back the first intent for all of them, and the
     * whole point of a button is which one was tapped.
     */
    private fun tapIntent(plan: PushRenderPlan, actionId: String?): PendingIntent {
        val intent = Intent(context, PushTapActivity::class.java)
            .setPackage(context.packageName)
            .putExtra(AnalyticsPush.EXTRA_PAYLOAD, AnalyticsPush.encode(plan.payload))
            .apply { actionId?.let { putExtra(AnalyticsPush.EXTRA_ACTION_ID, it) } }
        val requestCode = 31 * plan.notificationId + (actionId?.hashCode() ?: 0)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            // Immutable, as every pending intent must be since Android 12; updated so a replacing
            // notification's buttons carry the new payload rather than the collapsed one's.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun smallIcon(): Int =
        config.pushSmallIconResId.takeIf { it != 0 } ?: context.applicationInfo.icon

    private fun appLabel(): String =
        runCatching { context.applicationInfo.loadLabel(context.packageManager).toString() }.getOrDefault("")

    /**
     * Fetches the image, refusing anything past the configured size — including a server that
     * declares one size and sends another, which is why the read is counted rather than trusted.
     */
    private fun download(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                log("The image answered ${connection.responseCode}")
                return null
            }
            val declared = connection.contentLengthLong
            if (declared > config.pushMaxImageBytes) {
                log("The image is $declared bytes, over the ${config.pushMaxImageBytes} allowed")
                return null
            }
            val bytes = connection.inputStream.use { stream ->
                val buffer = ByteArray(BUFFER_BYTES)
                val collected = java.io.ByteArrayOutputStream()
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    collected.write(buffer, 0, read)
                    if (collected.size() > config.pushMaxImageBytes) {
                        log("The image exceeded ${config.pushMaxImageBytes} bytes mid-download")
                        return null
                    }
                }
                collected.toByteArray()
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            connection.disconnect()
        }
    }

    private fun log(message: String) {
        if (config.debugLogging) Log.d(TAG, message) else Log.w(TAG, message)
    }

    private companion object {
        const val TAG = "Analytics"
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val BUFFER_BYTES = 16 * 1024
    }
}
