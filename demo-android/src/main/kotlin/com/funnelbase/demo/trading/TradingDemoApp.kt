package com.funnelbase.demo.trading

import android.app.Application
import android.util.Log
import com.funnelbase.demo.trading.BuildConfig
import com.funnelbase.sdk.Analytics
import com.funnelbase.sdk.AnalyticsConfig
import com.funnelbase.sdk.fcm.AnalyticsMessagingService

class TradingDemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.ANALYTICS_API_KEY.isBlank()) {
            Log.w(TAG, "No ingest key configured — analytics is off. See docs/demo-app.md.")
            return
        }

        Analytics.initialize(
            this,
            AnalyticsConfig(
                apiKey = BuildConfig.ANALYTICS_API_KEY,
                endpoint = BuildConfig.ANALYTICS_ENDPOINT,
                debugLogging = BuildConfig.DEBUG,
                autoTrackScreenViews = false,
            ),
        )

        registerPushToken()
    }

    /**
     * Asks Firebase for this install's token once per launch.
     *
     * `onNewToken` fires when a token *changes*, so an install whose token was issued before the
     * app had analytics would never register it otherwise. Without a Firebase project there is
     * nothing to ask, and the glue says so rather than failing: the Notifications screen registers
     * a token of your own instead, which is how the fake gateway is exercised.
     */
    private fun registerPushToken() {
        runCatching { AnalyticsMessagingService.registerCurrentToken(this) }
            .onFailure { Log.w(TAG, "Could not ask Firebase for a push token: ${it.message}") }
    }

    private companion object {
        const val TAG = "TradeDemo"
    }
}
