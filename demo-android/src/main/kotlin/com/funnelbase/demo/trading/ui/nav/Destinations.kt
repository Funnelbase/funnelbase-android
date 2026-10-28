package com.funnelbase.demo.trading.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes.
 *
 * Separate from `analytics.Screens` on purpose. These are addresses the navigation library
 * resolves; those are the dimension a dashboard groups by. Reusing one string for both means a
 * routing refactor silently rewrites the analytics taxonomy, and the chart that has been
 * tracking `markets` for six months starts a new series.
 */
object Routes {
    const val WELCOME = "welcome"
    const val SIGN_IN = "sign_in"
    const val SIGN_UP = "sign_up"

    const val DASHBOARD = "dashboard"
    const val MARKETS = "markets"
    const val ORDERS = "orders"
    const val NEWS = "news"

    /** Reached from the dashboard, not a tab: push is a setting, not a section of the product. */
    const val PUSH = "push"

    /** Reached from the push screen: which kinds of message this person wants. */
    const val TOPICS = "push/topics"

    const val INSTRUMENT_DETAIL = "instrument/{symbol}"
    const val ARG_SYMBOL = "symbol"

    fun instrumentDetail(symbol: String): String = "instrument/$symbol"
}

/** The four bottom-bar tabs, in display order. */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD(Routes.DASHBOARD, "Dashboard", Icons.Filled.SpaceDashboard),
    MARKETS(Routes.MARKETS, "Markets", Icons.Filled.CandlestickChart),
    ORDERS(Routes.ORDERS, "Orders", Icons.AutoMirrored.Filled.ReceiptLong),
    NEWS(Routes.NEWS, "News", Icons.AutoMirrored.Filled.Article),
    ;

    companion object {
        val routes: Set<String> = entries.mapTo(mutableSetOf()) { it.route }
    }
}
