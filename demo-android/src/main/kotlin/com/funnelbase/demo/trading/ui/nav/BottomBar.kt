package com.funnelbase.demo.trading.ui.nav

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors

@Composable
fun BottomBar(
    currentRoute: String?,
    onTabSelected: (Tab) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Tab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                // Labels always shown: four tabs fit comfortably, and an icon-only bar makes
                // people guess what a candlestick glyph means.
                label = { Text(tab.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TradeDemoColors.OnAccent,
                    selectedTextColor = TradeDemoColors.Accent,
                    indicatorColor = TradeDemoColors.AccentMuted,
                    unselectedIconColor = TradeDemoColors.TextSecondary,
                    unselectedTextColor = TradeDemoColors.TextSecondary,
                ),
            )
        }
    }
}
