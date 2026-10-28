package com.funnelbase.demo.trading.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.funnelbase.demo.trading.ui.format.Money
import com.funnelbase.demo.trading.ui.theme.NumericTextStyle
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors

/**
 * A price move.
 *
 * The glyph is not decoration. Green-up/red-red is the convention, but it is a hue distinction
 * that roughly one man in twelve cannot make, so direction is always carried by the ▲/▼ as well
 * — colour reinforces the shape rather than being the only thing saying it.
 */
@Composable
fun ChangeText(
    changePercent: Double,
    modifier: Modifier = Modifier,
    absolute: Double? = null,
) {
    val isUp = changePercent >= 0
    val color = if (isUp) TradeDemoColors.Up else TradeDemoColors.Down
    val glyph = if (isUp) "▲" else "▼"

    Text(
        text = buildString {
            append(glyph)
            append(' ')
            if (absolute != null) {
                append(Money.signedUsd(absolute))
                append("  ")
            }
            append(Money.signedPercent(changePercent))
        },
        style = NumericTextStyle,
        color = color,
        modifier = modifier,
    )
}

/** A card. One corner radius and one border colour for the whole app, defined here. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

/** A section heading with an optional trailing action, e.g. "See all". */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        action?.invoke()
    }
}

/** A small filled pill — order status, sector, side. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * A `Label  Value` row, used for the stats grid and anywhere else two columns of text need to
 * line up. The value is monospaced so a stack of these has a straight right edge.
 */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TradeDemoColors.TextSecondary)
        Text(value, style = NumericTextStyle, color = TradeDemoColors.TextPrimary)
    }
}
