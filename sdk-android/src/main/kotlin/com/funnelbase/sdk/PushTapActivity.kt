package com.funnelbase.sdk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Where a tapped notification lands. It has no UI: it reports the open and hands the push to the
 * app, then finishes.
 *
 * It exists because of two rules that pull in opposite directions. Since Android 12 a notification
 * may not be answered by a service or a receiver that then starts an activity, so the pending
 * intent has to point at an activity — this one. And the SDK must not guess how the app navigates,
 * so this activity does not navigate: it calls the handler the app registered, and failing that
 * starts the app's own launcher screen with the push attached to the intent, where the app reads it
 * with [AnalyticsPush.payloadFrom].
 */
class PushTapActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = AnalyticsPush.payloadFrom(intent)
        val actionId = AnalyticsPush.actionIdFrom(intent)

        if (payload == null) {
            Log.w(TAG, "A notification tap carried no push payload")
            finish()
            return
        }

        // Reported here rather than by the app, so an app that forgets to route still counts the
        // open — the fact belongs to the delivery, not to the screen it leads to.
        Analytics.pushController?.reportOpen(payload, actionId)
            ?: Log.w(TAG, "A push was tapped before Analytics.initialize — the open was not recorded")

        val routed = runCatching { AnalyticsPush.tapHandler()?.onPushOpened(payload, actionId) == true }
            .onFailure { Log.w(TAG, "The app's push handler failed: ${it.message}") }
            .getOrDefault(false)
        if (!routed) openApp(payload, actionId)

        finish()
    }

    /**
     * The app's own launcher screen, carrying the push. The deep link rides as the intent's data
     * as well, so an app that already routes links needs no push-specific code at all.
     */
    private fun openApp(payload: PushPayload, actionId: String?) {
        val link = actionId?.let { id -> payload.buttons.firstOrNull { it.id == id }?.deepLink } ?: payload.deepLink
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "This app has no launcher activity, so a tapped push has nowhere to go")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(AnalyticsPush.EXTRA_PAYLOAD, AnalyticsPush.encode(payload))
        actionId?.let { intent.putExtra(AnalyticsPush.EXTRA_ACTION_ID, it) }
        link?.let { intent.data = runCatching { Uri.parse(it) }.getOrNull() }
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "Could not open the app: ${it.message}") }
    }

    private companion object {
        const val TAG = "Analytics"
    }
}
