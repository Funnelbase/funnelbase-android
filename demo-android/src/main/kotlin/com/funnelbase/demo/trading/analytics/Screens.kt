package com.funnelbase.demo.trading.analytics

/**
 * Every screen name this app reports, in one place.
 *
 * `screen_name` is a dashboard dimension, so these strings are the taxonomy — renaming one
 * splits a chart in two rather than relabelling it. Constants rather than route strings so a
 * navigation refactor cannot silently rewrite the analytics.
 *
 * snake_case to match the event and property naming everywhere else in the platform.
 */
object Screens {
    const val WELCOME = "welcome"
    const val SIGN_IN = "sign_in"

    /** The paged sign-up intro. 1-based: `signup_intro_1` reads better in a chart than `_0`. */
    fun signUpIntro(page: Int): String = "signup_intro_${page + 1}"

    const val SIGN_UP_USERNAME = "signup_username"

    const val DASHBOARD = "dashboard"
    const val MARKETS = "markets"
    const val ORDERS = "orders"
    const val NEWS = "news"

    /** Which instrument is on it is an [Events.INSTRUMENT_VIEWED] event, not a screen property. */
    const val INSTRUMENT_DETAIL = "instrument_detail"

    /** Notifications: the permission, this install's token, and the simulated campaigns. */
    const val PUSH = "push_settings"

    /** Notification settings: a switch per topic the dashboard lists, reached from [PUSH]. */
    const val NOTIFICATION_SETTINGS = "notification_settings"
}
