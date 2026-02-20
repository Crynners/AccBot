package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.screens.portfolio.KpiCardContent
import com.accbot.dca.presentation.screens.portfolio.ChartZoomHeader
import com.accbot.dca.presentation.screens.portfolio.DrillDownChips
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor

@Composable
private fun PortfolioPreviewContent() {
    val uiState = SampleData.portfolioUiState

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.portfolio_title),
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.History, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // KPI Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.chart_all_fiat, "EUR"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor()
                        )
                        Spacer(Modifier.height(8.dp))
                        KpiCardContent(
                            uiState = uiState,
                            isSinglePair = false
                        )
                    }
                }
            }

            // Zoom header
            item {
                ChartZoomHeader(
                    zoomLevel = uiState.zoomLevel,
                    canNavigatePrev = false,
                    canNavigateNext = false,
                    onZoomOut = {},
                    onNavigatePrev = {},
                    onNavigateNext = {}
                )
            }

            // Drill-down chips
            item {
                DrillDownChips(
                    zoomLevel = uiState.zoomLevel,
                    availableYears = uiState.availableYears,
                    availableMonths = emptyList(),
                    onDrillDownYear = {},
                    onDrillDownMonth = { _, _ -> }
                )
            }

            // Simplified chart placeholder (Vico won't render in @Preview)
            item {
                PreviewLineChart(
                    chartData = uiState.chartData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "Portfolio_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PortfolioPhoneEn() {
    AccBotTheme(darkTheme = true) { PortfolioPreviewContent() }
}

@PreviewTest
@Preview(name = "Portfolio_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun PortfolioPhoneCs() {
    AccBotTheme(darkTheme = true) { PortfolioPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "Portfolio_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PortfolioTablet7En() {
    AccBotTheme(darkTheme = true) { PortfolioPreviewContent() }
}

@PreviewTest
@Preview(name = "Portfolio_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun PortfolioTablet7Cs() {
    AccBotTheme(darkTheme = true) { PortfolioPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "Portfolio_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PortfolioTablet10En() {
    AccBotTheme(darkTheme = true) { PortfolioPreviewContent() }
}

@PreviewTest
@Preview(name = "Portfolio_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun PortfolioTablet10Cs() {
    AccBotTheme(darkTheme = true) { PortfolioPreviewContent() }
}
