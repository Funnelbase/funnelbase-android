package com.funnelbase.demo.trading

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.funnelbase.demo.trading.push.PushRouting
import com.funnelbase.demo.trading.ui.nav.AppNavHost
import com.funnelbase.demo.trading.ui.theme.TradeDemoTheme
import com.funnelbase.sdk.Analytics

/**
 * The app's single Activity.
 *
 * Single-Activity is why `AnalyticsConfig.autoTrackScreenViews` is off in [TradingDemoApp]: the
 * SDK's Activity-level tracker would report "MainActivity" on every foreground and nothing else.
 * Screen names come from `TrackScreenView` in each screen composable instead.
 */
class MainActivity : ComponentActivity() {

    /**
     * Where a tapped push wants to go. The SDK starts this activity with the push attached and
     * has already recorded the open, so all that is left is the routing — which the SDK cannot
     * do, because only the app knows its own screens.
     */
    private val pushRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readPush(intent)

        setContent {
            TradeDemoTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(
                        appState = rememberAppState(),
                        pushRoute = pushRoute.value,
                        onPushRouted = { pushRoute.value = null },
                    )
                }
            }
        }
    }

    /** The app is already running: a second tap arrives here rather than through onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readPush(intent)
    }

    private fun readPush(intent: Intent?) {
        val payload = Analytics.push.payloadFrom(intent) ?: return
        val link = PushRouting.linkFor(payload, Analytics.push.actionIdFrom(intent))
        pushRoute.value = PushRouting.routeFor(link)
    }
}
