package com.funnelbase.demo.trading.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.funnelbase.demo.trading.AppState
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.data.Order
import com.funnelbase.demo.trading.data.OrderSide
import com.funnelbase.demo.trading.data.OrderStatus
import com.funnelbase.demo.trading.ui.common.Pill
import com.funnelbase.demo.trading.ui.common.StatRow
import com.funnelbase.demo.trading.ui.format.Money
import com.funnelbase.demo.trading.ui.theme.NumericTextStyle
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(appState: AppState) {
    TrackScreenView(Screens.ORDERS)

    // The chips filter within this screen; they are not a navigation change, so nothing is
    // tracked here. Only screen and tab changes report a screen view.
    var selected by remember { mutableStateOf(OrderStatus.PENDING) }
    val orders = appState.market.orders
    val visible = orders.filter { it.status == selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OrderStatus.entries.forEach { status ->
                    val count = orders.count { it.status == status }
                    FilterChip(
                        selected = selected == status,
                        onClick = { selected = status },
                        label = { Text("${status.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TradeDemoColors.AccentMuted,
                            selectedLabelColor = TradeDemoColors.OnAccent,
                        ),
                    )
                }
            }

            if (visible.isEmpty()) {
                Text(
                    "No ${selected.label.lowercase()} orders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TradeDemoColors.TextSecondary,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(visible, key = Order::id) { order ->
                    OrderRow(order)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: Order) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(order.symbol, style = MaterialTheme.typography.titleSmall)
            Pill(
                text = order.side.label.uppercase(),
                color = if (order.side == OrderSide.BUY) TradeDemoColors.Up else TradeDemoColors.Down,
            )
            Spacer(Modifier.weight(1f))
            Pill(text = order.status.label, color = order.status.color())
        }

        Text(
            text = "${order.companyName} · ${order.id} · ${Money.relativeTime(order.placedMinutesAgo)}",
            style = MaterialTheme.typography.bodySmall,
            color = TradeDemoColors.TextSecondary,
            maxLines = 1,
        )

        Spacer(Modifier.height(10.dp))
        StatRow("Quantity", Money.quantity(order.quantity))
        StatRow("Price", Money.usd(order.price))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Cost",
                style = MaterialTheme.typography.bodyMedium,
                color = TradeDemoColors.TextSecondary,
            )
            // The one figure someone actually checks against quantity x price, so it gets the
            // emphasis the other two rows do not.
            Text(
                Money.usd(order.cost),
                style = NumericTextStyle,
                color = TradeDemoColors.Accent,
            )
        }
    }
}

private fun OrderStatus.color() = when (this) {
    OrderStatus.PENDING -> TradeDemoColors.Pending
    OrderStatus.FILLED -> TradeDemoColors.Filled
    OrderStatus.CANCELLED -> TradeDemoColors.Cancelled
}
