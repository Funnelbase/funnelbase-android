package com.funnelbase.demo.trading.ui.markets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.funnelbase.demo.trading.AppState
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.data.Instrument
import com.funnelbase.demo.trading.ui.common.ChangeText
import com.funnelbase.demo.trading.ui.format.Money
import com.funnelbase.demo.trading.ui.theme.NumericTextStyle
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    appState: AppState,
    onInstrumentSelected: (String) -> Unit,
) {
    TrackScreenView(Screens.MARKETS)

    val instruments = appState.market.instruments

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Markets", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${instruments.size} instruments · S&P 500",
                            style = MaterialTheme.typography.bodySmall,
                            color = TradeDemoColors.TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(instruments, key = Instrument::symbol) { instrument ->
                InstrumentRow(
                    instrument = instrument,
                    isFavourite = appState.isFavourite(instrument.symbol),
                    onToggleFavourite = { appState.toggleFavourite(instrument.symbol) },
                    onClick = { onInstrumentSelected(instrument.symbol) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun InstrumentRow(
    instrument: Instrument,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onToggleFavourite, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                // The label says what the tap does, not what the icon looks like — a screen
                // reader user needs the action, not the glyph.
                contentDescription = if (isFavourite) {
                    "Remove ${instrument.symbol} from favourites"
                } else {
                    "Add ${instrument.symbol} to favourites"
                },
                tint = if (isFavourite) TradeDemoColors.Accent else TradeDemoColors.TextSecondary,
            )
        }

        Column(Modifier.weight(1f)) {
            Text(instrument.symbol, style = MaterialTheme.typography.titleSmall)
            Text(
                text = instrument.name,
                style = MaterialTheme.typography.bodySmall,
                color = TradeDemoColors.TextSecondary,
                maxLines = 1,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.usd(instrument.price),
                style = NumericTextStyle,
                color = TradeDemoColors.TextPrimary,
            )
            ChangeText(instrument.changePercent)
        }
    }
}
