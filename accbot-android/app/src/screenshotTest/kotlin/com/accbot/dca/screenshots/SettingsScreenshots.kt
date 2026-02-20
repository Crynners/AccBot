package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.screens.BiometricToggleCard
import com.accbot.dca.presentation.screens.ExchangeSettingsCard
import com.accbot.dca.presentation.screens.SandboxToggleCard
import com.accbot.dca.presentation.screens.SettingsCard
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.Error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPreviewContent() {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Exchange Accounts
            item {
                Text(
                    text = stringResource(R.string.settings_exchange_accounts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_manage_exchanges),
                    subtitle = stringResource(R.string.settings_exchanges_connected, 2),
                    icon = Icons.Default.AccountBalance,
                    onClick = {}
                )
            }

            item {
                ExchangeSettingsCard(exchange = Exchange.COINMATE, onRemove = {})
            }

            item {
                ExchangeSettingsCard(exchange = Exchange.BINANCE, onRemove = {})
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // System
            item {
                Text(
                    text = stringResource(R.string.settings_system),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_battery_optimization),
                    subtitle = stringResource(R.string.settings_battery_unrestricted),
                    icon = Icons.Default.BatteryChargingFull,
                    onClick = {}
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_low_balance_warning),
                    subtitle = stringResource(R.string.settings_low_balance_subtitle, 3),
                    icon = Icons.Default.Warning,
                    onClick = {}
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_english),
                    icon = Icons.Default.Language,
                    onClick = {}
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_subtitle),
                    icon = Icons.Default.Notifications,
                    onClick = {}
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Security
            item {
                Text(
                    text = stringResource(R.string.settings_security),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                BiometricToggleCard(isEnabled = true, onToggle = {})
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // About
            item {
                Text(
                    text = stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_documentation),
                    subtitle = stringResource(R.string.settings_documentation_subtitle),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = {}
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Version
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.settings_accbot_dca),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v2.0.114 (20114)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_made_with_love),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "Settings_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SettingsPhoneEn() {
    AccBotTheme(darkTheme = true) { SettingsPreviewContent() }
}

@PreviewTest
@Preview(name = "Settings_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun SettingsPhoneCs() {
    AccBotTheme(darkTheme = true) { SettingsPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "Settings_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SettingsTablet7En() {
    AccBotTheme(darkTheme = true) { SettingsPreviewContent() }
}

@PreviewTest
@Preview(name = "Settings_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun SettingsTablet7Cs() {
    AccBotTheme(darkTheme = true) { SettingsPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "Settings_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SettingsTablet10En() {
    AccBotTheme(darkTheme = true) { SettingsPreviewContent() }
}

@PreviewTest
@Preview(name = "Settings_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun SettingsTablet10Cs() {
    AccBotTheme(darkTheme = true) { SettingsPreviewContent() }
}
