package com.funnelbase.demo.trading.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funnelbase.demo.trading.AppState
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.data.Instrument
import com.funnelbase.demo.trading.data.NewsArticle
import com.funnelbase.demo.trading.ui.common.ChangeText
import com.funnelbase.demo.trading.ui.common.SectionCard
import com.funnelbase.demo.trading.ui.common.SectionHeader
import com.funnelbase.demo.trading.ui.format.Money
import com.funnelbase.demo.trading.ui.theme.NumericTextStyle
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    appState: AppState,
    onInstrumentSelected: (String) -> Unit,
    onSeeAllNews: () -> Unit,
    onPush: () -> Unit,
    onSignOut: () -> Unit,
) {
    TrackScreenView(Screens.DASHBOARD)

    val market = appState.market
    val favourites = market.instruments.filter { appState.isFavourite(it.symbol) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dashboard", style = MaterialTheme.typography.titleLarge)
                        appState.username?.let {
                            Text(
                                text = "Signed in as $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = TradeDemoColors.TextSecondary,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onPush) {
                        Icon(Icons.Filled.NotificationsNone, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PortfolioValueCard(appState) }
            item {
                FavouriteInstrumentsCard(
                    favourites = favourites,
                    onInstrumentSelected = onInstrumentSelected,
                )
            }
            item {
                RecentNewsCard(
                    articles = market.news.take(3),
                    onSeeAll = onSeeAllNews,
                )
            }
        }
    }
}

@Composable
private fun PortfolioValueCard(appState: AppState) {
    val portfolio = appState.market.portfolio

    SectionCard {
        Text(
            "Portfolio value",
            style = MaterialTheme.typography.labelLarge,
            color = TradeDemoColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = Money.usd(portfolio.totalValue),
            style = NumericTextStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
            color = TradeDemoColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        ChangeText(
            changePercent = portfolio.dayChangePercent,
            absolute = portfolio.dayChange,
        )
        Text(
            "Today",
            style = MaterialTheme.typography.bodySmall,
            color = TradeDemoColors.TextSecondary,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LabelledAmount("Invested", Money.usd(portfolio.investedValue))
            LabelledAmount("Cash", Money.usd(portfolio.cashUsd))
            LabelledAmount("Positions", portfolio.holdings.size.toString())
        }
    }
}

@Composable
private fun LabelledAmount(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TradeDemoColors.TextSecondary)
        Text(value, style = NumericTextStyle, color = TradeDemoColors.TextPrimary)
    }
}

@Composable
private fun FavouriteInstrumentsCard(
    favourites: List<Instrument>,
    onInstrumentSelected: (String) -> Unit,
) {
    SectionCard {
        SectionHeader("Favourite instruments")
        Spacer(Modifier.height(4.dp))

        if (favourites.isEmpty()) {
            Text(
                "Nothing favourited yet. Tap the star on any instrument in Markets.",
                style = MaterialTheme.typography.bodyMedium,
                color = TradeDemoColors.TextSecondary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@SectionCard
        }

        favourites.forEach { instrument ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInstrumentSelected(instrument.symbol) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
    }
}

@Composable
private fun RecentNewsCard(articles: List<NewsArticle>, onSeeAll: () -> Unit) {
    SectionCard {
        SectionHeader("Recent news") {
            TextButton(onClick = onSeeAll) { Text("See all") }
        }

        articles.forEach { article ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = article.headline,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${article.source} · ${Money.relativeTime(article.minutesAgo)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TradeDemoColors.TextSecondary,
                )
            }
        }
    }
}
