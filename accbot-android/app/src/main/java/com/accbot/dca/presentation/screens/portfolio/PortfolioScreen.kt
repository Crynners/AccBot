package com.accbot.dca.presentation.screens.portfolio

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.usecase.ChartZoomLevel
import com.accbot.dca.presentation.components.*
import com.accbot.dca.presentation.components.btcPriceColor
import com.accbot.dca.presentation.components.accumulatedCryptoColor
import com.accbot.dca.presentation.components.avgBuyPriceColor
import com.accbot.dca.presentation.ui.theme.Primary
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.NumberFormatters
import com.accbot.dca.presentation.utils.NumberFormatters.isPositiveRoi
import com.accbot.dca.presentation.utils.NumberFormatters.roiSign
import java.math.BigDecimal
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (crypto: String?, fiat: String?) -> Unit,
    onChartTouching: (Boolean) -> Unit = {},
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Refresh portfolio data when returning to screen (e.g. after transaction import)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshIfStale()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Landscape: two-pane layout – chart left, controls right
    if (isLandscape) {
        val chartData = uiState.chartData
        val hasAnyData = chartData.isNotEmpty()
        val hasData = chartData.size >= 2
        val currentPage = uiState.pages.getOrNull(uiState.selectedPageIndex)
        val isSinglePair = currentPage is PairPage.Plan

        // Scrub-to-inspect state
        var scrubbedIndex by remember { mutableIntStateOf(-1) }
        val scrubbedDataPoint = if (scrubbedIndex in chartData.indices) chartData[scrubbedIndex] else null

        val haptic = LocalHapticFeedback.current
        LaunchedEffect(scrubbedIndex) {
            onChartTouching(scrubbedIndex >= 0)
            if (scrubbedIndex >= 0) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        val pairLabel = when (currentPage) {
            is PairPage.Aggregate -> stringResource(R.string.chart_all_fiat, currentPage.fiat)
            is PairPage.Plan -> currentPage.name
            null -> ""
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left pane: Chart + Legend below
                Column(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxHeight()
                ) {
                    if (hasData) {
                        val unitSuffix = when (uiState.denominationMode) {
                            DenominationMode.FIAT -> uiState.currentPairFiat ?: "EUR"
                            DenominationMode.CRYPTO -> uiState.currentPairCrypto ?: "BTC"
                        }
                        val legendEntries = rememberLegendEntries(
                            denominationMode = uiState.denominationMode,
                            isSinglePair = isSinglePair,
                            currentPairCrypto = uiState.currentPairCrypto,
                            isAggregate = currentPage is PairPage.Aggregate
                        )
                        PortfolioLineChart(
                            chartData = chartData,
                            denominationMode = uiState.denominationMode,
                            unitSuffix = unitSuffix,
                            fiatSymbol = uiState.currentPairFiat ?: "EUR",
                            cryptoSymbol = uiState.currentPairCrypto ?: "",
                            visibleSeries = uiState.visibleSeries,
                            planLines = uiState.planLines,
                            visiblePlanLines = uiState.visiblePlanLines,
                            zoomLevel = uiState.zoomLevel,
                            onScrub = { idx -> scrubbedIndex = idx ?: -1 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        // Legend below chart
                        InteractiveChartLegend(
                            entries = legendEntries,
                            visibleSeries = uiState.visibleSeries,
                            onToggleSeries = { viewModel.toggleSeriesVisibility(it) },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        // Per-plan legend (landscape)
                        if (uiState.planLines.isNotEmpty()) {
                            PlanLinesLegend(
                                planLines = uiState.planLines,
                                visiblePlanLines = uiState.visiblePlanLines,
                                onToggle = { id, type -> viewModel.togglePlanLineVisibility(id, type) }
                            )
                        }

                        // Zoom header + drill-down chips
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Crossfade(targetState = uiState.zoomLevel, label = "zoom") { zoom ->
                                ChartZoomHeader(
                                    zoomLevel = zoom,
                                    canNavigatePrev = uiState.canNavigatePrev,
                                    canNavigateNext = uiState.canNavigateNext,
                                    onZoomOut = { viewModel.zoomOut() },
                                    onNavigatePrev = { viewModel.navigatePrev() },
                                    onNavigateNext = { viewModel.navigateNext() }
                                )
                            }
                            DrillDownChips(
                                zoomLevel = uiState.zoomLevel,
                                availableYears = uiState.availableYears,
                                availableMonths = uiState.availableMonths,
                                onDrillDownYear = { viewModel.drillDownToYear(it) },
                                onDrillDownMonth = { year, month -> viewModel.drillDownToMonth(year, month) }
                            )
                        }
                    }
                }

                // Right pane: Controls
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pair label with navigation
                    if (uiState.pages.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    val prev = (uiState.selectedPageIndex - 1 + uiState.pages.size) % uiState.pages.size
                                    viewModel.selectPairPage(prev)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.common_previous), modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = pairLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor()
                            )
                            IconButton(
                                onClick = {
                                    val next = (uiState.selectedPageIndex + 1) % uiState.pages.size
                                    viewModel.selectPairPage(next)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.common_next), modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        Text(
                            text = pairLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor()
                        )
                    }

                    // KPI section (compact vertical layout for landscape)
                    if (hasAnyData) {
                        LandscapeKpiContent(
                            uiState = uiState,
                            isSinglePair = isSinglePair,
                            scrubbedDataPoint = scrubbedDataPoint
                        )
                    }

                    // Plan chip row
                    if (uiState.pages.size > 1) {
                        PlanChipRow(
                            pages = uiState.pages,
                            selectedIndex = uiState.selectedPageIndex,
                            onPageSelected = { viewModel.selectPairPage(it) }
                        )
                    }
                }
            }
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.portfolio_title),
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
                        onRetry = { viewModel.forceRefresh() }
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
                    onPairPageSelected = { viewModel.selectPairPage(it) },
                    onToggleSeriesVisibility = { viewModel.toggleSeriesVisibility(it) },
                    onTogglePlanLineVisibility = { id, type -> viewModel.togglePlanLineVisibility(id, type) },
                    onRefresh = { viewModel.syncPricesAndLoadChart() },
                    onChartTouching = onChartTouching,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortfolioContent(
    uiState: PortfolioUiState,
    onDrillDownYear: (Int) -> Unit,
    onDrillDownMonth: (Int, Int) -> Unit,
    onZoomOut: () -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onPairPageSelected: (Int) -> Unit,
    onToggleSeriesVisibility: (Int) -> Unit,
    onTogglePlanLineVisibility: (Long, PlanLineType) -> Unit,
    onRefresh: () -> Unit,
    onChartTouching: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chartData = uiState.chartData
    val hasAnyData = chartData.isNotEmpty()
    val hasData = chartData.size >= 2

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
    val isSinglePair = currentPage is PairPage.Plan

    // Scrub-to-inspect state (ephemeral, local to composable)
    var scrubbedIndex by remember { mutableIntStateOf(-1) }
    val scrubbedDataPoint = if (scrubbedIndex in uiState.chartData.indices) uiState.chartData[scrubbedIndex] else null

    // Haptic feedback on scrub position change
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(scrubbedIndex) {
        onChartTouching(scrubbedIndex >= 0)
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
                            userScrollEnabled = pageCount > 1,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            val pageItem = uiState.pages.getOrNull(page)
                            val pairLabel = when (pageItem) {
                                is PairPage.Aggregate -> stringResource(R.string.chart_all_fiat, pageItem.fiat)
                                is PairPage.Plan -> pageItem.name
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
                                if (hasAnyData) {
                                    Spacer(Modifier.height(8.dp))
                                    KpiCardContent(
                                        uiState = uiState,
                                        isSinglePair = pageItem is PairPage.Plan,
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
            // Single page (no pager needed) – still show label + KPI
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
                            is PairPage.Plan -> pageItem.name
                            null -> ""
                        }
                        Text(
                            text = pairLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor()
                        )
                        if (hasAnyData) {
                            Spacer(Modifier.height(8.dp))
                            KpiCardContent(
                                uiState = uiState,
                                isSinglePair = pageItem is PairPage.Plan,
                                scrubbedDataPoint = scrubbedDataPoint
                            )
                        }
                    }
                }
            }
        }

        // Chart
        item {
            if (uiState.isChartLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
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
                    fiatSymbol = uiState.currentPairFiat ?: "EUR",
                    cryptoSymbol = uiState.currentPairCrypto ?: "",
                    visibleSeries = uiState.visibleSeries,
                    planLines = uiState.planLines,
                    visiblePlanLines = uiState.visiblePlanLines,
                    zoomLevel = uiState.zoomLevel,
                    onScrub = { idx -> scrubbedIndex = idx ?: -1 },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (chartData.size == 1) {
                EmptyState(
                    icon = Icons.Default.ShowChart,
                    title = stringResource(R.string.chart_no_data_title),
                    description = stringResource(R.string.chart_insufficient_data_desc)
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
                val legendEntries = rememberLegendEntries(
                    denominationMode = uiState.denominationMode,
                    isSinglePair = isSinglePair,
                    currentPairCrypto = uiState.currentPairCrypto,
                    isAggregate = currentPage is PairPage.Aggregate
                )
                InteractiveChartLegend(
                    entries = legendEntries,
                    visibleSeries = uiState.visibleSeries,
                    onToggleSeries = onToggleSeriesVisibility
                )
                // Per-plan legend entries
                if (uiState.planLines.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    PlanLinesLegend(
                        planLines = uiState.planLines,
                        visiblePlanLines = uiState.visiblePlanLines,
                        onToggle = onTogglePlanLineVisibility
                    )
                }
            }
        }

        // Zoom header (back arrow + prev/next navigation)
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

        // Drill-down chips (explore history)
        item {
            DrillDownChips(
                zoomLevel = uiState.zoomLevel,
                availableYears = uiState.availableYears,
                availableMonths = uiState.availableMonths,
                onDrillDownYear = onDrillDownYear,
                onDrillDownMonth = onDrillDownMonth
            )
        }

        // Plan chip row (replaces exchange filter)
        if (uiState.pages.size > 1) {
            item {
                PlanChipRow(
                    pages = uiState.pages,
                    selectedIndex = uiState.selectedPageIndex,
                    onPageSelected = onPairPageSelected
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
private fun rememberLegendEntries(
    denominationMode: DenominationMode,
    isSinglePair: Boolean,
    currentPairCrypto: String?,
    isAggregate: Boolean = false
): List<LegendEntry> {
    val line1 = when {
        isAggregate && denominationMode == DenominationMode.FIAT -> stringResource(R.string.chart_total_value)
        denominationMode == DenominationMode.FIAT -> stringResource(R.string.chart_portfolio_value)
        else -> stringResource(R.string.chart_legend_crypto_held)
    }
    val line2 = when {
        isAggregate && denominationMode == DenominationMode.FIAT -> stringResource(R.string.chart_total_invested)
        denominationMode == DenominationMode.FIAT -> stringResource(R.string.chart_cost_basis)
        else -> stringResource(R.string.chart_legend_invested_equiv)
    }
    val crypto = currentPairCrypto ?: "BTC"
    val cryptoPriceLabel = stringResource(R.string.chart_crypto_price, crypto)
    val accumulatedCryptoLabel = stringResource(R.string.chart_accumulated_crypto, crypto)
    val avgBuyPriceLabel = stringResource(R.string.chart_avg_buy_price)
    return remember(denominationMode, isSinglePair, currentPairCrypto, isAggregate, line1, line2) {
        buildList {
            add(LegendEntry(0, line1, Primary))
            add(LegendEntry(1, line2, androidx.compose.ui.graphics.Color(0xFF888888)))
            if (isSinglePair && denominationMode == DenominationMode.FIAT) {
                add(LegendEntry(2, cryptoPriceLabel, btcPriceColor))
                add(LegendEntry(4, avgBuyPriceLabel, avgBuyPriceColor))
                add(LegendEntry(3, accumulatedCryptoLabel, accumulatedCryptoColor))
            }
        }
    }
}

@Composable
internal fun ChartZoomHeader(
    zoomLevel: ChartZoomLevel,
    canNavigatePrev: Boolean,
    canNavigateNext: Boolean,
    onZoomOut: () -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit
) {
    when (zoomLevel) {
        is ChartZoomLevel.Overview -> {
            // No header needed – drill-down chips show "Explore history" label
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
                        contentDescription = stringResource(R.string.chart_zoom_all_time),
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
                        contentDescription = stringResource(R.string.common_previous),
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
                        contentDescription = stringResource(R.string.common_next),
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
                        contentDescription = stringResource(R.string.common_back),
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
                        contentDescription = stringResource(R.string.common_previous),
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
                        contentDescription = stringResource(R.string.common_next),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DrillDownChips(
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

/**
 * Calculates the period label and ROI percentage for the current zoom level.
 */
private fun calculatePeriodRoi(uiState: PortfolioUiState): Pair<String?, BigDecimal?> {
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
    return periodLabel to periodRoi
}

@Composable
internal fun KpiCardContent(
    uiState: PortfolioUiState,
    isSinglePair: Boolean,
    scrubbedDataPoint: com.accbot.dca.domain.usecase.ChartDataPoint? = null
) {
    val displayPoint = scrubbedDataPoint ?: uiState.chartData.lastOrNull() ?: return
    val latest = displayPoint
    val isScrubbing = scrubbedDataPoint != null
    val isPositive = isPositiveRoi(latest.roiAbsolute)
    val roiColor = if (isPositive) successColor() else MaterialTheme.colorScheme.error
    val sign = roiSign(latest.roiAbsolute)
    val fiatSymbol = uiState.currentPairFiat ?: "EUR"

    val (periodLabel, periodRoi) = remember(uiState.zoomLevel, uiState.chartData) {
        calculatePeriodRoi(uiState)
    }

    // Scrub date indicator
    if (isScrubbing) {
        Text(
            text = if (latest.epochMillis != null) {
                java.time.Instant.ofEpochMilli(latest.epochMillis)
                    .let { com.accbot.dca.presentation.utils.DateFormatters.shortDateTime.format(it) }
            } else {
                LocalDate.ofEpochDay(latest.epochDay)
                    .format(com.accbot.dca.presentation.utils.DateFormatters.shortDate)
            },
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
                val periodSign = roiSign(periodRoi)
                val periodColor = if (isPositiveRoi(periodRoi)) successColor() else MaterialTheme.colorScheme.error
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

    // Row 2: Invested | Avg Buy Price (single pair) or just Invested (aggregate)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
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
        if (isSinglePair) {
            val crypto = uiState.currentPairCrypto ?: "BTC"
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.chart_avg_price),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${NumberFormatters.fiat(latest.avgBuyPrice)} $fiatSymbol/$crypto",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Row 3: Crypto Price | Accumulated Crypto (single pair only)
    if (isSinglePair) {
        val crypto = uiState.currentPairCrypto ?: "BTC"

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.chart_crypto_price, crypto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${NumberFormatters.fiat(latest.price)} $fiatSymbol",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.chart_accumulated_crypto, crypto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${NumberFormatters.crypto(latest.cumulativeCrypto)} $crypto",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LandscapeKpiContent(
    uiState: PortfolioUiState,
    isSinglePair: Boolean,
    scrubbedDataPoint: com.accbot.dca.domain.usecase.ChartDataPoint? = null
) {
    val displayPoint = scrubbedDataPoint ?: uiState.chartData.lastOrNull() ?: return
    val isScrubbing = scrubbedDataPoint != null
    val isPositive = isPositiveRoi(displayPoint.roiAbsolute)
    val roiColor = if (isPositive) successColor() else MaterialTheme.colorScheme.error
    val sign = roiSign(displayPoint.roiAbsolute)
    val fiatSymbol = uiState.currentPairFiat ?: "EUR"

    val (periodLabel, periodRoi) = remember(uiState.zoomLevel, uiState.chartData) {
        calculatePeriodRoi(uiState)
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Scrub date
        if (isScrubbing) {
            Text(
                text = if (displayPoint.epochMillis != null) {
                    java.time.Instant.ofEpochMilli(displayPoint.epochMillis)
                        .let { com.accbot.dca.presentation.utils.DateFormatters.shortDateTime.format(it) }
                } else {
                    LocalDate.ofEpochDay(displayPoint.epochDay)
                        .format(com.accbot.dca.presentation.utils.DateFormatters.shortDate)
                },
                style = MaterialTheme.typography.labelMedium,
                color = accentColor(),
                fontWeight = FontWeight.SemiBold
            )
        }

        // Portfolio value
        Text(
            text = stringResource(R.string.chart_portfolio_value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${NumberFormatters.fiat(displayPoint.portfolioValue)} $fiatSymbol",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // ROI
        Text(
            text = stringResource(R.string.chart_total_roi),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$sign${NumberFormatters.percent(displayPoint.roiPercent)}%  ($sign${NumberFormatters.fiat(displayPoint.roiAbsolute)} $fiatSymbol)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = roiColor
        )

        // Period ROI
        if (periodRoi != null && periodLabel != null) {
            val periodSign = roiSign(periodRoi)
            val periodColor = if (isPositiveRoi(periodRoi)) successColor() else MaterialTheme.colorScheme.error
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

        // Invested, Avg price, Crypto Price, Accumulated (single pair) or just Invested (aggregate)
        if (isSinglePair) {
            val crypto = uiState.currentPairCrypto ?: "BTC"

            // Invested
            Text(
                text = stringResource(R.string.chart_invested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${NumberFormatters.fiat(displayPoint.totalInvested)} $fiatSymbol",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Avg price
            Text(
                text = stringResource(R.string.chart_avg_price),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${NumberFormatters.fiat(displayPoint.avgBuyPrice)} $fiatSymbol/$crypto",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Crypto Price
            Text(
                text = stringResource(R.string.chart_crypto_price, crypto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${NumberFormatters.fiat(displayPoint.price)} $fiatSymbol",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Accumulated Crypto
            Text(
                text = stringResource(R.string.chart_accumulated_crypto, crypto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${NumberFormatters.crypto(displayPoint.cumulativeCrypto)} $crypto",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = stringResource(R.string.chart_invested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${NumberFormatters.fiat(displayPoint.totalInvested)} $fiatSymbol",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Transactions
        Text(
            text = "${stringResource(R.string.chart_transactions)}: ${uiState.totalTransactions}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanLinesLegend(
    planLines: List<PlanLineInfo>,
    visiblePlanLines: Set<Pair<Long, PlanLineType>>,
    onToggle: (Long, PlanLineType) -> Unit
) {
    val planLineColors = com.accbot.dca.presentation.components.planLineColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        planLines.forEachIndexed { index, planLine ->
            val baseColor = planLineColors[index % planLineColors.size]
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Value entry
                val valueEnabled = (planLine.planId to PlanLineType.VALUE) in visiblePlanLines
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onToggle(planLine.planId, PlanLineType.VALUE) }
                        .padding(4.dp)
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (valueEnabled) baseColor else baseColor.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.chart_plan_value, planLine.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (valueEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textDecoration = if (valueEnabled) null else TextDecoration.LineThrough
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Invested entry
                val investedEnabled = (planLine.planId to PlanLineType.INVESTED) in visiblePlanLines
                val investedColor = baseColor.copy(alpha = 0.4f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onToggle(planLine.planId, PlanLineType.INVESTED) }
                        .padding(4.dp)
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (investedEnabled) investedColor else investedColor.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.chart_plan_invested, planLine.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (investedEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textDecoration = if (investedEnabled) null else TextDecoration.LineThrough
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanChipRow(
    pages: List<PairPage>,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(pages) { index, page ->
            val label = when (page) {
                is PairPage.Aggregate -> stringResource(R.string.chart_all_fiat, page.fiat)
                is PairPage.Plan -> page.name
            }
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onPageSelected(index) },
                label = { Text(label) }
            )
        }
    }
}
