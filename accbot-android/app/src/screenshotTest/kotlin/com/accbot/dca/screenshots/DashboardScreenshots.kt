package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.presentation.screens.DcaPlanCard
import com.accbot.dca.presentation.screens.HoldingsPager
import com.accbot.dca.presentation.screens.QuickActionsRow
import com.accbot.dca.presentation.ui.theme.AccBotTheme

@Composable
private fun DashboardPreviewContent() {
    val state = SampleData.dashboardUiState
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {},
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                HoldingsPager(
                    holdings = state.holdings,
                    isPriceLoading = false,
                    onRefreshPrices = {}
                )
            }

            item {
                Text(
                    stringResource(R.string.dashboard_active_plans),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(state.activePlans, key = { it.plan.id }) { planWithBalance ->
                DcaPlanCard(
                    planWithBalance = planWithBalance,
                    onToggle = {},
                    onClick = {}
                )
            }

            item {
                QuickActionsRow(
                    onViewHistory = {},
                    onRunNow = {}
                )
            }

            item { Spacer(modifier = Modifier.height(56.dp)) }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "Dashboard_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun DashboardPhoneEn() {
    AccBotTheme(darkTheme = true) { DashboardPreviewContent() }
}

@PreviewTest
@Preview(name = "Dashboard_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun DashboardPhoneCs() {
    AccBotTheme(darkTheme = true) { DashboardPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "Dashboard_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun DashboardTablet7En() {
    AccBotTheme(darkTheme = true) { DashboardPreviewContent() }
}

@PreviewTest
@Preview(name = "Dashboard_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun DashboardTablet7Cs() {
    AccBotTheme(darkTheme = true) { DashboardPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "Dashboard_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun DashboardTablet10En() {
    AccBotTheme(darkTheme = true) { DashboardPreviewContent() }
}

@PreviewTest
@Preview(name = "Dashboard_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun DashboardTablet10Cs() {
    AccBotTheme(darkTheme = true) { DashboardPreviewContent() }
}
