package com.funnelbase.sdk.fcm

import android.content.Context
import android.util.Log
import com.funnelbase.sdk.Analytics
import com.funnelbase.sdk.TokenKinds
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase's side of push, handed straight to the SDK. Declaring this module is all an app on
 * Firebase has to do: the service is in its manifest, and every decision about the token, the
 * notification and the receipt is the SDK's (docs/push.md).
 *
 * It is glue on purpose — every rule it could hold lives in `PushController`, where it is tested
 * without a device. What is left here is the two callbacks Firebase offers.
 *
 * The SDK must already be initialised, which it is whenever the app initialises in
 * `Application.onCreate`: a push starts the process through `Application` before this service.
 * An app that initialises later, from an Activity, loses the pushes that arrive first, and the
 * warning below is what says so.
 */
class AnalyticsMessagingService : FirebaseMessagingService() {

    /**
     * A data message from the delivery layer, rendered by the SDK. Firebase calls this for a
     * data-only message whether the app is in the foreground or the background, which is exactly
     * why Funnelbase sends one: a notification message would be drawn by the system instead,
     * and nothing would report that it arrived.
     *
     * Another sender's message reaching the same app is left alone, and returns false inside.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        if (!Analytics.push.handleMessage(message.data)) {
            Log.d(TAG, "A push arrived that was not ours; leaving it to the app")
        }
    }

    /** A new registration token, at install, on refresh, and after the app's data is cleared. */
    override fun onNewToken(token: String) {
        Analytics.push.setToken(token, TokenKinds.FCM)
    }

    companion object {
        private const val TAG = "Analytics"

        /**
         * Asks Firebase for this install's token and registers it. Worth calling once at startup:
         * [onNewToken] fires when a token *changes*, so an install whose token was issued before
         * the app had analytics would otherwise never register one.
         *
         * Returns false, having done nothing, when there is no Firebase project configured — no
         * `google-services.json`, which is the normal state of this repo when it is cloned. The
         * check lives here rather than in the app so that adding this module stays the whole of
         * an app's push integration, Firebase's own types included.
         */
        @JvmStatic
        fun registerCurrentToken(context: Context): Boolean {
            if (FirebaseApp.getApps(context).isEmpty()) {
                Log.i(TAG, "No Firebase project is configured, so no push token was registered")
                return false
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> Analytics.push.setToken(token, TokenKinds.FCM) }
                .addOnFailureListener { failure -> Log.w(TAG, "Firebase gave no push token: ${failure.message}") }
            return true
        }
    }
}
