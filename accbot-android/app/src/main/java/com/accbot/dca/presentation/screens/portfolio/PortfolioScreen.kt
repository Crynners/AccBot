package com.accbot.dca.presentation.screens.portfolio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.hilt.navigation.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.usecase.ChartZoomLevel
import com.accbot.dca.presentation.components.*
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.NumberFormatters
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (crypto: String?, fiat: String?) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Landscape: fullscreen chart with auto-hiding overlay
    if (isLandscape) {
        val chartData = uiState.chartData
        val hasData = chartData.isNotEmpty()
        var overlayVisible by remember { mutableStateOf(true) }
        var overlayTrigger by remember { mutableIntStateOf(0) }

        // Auto-hide overlay after 3 seconds
        LaunchedEffect(overlayTrigger) {
            if (overlayVisible) {
                delay(3000)
                overlayVisible = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable {
                    overlayVisible = !overlayVisible
                    if (overlayVisible) overlayTrigger++
                }
        ) {
            if (hasData) {
                val unitSuffix = when (uiState.denominationMode) {
                    DenominationMode.FIAT -> uiState.currentPairFiat ?: "EUR"
                    DenominationMode.CRYPTO -> uiState.currentPairCrypto ?: "BTC"
                }
                PortfolioLineChart(
                    chartData = chartData,
                    denominationMode = uiState.denominationMode,
                    unitSuffix = unitSuffix,
                    visibleSeries = uiState.visibleSeries,
                    zoomLevel = uiState.zoomLevel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Semi-transparent overlay bar
            AnimatedVisibility(
                visible = overlayVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }

                    // Zoom level label
                    val zoomLabel = when (val z = uiState.zoomLevel) {
                        is ChartZoomLevel.Overview -> stringResource(R.string.chart_zoom_all_time)
                        is ChartZoomLevel.Year -> "${z.year}"
                        is ChartZoomLevel.Month -> {
                            val mn = Month.of(z.month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            "$mn ${z.year}"
                        }
                    }
                    Text(
                        text = zoomLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Prev/next navigation
                    if (uiState.zoomLevel !is ChartZoomLevel.Overview) {
                        IconButton(
                            onClick = { viewModel.navigatePrev() },
                            enabled = uiState.canNavigatePrev
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null)
                        }
                        IconButton(
                            onClick = { viewModel.navigateNext() },
                            enabled = uiState.canNavigateNext
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // KPI summary
                    if (hasData) {
                        val latest = chartData.last()
                        val fiat = uiState.currentPairFiat ?: "EUR"
                        val isPositive = latest.roiAbsolute >= BigDecimal.ZERO
                        val roiSign = if (isPositive) "+" else ""
                        val roiColor = if (isPositive) successColor() else MaterialTheme.colorScheme.error
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${NumberFormatters.fiat(latest.portfolioValue)} $fiat",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$roiSign${NumberFormatters.percent(latest.roiPercent)}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = roiColor
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.portfolio_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = {
                        onNavigateToHistory(uiState.currentPairCrypto, uiState.currentPairFiat)
                    }) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.dashboard_history))
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingState(message = stringResource(R.string.portfolio_loading))
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.refresh() }
                    )
                }
            }
            else -> {
                PortfolioContent(
                    uiState = uiState,
                    onDrillDownYear = { viewModel.drillDownToYear(it) },
                    onDrillDownMonth = { year, month -> viewModel.drillDownToMonth(year, month) },
                    onZoomOut = { viewModel.zoomOut() },
                    onNavigatePrev = { viewModel.navigatePrev() },
                    onNavigateNext = { viewModel.navigateNext() },
                    onExchangeFilterSelected = { viewModel.selectExchangeFilter(it) },
                    onPairPageSelected = { viewModel.selectPairPage(it) },
                    onToggleDenomination = { viewModel.toggleDenomination() },
                    onToggleSeriesVisibility = { viewModel.toggleSeriesVisibility(it) },
                    onRefresh = { viewModel.syncPricesAndLoadChart() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortfolioContent(
    uiState: PortfolioUiState,
    onDrillDownYear: (Int) -> Unit,
    onDrillDownMonth: (Int, Int) -> Unit,
    onZoomOut: () -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onExchangeFilterSelected: (String?) -> Unit,
    onPairPageSelected: (Int) -> Unit,
    onToggleDenomination: () -> Unit,
    onToggleSeriesVisibility: (Int) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chartData = uiState.chartData
    val hasData = chartData.isNotEmpty()

    val pageCount = uiState.pages.size
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedPageIndex,
        pageCount = { pageCount }
    )

    // Sync pager with ViewModel
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.selectedPageIndex) {
            onPairPageSelected(pagerState.currentPage)
        }
    }

    val currentPage = uiState.pages.getOrNull(uiState.selectedPageIndex)
    val isSinglePair = currentPage is PairPage.SinglePair

    // Scrub-to-inspect state (ephemeral, local to composable)
    var scrubbedIndex by remember { mutableIntStateOf(-1) }
    val scrubbedDataPoint = if (scrubbedIndex in uiState.chartData.indices) uiState.chartData[scrubbedIndex] else null

    // Haptic feedback on scrub position change
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(scrubbedIndex) {
        if (scrubbedIndex >= 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isPriceSyncing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Syncing indicator
        if (uiState.isPriceSyncing) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accentColor()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.chart_syncing_prices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Pair pager with KPI (swipe together) + dots
        if (pageCount > 1) {
            item {
                Card(
                    modifier = Modifier.animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            val pageItem = uiState.pages.getOrNull(page)
                            val pairLabel = when (pageItem) {
                                is PairPage.Aggregate -> stringResource(R.string.chart_all_fiat, pageItem.fiat)
                                is PairPage.SinglePair -> "${pageItem.crypto}/${pageItem.fiat}"
                                null -> ""
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = pairLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor()
                                )
                                if (hasData) {
                                    Spacer(Modifier.height(8.dp))
                                    KpiCardContent(
                                        uiState = uiState,
                                        isSinglePair = pageItem is PairPage.SinglePair,
                                        scrubbedDataPoint = scrubbedDataPoint
                                    )
                                }
                            }
                        }

                        // Page indicator dots
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(pageCount) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (isSelected) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) accentColor()
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        } else if (pageCount == 1) {
            // Single page (no pager needed) — still show label + KPI
            item {
                Card(
                    modifier = Modifier.animateContentSize(),
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
                        val pageItem = uiState.pages.firstOrNull()
                        val pairLabel = when (pageItem) {
                            is PairPage.Aggregate -> stringResource(R.string.chart_all_fiat, pageItem.fiat)
                            is PairPage.SinglePair -> "${pageItem.crypto}/${pageItem.fiat}"
                            null -> ""
                        }
                        Text(
                            text = pairLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor()
                        )
                        if (hasData) {
                            Spacer(Modifier.height(8.dp))
                            KpiCardContent(
                                uiState = uiState,
                                isSinglePair = pageItem is PairPage.SinglePair
                            )
                        }
                    }
                }
            }
        }

        // Denomination toggle (only for single pair)
        if (isSinglePair && hasData) {
            item {
                DenominationToggle(
                    denominationMode = uiState.denominationMode,
                    cryptoSymbol = uiState.currentPairCrypto ?: "",
                    fiatSymbol = uiState.currentPairFiat ?: "",
                    onToggle = onToggleDenomination
                )
            }
        }

        // Zoom header (navigation controls) with crossfade animation
        item {
            Crossfade(targetState = uiState.zoomLevel, label = "zoom") { zoom ->
                ChartZoomHeader(
                    zoomLevel = zoom,
                    canNavigatePrev = uiState.canNavigatePrev,
                    canNavigateNext = uiState.canNavigateNext,
                    onZoomOut = onZoomOut,
                    onNavigatePrev = onNavigatePrev,
                    onNavigateNext = onNavigateNext
                )
            }
        }

        // Drill-down chips (above chart so user sees result without scrolling)
        item {
            DrillDownChips(
                zoomLevel = uiState.zoomLevel,
                availableYears = uiState.availableYears,
                availableMonths = uiState.availableMonths,
                onDrillDownYear = onDrillDownYear,
                onDrillDownMonth = onDrillDownMonth
            )
        }

        // Chart
        item {
            if (uiState.isChartLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor())
                }
            } else if (hasData) {
                val unitSuffix = when (uiState.denominationMode) {
                    DenominationMode.FIAT -> uiState.currentPairFiat ?: "EUR"
                    DenominationMode.CRYPTO -> uiState.currentPairCrypto ?: "BTC"
                }
                PortfolioLineChart(
                    chartData = chartData,
                    denominationMode = uiState.denominationMode,
                    unitSuffix = unitSuffix,
                    visibleSeries = uiState.visibleSeries,
                    zoomLevel = uiState.zoomLevel,
                    onScrub = { idx -> scrubbedIndex = idx ?: -1 },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (!uiState.isPriceSyncing) {
                EmptyState(
                    icon = Icons.Default.ShowChart,
                    title = stringResource(R.string.chart_no_data_title),
                    description = stringResource(R.string.chart_no_data_desc)
                )
            }
        }

        // Interactive chart legend
        if (hasData) {
            item {
                val (line1, line2) = when (uiState.denominationMode) {
                    DenominationMode.FIAT -> stringResource(R.string.chart_portfolio_value) to stringResource(R.string.chart_cost_basis)
                    DenominationMode.CRYPTO -> stringResource(R.string.chart_legend_crypto_held) to stringResource(R.string.chart_legend_invested_equiv)
                }
                InteractiveChartLegend(
                    line1Label = line1,
                    line2Label = line2,
                    visibleSeries = uiState.visibleSeries,
                    onToggleSeries = onToggleSeriesVisibility
                )
            }
        }

        // Exchange filter chips
        if (uiState.availableExchanges.size > 1) {
            item {
                ExchangeFilterRow(
                    exchanges = uiState.availableExchanges,
                    selectedExchange = uiState.selectedExchangeFilter,
                    onExchangeSelected = onExchangeFilterSelected
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    } // PullToRefreshBox
}

@Composable
private fun ChartZoomHeader(
    zoomLevel: ChartZoomLevel,
    canNavigatePrev: Boolean,
    canNavigateNext: Boolean,
    onZoomOut: () -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit
) {
    when (zoomLevel) {
        is ChartZoomLevel.Overview -> {
            // Centered "All Time" label
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.chart_zoom_all_time),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        is ChartZoomLevel.Year -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom-out link
                Row(
                    modifier = Modifier
                        .clickable(onClick = onZoomOut)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor()
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.chart_zoom_all_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor()
                    )
                }

                Spacer(Modifier.weight(1f))

                // Prev/label/next
                IconButton(
                    onClick = onNavigatePrev,
                    enabled = canNavigatePrev,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "${zoomLevel.year}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 56.dp)
                )

                IconButton(
                    onClick = onNavigateNext,
                    enabled = canNavigateNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        is ChartZoomLevel.Month -> {
            val monthName = Month.of(zoomLevel.month)
                .getDisplayName(TextStyle.FULL, Locale.getDefault())
            val label = "$monthName ${zoomLevel.year}"

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom-out link (back to year)
                Row(
                    modifier = Modifier
                        .clickable(onClick = onZoomOut)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor()
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${zoomLevel.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor()
                    )
                }

                Spacer(Modifier.weight(1f))

                // Prev/label/next
                IconButton(
                    onClick = onNavigatePrev,
                    enabled = canNavigatePrev,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 100.dp)
                )

                IconButton(
                    onClick = onNavigateNext,
                    enabled = canNavigateNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrillDownChips(
    zoomLevel: ChartZoomLevel,
    availableYears: List<Int>,
    availableMonths: List<Int>,
    onDrillDownYear: (Int) -> Unit,
    onDrillDownMonth: (Int, Int) -> Unit
) {
    val accent = accentColor()
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = accent.copy(alpha = 0.15f),
        labelColor = accent
    )
    val chipBorder = FilterChipDefaults.filterChipBorder(
        borderColor = accent.copy(alpha = 0.3f),
        enabled = true,
        selected = false
    )

    when (zoomLevel) {
        is ChartZoomLevel.Overview -> {
            if (availableYears.isNotEmpty()) {
                Column {
                    Text(
                        text = stringResource(R.string.chart_explore),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableYears) { year ->
                            FilterChip(
                                selected = false,
                                onClick = { onDrillDownYear(year) },
                                label = { Text("$year") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = accent
                                    )
                                },
                                colors = chipColors,
                                border = chipBorder
                            )
                        }
                    }
                }
            }
        }
        is ChartZoomLevel.Year -> {
            if (availableMonths.isNotEmpty()) {
                Column {
                    Text(
                        text = stringResource(R.string.chart_explore),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableMonths) { month ->
                            val shortName = Month.of(month)
                                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            FilterChip(
                                selected = false,
                                onClick = { onDrillDownMonth(zoomLevel.year, month) },
                                label = { Text(shortName) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = accent
                                    )
                                },
                                colors = chipColors,
                                border = chipBorder
                            )
                        }
                    }
                }
            }
        }
        is ChartZoomLevel.Month -> {
            // No deeper drill-down at day level
        }
    }
}

@Composable
private fun KpiCardContent(
    uiState: PortfolioUiState,
    isSinglePair: Boolean,
    scrubbedDataPoint: com.accbot.dca.domain.usecase.ChartDataPoint? = null
) {
    val displayPoint = scrubbedDataPoint ?: uiState.chartData.lastOrNull() ?: return
    val latest = displayPoint
    val isScrubbing = scrubbedDataPoint != null
    val isPositive = latest.roiAbsolute >= BigDecimal.ZERO
    val roiColor = if (isPositive) successColor() else MaterialTheme.colorScheme.error
    val sign = if (isPositive) "+" else ""
    val fiatSymbol = uiState.currentPairFiat ?: "EUR"

    // Period ROI (only when zoomed into Year or Month)
    val periodLabel = when (val zoom = uiState.zoomLevel) {
        is ChartZoomLevel.Year -> "${zoom.year}"
        is ChartZoomLevel.Month -> {
            val monthName = Month.of(zoom.month)
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "$monthName ${zoom.year}"
        }
        else -> null
    }
    val periodRoi = if (periodLabel != null && uiState.chartData.size >= 2) {
        val first = uiState.chartData.first()
        val last = uiState.chartData.last()
        val startValue = first.portfolioValue
        val endValue = last.portfolioValue
        if (startValue > BigDecimal.ZERO) {
            endValue.subtract(startValue)
                .multiply(BigDecimal(100))
                .divide(startValue, 2, java.math.RoundingMode.HALF_UP)
        } else null
    } else null

    // Scrub date indicator
    if (isScrubbing) {
        Text(
            text = LocalDate.ofEpochDay(latest.epochDay)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy")),
            style = MaterialTheme.typography.labelMedium,
            color = accentColor(),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
    }

    // Row 1: Portfolio Value | ROI
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.chart_portfolio_value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${NumberFormatters.fiat(latest.portfolioValue)} $fiatSymbol",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.chart_total_roi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$sign${NumberFormatters.percent(latest.roiPercent)}%",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = roiColor
            )
            Text(
                text = "$sign${NumberFormatters.fiat(latest.roiAbsolute)} $fiatSymbol",
                style = MaterialTheme.typography.bodySmall,
                color = roiColor
            )
            // Period ROI below all-time ROI
            if (periodRoi != null && periodLabel != null) {
                val periodPositive = periodRoi >= BigDecimal.ZERO
                val periodSign = if (periodPositive) "+" else ""
                val periodColor = if (periodPositive) successColor() else MaterialTheme.colorScheme.error
                Text(
                    text = stringResource(
                        R.string.chart_period_roi,
                        "$periodSign${NumberFormatters.percent(periodRoi)}%",
                        periodLabel
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = periodColor
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Row 2: Avg Buy Price (single pair only) | Transactions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (isSinglePair) {
            Column {
                Text(
                    text = stringResource(R.string.chart_avg_price),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${NumberFormatters.fiat(latest.avgBuyPrice)} $fiatSymbol",
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Column {
                Text(
                    text = stringResource(R.string.chart_invested),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${NumberFormatters.fiat(latest.totalInvested)} $fiatSymbol",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.chart_transactions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${uiState.totalTransactions}",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DenominationToggle(
    denominationMode: DenominationMode,
    cryptoSymbol: String,
    fiatSymbol: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = denominationMode == DenominationMode.FIAT,
            onClick = { if (denominationMode != DenominationMode.FIAT) onToggle() },
            label = { Text(fiatSymbol) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = denominationMode == DenominationMode.CRYPTO,
            onClick = { if (denominationMode != DenominationMode.CRYPTO) onToggle() },
            label = { Text(cryptoSymbol) }
        )
    }
}

@Composable
private fun ExchangeFilterRow(
    exchanges: List<String>,
    selectedExchange: String?,
    onExchangeSelected: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedExchange == null,
                onClick = { onExchangeSelected(null) },
                label = { Text(stringResource(R.string.chart_filter_all_exchanges)) }
            )
        }
        items(exchanges) { exchange ->
            FilterChip(
                selected = exchange == selectedExchange,
                onClick = { onExchangeSelected(exchange) },
                label = { Text(exchange) }
            )
        }
    }
}
