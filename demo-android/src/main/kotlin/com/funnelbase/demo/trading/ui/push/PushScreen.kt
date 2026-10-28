package com.funnelbase.demo.trading.ui.push

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.push.PushSimulation
import com.funnelbase.demo.trading.ui.common.Pill
import com.funnelbase.demo.trading.ui.common.SectionCard
import com.funnelbase.demo.trading.ui.common.SectionHeader
import com.funnelbase.demo.trading.ui.common.StatRow
import com.funnelbase.demo.trading.ui.theme.NumericTextStyle
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors
import com.funnelbase.sdk.Analytics
import com.funnelbase.sdk.TokenKinds
import java.util.UUID

/**
 * Push, from the device's side: whether notifications are allowed, which token this install
 * registered, and a way to see a campaign without waiting for one.
 *
 * It is a demo screen, so it shows the machinery rather than hiding it. The simulate buttons are
 * the part worth having: they hand the SDK the same `data` map Funnelbase sends, so the
 * notification, its image, its buttons, the receipt and the tap can all be seen on an emulator
 * with no Firebase project at all (docs/push.md, "Trying push without a provider").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushScreen(onBack: () -> Unit, onTopics: () -> Unit) {
    TrackScreenView(Screens.PUSH)

    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Analytics.push.notificationsEnabled()) }
    var token by remember { mutableStateOf(Analytics.push.registeredToken()) }
    var manualToken by remember { mutableStateOf("") }
    var simulated by remember { mutableIntStateOf(0) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Whatever the person chose, the registry needs to hear it: the token's status is the
        // permission, so it is re-registered rather than assumed.
        Analytics.push.refreshToken()
        allowed = Analytics.push.notificationsEnabled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", style = MaterialTheme.typography.titleLarge) },
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
            SectionCard {
                SectionHeader("Permission") {
                    Pill(
                        text = if (allowed) "Allowed" else "Blocked",
                        color = if (allowed) TradeDemoColors.Up else TradeDemoColors.Down,
                    )
                }
                Text(
                    "From Android 13 a push needs the person's permission. The SDK never asks — it has no " +
                        "screen to ask from — so the app asks and the SDK reads the answer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TradeDemoColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (!allowed) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(notificationSettings(context.packageName))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text("Allow notifications")
                    }
                }
            }

            SectionCard {
                SectionHeader("Topics")
                Text(
                    "Which kinds of message you want, one switch each. The list is the dashboard's, and a " +
                        "campaign sent under a topic skips whoever switched it off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TradeDemoColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = onTopics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text("Choose topics")
                }
            }

            SectionCard {
                SectionHeader("This install's token")
                Text(
                    token ?: "None registered yet. With Firebase configured the SDK registers one at startup; " +
                        "without it, paste any string below and the dashboard can target this install through a " +
                        "custom gateway.",
                    style = if (token == null) MaterialTheme.typography.bodySmall else NumericTextStyle,
                    color = if (token == null) TradeDemoColors.TextSecondary else TradeDemoColors.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("A token of your own") },
                    placeholder = { Text("opaque-…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                OutlinedButton(
                    onClick = {
                        val value = manualToken.trim().ifBlank { "opaque-" + UUID.randomUUID() }
                        Analytics.push.setToken(value, TokenKinds.OPAQUE)
                        manualToken = ""
                        token = Analytics.push.registeredToken()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text("Register it")
                }
            }

            SectionCard {
                SectionHeader("See a campaign") {
                    if (simulated > 0) Pill(text = "$simulated sent", color = TradeDemoColors.Accent)
                }
                Text(
                    "The same payload the platform sends, handed to the SDK here. Everything after the " +
                        "transport is real: the notification, the receipt event, and the tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TradeDemoColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                SimulateButton("Basic notification", { PushSimulation.basic(delivery()) }) { simulated += 1 }
                SimulateButton("With an image and two buttons", { PushSimulation.rich(delivery()) }) { simulated += 1 }
                SimulateButton("Collapsing: replaces the last one", { PushSimulation.collapsing(delivery()) }) { simulated += 1 }
                StatRow("Image comes from", "10.0.2.2:9099")
                Text(
                    "Serve an image at /media/sample.png on the host's port 9099 for it to load; without it the notification " +
                        "still shows, without its picture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TradeDemoColors.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** A simulate button, so the three read the same. */
@Composable
private fun SimulateButton(label: String, payload: () -> Map<String, String>, onSent: () -> Unit) {
    OutlinedButton(
        onClick = {
            Analytics.push.handleMessage(payload())
            onSent()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Text(label)
    }
}

/** A delivery id per simulated push, so each one is its own notification and its own receipt. */
private fun delivery(): String = "sim-" + UUID.randomUUID().toString().take(8)

private fun notificationSettings(packageName: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
