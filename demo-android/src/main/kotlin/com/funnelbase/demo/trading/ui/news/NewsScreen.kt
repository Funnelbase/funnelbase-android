package com.funnelbase.demo.trading.ui.news

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.funnelbase.demo.trading.AppState
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.data.NewsArticle
import com.funnelbase.demo.trading.ui.format.Money
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(appState: AppState) {
    TrackScreenView(Screens.NEWS)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News", style = MaterialTheme.typography.titleLarge) },
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
            items(appState.market.news, key = NewsArticle::id) { article ->
                ArticleRow(article)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun ArticleRow(article: NewsArticle) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(article.headline, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = article.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = TradeDemoColors.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${article.source} · ${Money.relativeTime(article.minutesAgo)}",
            style = MaterialTheme.typography.labelSmall,
            color = TradeDemoColors.TextSecondary,
        )
    }
}
