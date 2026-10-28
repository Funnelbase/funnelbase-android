package com.funnelbase.demo.trading.ui.push

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.ui.common.SectionCard
import com.funnelbase.demo.trading.ui.common.SectionHeader
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors
import com.funnelbase.sdk.Analytics
import com.funnelbase.sdk.TopicChoice

/** What the list shows: still loading, the topics, or the read failed. */
private sealed interface TopicsState {
    data object Loading : TopicsState
    data class Loaded(val topics: List<TopicChoice>) : TopicsState
    data object Failed : TopicsState
}

/**
 * Which kinds of message this person wants, one switch per notification topic.
 *
 * The list is the dashboard's, read through `Analytics.topics.fetch` — the app hard-codes none of
 * it, so a topic added under Engagement appears here without a release. A switch sends the choice
 * at once through `Analytics.topics.set`; the SDK queues it like any event and keeps showing it
 * until the server has it, so the switch never flips back on the next read.
 *
 * A topic switched on is still silent while the app's notifications or the SDK's channel are off,
 * so that is said above the list, with the way to the system's settings — the only place either
 * can be turned back on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(onBack: () -> Unit) {
    TrackScreenView(Screens.NOTIFICATION_SETTINGS)

    val context = LocalContext.current
    var state by remember { mutableStateOf<TopicsState>(TopicsState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var allowed by remember { mutableStateOf(Analytics.push.notificationsEnabled()) }

    LaunchedEffect(attempt) {
        state = TopicsState.Loading
        state = Analytics.topics.fetch().fold(
            onSuccess = { TopicsState.Loaded(it) },
            onFailure = { TopicsState.Failed },
        )
    }

    // Back from the system's settings is where the switch or the channel may have changed; the
    // SDK re-registers the token on the same return, this only redraws the line.
    LifecycleResumeEffect(Unit) {
        allowed = Analytics.push.notificationsEnabled()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification topics", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            if (!allowed) {
                SectionCard {
                    Text(
                        "Notifications from this app are turned off, or their channel is blocked, so none of " +
                            "these will reach you until they are turned back on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TradeDemoColors.Down,
                    )
                    OutlinedButton(
                        onClick = { context.startActivity(appNotificationSettings(context.packageName)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text("Open notification settings")
                    }
                }
            }

            when (val current = state) {
                TopicsState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                TopicsState.Failed -> SectionCard {
                    Text("Couldn't load your notification topics", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Check your connection and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TradeDemoColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(
                        onClick = { attempt += 1 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text("Retry")
                    }
                }

                is TopicsState.Loaded -> SectionCard {
                    SectionHeader("What you hear about")
                    if (current.topics.isEmpty()) {
                        Text(
                            "This app has no notification topics yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TradeDemoColors.TextSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    current.topics.forEachIndexed { index, topic ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        TopicRow(topic) { subscribed ->
                            Analytics.topics.set(topic.key, subscribed)
                            state = TopicsState.Loaded(
                                current.topics.map {
                                    if (it.key == topic.key) it.copy(subscribed = subscribed, chosen = true) else it
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: TopicChoice, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(topic.name, style = MaterialTheme.typography.bodyLarge)
            val description = topic.description
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TradeDemoColors.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(checked = topic.subscribed, onCheckedChange = onChange)
    }
}

/** The system's notification settings for this app, where both the switch and the channels live. */
private fun appNotificationSettings(packageName: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
