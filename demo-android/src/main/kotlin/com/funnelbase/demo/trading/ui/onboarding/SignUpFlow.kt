package com.funnelbase.demo.trading.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.identity.UserId
import com.funnelbase.demo.trading.ui.theme.NumericTextStyle
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors
import com.funnelbase.sdk.Analytics
import kotlinx.coroutines.launch

private data class IntroPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val INTRO_PAGES = listOf(
    IntroPage(
        Icons.Filled.CandlestickChart,
        "Follow the market",
        "Fifty S&P 500 constituents with live-looking prices. Tap any of them for the company " +
            "detail, and favourite the ones you want on your dashboard.",
    ),
    IntroPage(
        Icons.Filled.PieChart,
        "Watch your portfolio",
        "Positions, cash and the day's move in one figure at the top of the dashboard, " +
            "recalculated from the same prices the market list shows.",
    ),
    IntroPage(
        Icons.AutoMirrored.Filled.ReceiptLong,
        "Track your orders",
        "Pending, filled and cancelled orders with quantity, price and total cost — and a news " +
            "feed for the tickers you follow.",
    ),
)

/**
 * The sign-up flow: three intro pages, then the username step.
 *
 * The intro is a pager rather than three navigation destinations because swiping is what makes
 * an onboarding carousel feel like one, and three destinations that only ever go forwards is a
 * back stack pretending to be a carousel.
 *
 * That has one consequence for analytics, handled below: page changes are not destination
 * changes, so this screen tracks them itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpFlow(
    onBack: () -> Unit,
    onSignedUp: (String) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { INTRO_PAGES.size })
    var onUsernameStep by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // settledPage, not currentPage: currentPage moves as soon as a drag crosses the halfway
    // point, and Compose composes the neighbouring page before it is on screen. Either would
    // report screens nobody actually looked at.
    LaunchedEffect(pagerState, onUsernameStep) {
        if (onUsernameStep) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }.collect { page ->
            Analytics.screen(Screens.signUpIntro(page))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onUsernameStep) "Create account" else "Getting started") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (onUsernameStep) onUsernameStep = false else onBack() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (onUsernameStep) {
                UsernameStep(onSignedUp = onSignedUp)
            } else {
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                    ) { page -> IntroPageContent(INTRO_PAGES[page]) }

                    PageIndicator(
                        pageCount = INTRO_PAGES.size,
                        currentPage = pagerState.currentPage,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 24.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onUsernameStep = true }) { Text("Skip") }

                        val isLastPage = pagerState.currentPage == INTRO_PAGES.lastIndex
                        Button(
                            onClick = {
                                if (isLastPage) {
                                    onUsernameStep = true
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                        ) {
                            Text(if (isLastPage) "Continue" else "Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            tint = TradeDemoColors.Accent,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyMedium,
            color = TradeDemoColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "indicator")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (selected) TradeDemoColors.Accent else TradeDemoColors.Outline,
                    ),
            )
        }
    }
}

/**
 * The final step: the username the whole identity hangs off.
 *
 * The derived id is shown live as you type. That is the point of the screen — it makes the
 * determinism visible rather than asserted in a comment, and it is the value to look up in the
 * dashboard when checking that events arrived.
 */
@Composable
private fun UsernameStep(onSignedUp: (String) -> Unit) {
    TrackScreenView(Screens.SIGN_UP_USERNAME)

    var username by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            "Pick a username. No password, no email — the username is all this demo needs.",
            style = MaterialTheme.typography.bodyMedium,
            color = TradeDemoColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Analytics user id",
            style = MaterialTheme.typography.labelMedium,
            color = TradeDemoColors.TextSecondary,
        )
        Text(
            text = if (username.isBlank()) "—" else UserId.forUsername(username),
            style = NumericTextStyle,
            color = TradeDemoColors.Accent,
        )
        Text(
            text = "Derived from the username, so signing in with it again is the same person.",
            style = MaterialTheme.typography.bodySmall,
            color = TradeDemoColors.TextSecondary,
        )

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { onSignedUp(username) },
            enabled = username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create account", modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}
