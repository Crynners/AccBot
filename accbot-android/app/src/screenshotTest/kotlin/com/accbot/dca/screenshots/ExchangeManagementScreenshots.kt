package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.ExchangeCard
import com.accbot.dca.presentation.components.SectionHeader
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExchangeManagementPreviewContent() {
    val state = SampleData.exchangeManagementUiState
    val availableExchanges = Exchange.entries.filter { it !in state.connectedExchanges }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.exchanges_title),
                onNavigateBack = {}
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {},
                containerColor = accentColor()
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.exchanges_add))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Connected exchanges
            item {
                SectionHeader(title = stringResource(R.string.exchanges_connected))
            }

            items(state.connectedExchanges, key = { it.name }) { exchange ->
                ExchangeCard(
                    exchange = exchange,
                    isConnected = true,
                    onClick = {},
                    onRemove = {}
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Available exchanges
            item {
                SectionHeader(title = stringResource(R.string.exchanges_available))
            }

            items(availableExchanges, key = { it.name }) { exchange ->
                ExchangeCard(
                    exchange = exchange,
                    isConnected = false,
                    onClick = {}
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "ExchangeManagement_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExchangeManagementPhoneEn() {
    AccBotTheme(darkTheme = true) { ExchangeManagementPreviewContent() }
}

@PreviewTest
@Preview(name = "ExchangeManagement_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun ExchangeManagementPhoneCs() {
    AccBotTheme(darkTheme = true) { ExchangeManagementPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "ExchangeManagement_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExchangeManagementTablet7En() {
    AccBotTheme(darkTheme = true) { ExchangeManagementPreviewContent() }
}

@PreviewTest
@Preview(name = "ExchangeManagement_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun ExchangeManagementTablet7Cs() {
    AccBotTheme(darkTheme = true) { ExchangeManagementPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "ExchangeManagement_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExchangeManagementTablet10En() {
    AccBotTheme(darkTheme = true) { ExchangeManagementPreviewContent() }
}

@PreviewTest
@Preview(name = "ExchangeManagement_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun ExchangeManagementTablet10Cs() {
    AccBotTheme(darkTheme = true) { ExchangeManagementPreviewContent() }
}
