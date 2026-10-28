package com.funnelbase.demo.trading.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.funnelbase.demo.trading.analytics.Screens
import com.funnelbase.demo.trading.analytics.TrackScreenView
import com.funnelbase.demo.trading.ui.theme.TradeDemoColors

/**
 * Sign-in. Any username and any password are accepted — there is no auth backend, by design.
 *
 * The username is not decoration: it is hashed into the `user_id` every event afterwards carries
 * (see `identity.UserId`), so signing in as the same name twice produces the same person.
 * The "Pro plan" switch stands in for billing: a pro account's `$identify` carries the day its
 * plan renews (`AppState.signIn`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onSignedIn: (username: String, pro: Boolean) -> Unit,
) {
    TrackScreenView(Screens.SIGN_IN)

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pro by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Text(
                "Welcome back. Any credentials work — this demo has no authentication server.",
                style = MaterialTheme.typography.bodyMedium,
                color = TradeDemoColors.TextSecondary,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Pro plan",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = pro, onCheckedChange = { pro = it })
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSignedIn(username, pro) },
                // The password is genuinely unchecked, but an empty username would hash to a
                // shared id for everyone who left the field blank.
                enabled = username.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign in", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}
