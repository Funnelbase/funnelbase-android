package com.funnelbase.demo.trading.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funnelbase.demo.trading.AppState
import com.funnelbase.demo.trading.analytics.Events
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.ui.common.ChangeText
import com.funnelbase.demo.trading.ui.common.Pill
import com.funnelbase.demo.trading.ui.common.SectionCard
import com.funnelbase.demo.trading.ui.common.StatRow
import com.funnelbase.demo.trading.ui.format.Money
import com.funnelbase.demo.trading.ui.theme.NumericTextStyle
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors
import com.funnelbase.sdk.Analytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentDetailScreen(
    appState: AppState,
    symbol: String,
    onBack: () -> Unit,
) {
    val instrument = appState.market.instrument(symbol)

    // Reachable only in principle — every route into this screen comes from a list built from
    // the same instrument set. Handled anyway rather than force-unwrapped, because a crash on
    // a bad deep link is a worse answer than a back button.
    if (instrument == null) {
        // A screen was shown, but no instrument was viewed.
        TrackScreenView(Screens.INSTRUMENT_DETAIL)
        UnknownInstrument(symbol = symbol, onBack = onBack)
        return
    }

    TrackScreenView(Screens.INSTRUMENT_DETAIL)
    // Which instrument is a product fact, not navigation context, so it is its own event
    // rather than a property on the screen view. Same firing rule as TrackScreenView: once
    // on entering, again on re-entering, never on recomposition. Needs a catalog definition —
    // see Events.
    LaunchedEffect(instrument.symbol) {
        Analytics.track(Events.INSTRUMENT_VIEWED, mapOf(Events.PROPERTY_SYMBOL to instrument.symbol))
    }

    val isFavourite = appState.isFavourite(instrument.symbol)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(instrument.symbol, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { appState.toggleFavourite(instrument.symbol) }) {
                        Icon(
                            imageVector = if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isFavourite) {
                                "Remove from favourites"
                            } else {
                                "Add to favourites"
                            },
                            tint = if (isFavourite) {
                                TradeDemoColors.Accent
                            } else {
                                TradeDemoColors.TextSecondary
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(instrument.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Pill(instrument.sector, TradeDemoColors.Accent)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = Money.usd(instrument.price),
                    style = NumericTextStyle.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                    color = TradeDemoColors.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                ChangeText(
                    changePercent = instrument.changePercent,
                    absolute = instrument.changeAbsolute,
                )
            }

            SectionCard {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                StatRow("Open", Money.usd(instrument.open))
                StatRow("Day high", Money.usd(instrument.dayHigh))
                StatRow("Day low", Money.usd(instrument.dayLow))
                StatRow("Previous close", Money.usd(instrument.previousClose))
                StatRow("Volume", Money.compact(instrument.volume.toDouble()))
                StatRow("Market cap", Money.compactUsd(instrument.marketCapUsd))
            }

            SectionCard {
                Text("About the company", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = instrument.about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TradeDemoColors.TextSecondary,
                )
            }

            // Deliberately inert. Wiring these up would mean order placement, which is a feature
            // this demo does not have — and a button that silently does nothing is better than
            // a screen that looks like it cannot trade at all.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Buy") }
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Sell") }
            }
        }
    }
}

@Composable
private fun UnknownInstrument(symbol: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No instrument $symbol", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
