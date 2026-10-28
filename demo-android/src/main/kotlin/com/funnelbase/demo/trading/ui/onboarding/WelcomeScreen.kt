package com.funnelbase.demo.trading.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
) {
    TrackScreenView(Screens.WELCOME)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            tint = TradeDemoColors.Accent,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(24.dp))

        Text("Trade Demo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Markets, portfolio and orders — a sample app instrumented with the " +
                "in-house analytics SDK.",
            style = MaterialTheme.typography.bodyMedium,
            color = TradeDemoColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onSignUp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create account", modifier = Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign in", modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}
