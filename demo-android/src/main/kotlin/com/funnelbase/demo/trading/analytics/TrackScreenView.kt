package com.funnelbase.demo.trading.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.funnelbase.sdk.Analytics

/**
 * Reports a screen view. Call once, at the top of every screen composable.
 *
 * `LaunchedEffect` keyed on the name fires when the screen enters composition and again when it
 * re-enters — so arriving at a screen, coming back to it, and switching tabs each produce
 * exactly one event, while a recomposition produces none.
 *
 * The alternative, a single `NavController.OnDestinationChangedListener`, was not taken: it
 * cannot see the sign-up intro pager's pages, which are one destination showing three screens,
 * and running both mechanisms would double-count everything else.
 *
 * No properties, on purpose: a screen view says where the user is and carries `screen_name`
 * only. What they are looking at is a custom event (see [Events]), which the catalog can
 * describe and the Events page can break down.
 *
 * `Analytics.screen` is a no-op when the SDK was never initialised (no ingest key configured),
 * so nothing here needs to check for that.
 */
@Composable
fun TrackScreenView(name: String) {
    LaunchedEffect(name) { Analytics.screen(name) }
}
