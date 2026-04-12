package com.accbot.dca.presentation.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.data.remote.CryptoData
import com.accbot.dca.data.remote.FearGreedData
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.presentation.components.CryptoIcon
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.components.MarketPulseInfoSheet
import com.accbot.dca.presentation.components.SectionHeader
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.TimeUtils
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.delay

/**
 * State holder for drag-to-reorder in a LazyColumn of plan cards.
 * Long-press on a card activates drag mode; dragging past the midpoint
 * of an adjacent item triggers a swap.
 */
private class PlanDragState(
    private val onReorder: (Int, Int) -> Unit
) {
    var draggedIndex by mutableIntStateOf(-1)
    var dragOffset by mutableFloatStateOf(0f)
    private var accumulatedOffset = 0f
    private var itemHeight = 0

    fun startDrag(index: Int, heightPx: Int) {
        draggedIndex = index
        dragOffset = 0f
        accumulatedOffset = 0f
        itemHeight = heightPx
    }

    fun drag(delta: Float, totalItems: Int) {
        if (draggedIndex < 0 || itemHeight == 0) return
        accumulatedOffset += delta
        dragOffset = accumulatedOffset

        // Swap when dragged past midpoint of adjacent item
        val threshold = itemHeight * 0.5f
        if (accumulatedOffset > threshold && draggedIndex < totalItems - 1) {
            onReorder(draggedIndex, draggedIndex + 1)
            draggedIndex += 1
            accumulatedOffset -= itemHeight
            dragOffset = accumulatedOffset
        } else if (accumulatedOffset < -threshold && draggedIndex > 0) {
            onReorder(draggedIndex, draggedIndex - 1)
            draggedIndex -= 1
            accumulatedOffset += itemHeight
            dragOffset = accumulatedOffset
        }
    }

    fun endDrag() {
        draggedIndex = -1
        dragOffset = 0f
        accumulatedOffset = 0f
    }
}

@Composable
private fun rememberPlanDragState(
    onReorder: (from: Int, to: Int) -> Unit
): PlanDragState {
    return remember { PlanDragState(onReorder) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToPlans: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlanDetails: ((Long) -> Unit)? = null,
    onNavigateToPortfolio: ((String, String) -> Unit)? = null,
    onNavigateToExchangeManagement: (() -> Unit)? = null,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Shared ticker for all DcaPlanCard countdown texts
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    // Re-read preferences (e.g. Market Pulse toggle) when returning from Settings
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

    LaunchedEffect(uiState.runNowTriggered) {
        if (uiState.runNowTriggered) {
            snackbarHostState.showSnackbar(context.getString(R.string.dashboard_dca_triggered))
            viewModel.clearRunNowTriggered()
        }
    }

    if (uiState.showRunNowSheet) {
        RunNowBottomSheet(
            plans = uiState.activePlans,
            onDismiss = { viewModel.hideRunNowSheet() },
            onConfirm = { viewModel.runSelectedPlans(it) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { AccBotHeaderLogo() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isPriceLoading,
            onRefresh = { viewModel.refreshAll() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        if (isLandscape) {
            // Landscape: two-column layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left column: Holdings + Quick Actions
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.isSandboxMode) {
                        SandboxBanner()
                    }

                    if (uiState.networkRetryInfo.plans.isNotEmpty() && !uiState.networkRetryInfo.dismissed) {
                        NetworkRetryBanner(
                            retryPlans = uiState.networkRetryInfo.plans,
                            onRunNow = { viewModel.runRetryPlans() },
                            onDismiss = { viewModel.dismissRetryBanner() }
                        )
                    }

                    if (uiState.missedPurchases.isNotEmpty()) {
                        MissedPurchasesBanner(
                            missed = uiState.missedPurchases,
                            onExecute = { planId, count -> viewModel.executeMissedPurchases(planId, count) },
                            onDismiss = { planId -> viewModel.dismissMissedPurchases(planId) }
                        )
                    }

                    if (uiState.holdings.size >= 2) {
                        PortfolioSummaryCard(holdings = uiState.holdings)
                    }

                    HoldingsPager(
                        holdings = uiState.holdings,
                        onHoldingClick = onNavigateToPortfolio,
                        // Phase 7+: route to Exchange Management list rather than deep-linking
                        // into a specific connection. User picks the connection there.
                        onImportViaApi = onNavigateToExchangeManagement?.let { nav -> { nav() } },
                        compact = true
                    )

                    if (uiState.showMarketPulse && (uiState.fearGreedData != null || uiState.athDataByCrypto.isNotEmpty())) {
                        MarketPulseCard(
                            fearGreedData = uiState.fearGreedData,
                            athDataByCrypto = uiState.athDataByCrypto,
                            isExpanded = uiState.isMarketPulseExpanded,
                            onToggleExpand = { viewModel.toggleMarketPulseExpanded() }
                        )
                    }

                    QuickActionsRow(
                        onViewHistory = onNavigateToHistory,
                        onRunNow = { viewModel.showRunNowSheet() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Right column: DCA Plans
                val landscapeDragState = rememberPlanDragState { from, to ->
                    viewModel.reorderPlans(from, to)
                }
                LazyColumn(
                    modifier = Modifier.weight(0.5f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.dashboard_active_plans),
                            action = "+",
                            onAction = onNavigateToPlans
                        )
                    }

                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (uiState.activePlans.isEmpty()) {
                        item {
                            EmptyPlansCard(onAddPlan = onNavigateToPlans)
                        }
                    } else {
                        itemsIndexed(uiState.activePlans, key = { _, p -> p.plan.id }) { index, planWithBalance ->
                            DcaPlanCard(
                                planWithBalance = planWithBalance,
                                onToggle = { viewModel.togglePlan(planWithBalance.plan.id) },
                                onClick = { onNavigateToPlanDetails?.invoke(planWithBalance.plan.id) },
                                currentTime = currentTime,
                                isDragging = index == landscapeDragState.draggedIndex,
                                dragOffset = if (index == landscapeDragState.draggedIndex) landscapeDragState.dragOffset else 0f,
                                onDragStart = { heightPx -> landscapeDragState.startDrag(index, heightPx) },
                                onDrag = { delta -> landscapeDragState.drag(delta, uiState.activePlans.size) },
                                onDragEnd = { landscapeDragState.endDrag() }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        } else {
            // Portrait: single column
            val portraitDragState = rememberPlanDragState { from, to ->
                viewModel.reorderPlans(from, to)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sandbox Mode Banner
                if (uiState.isSandboxMode) {
                    item {
                        SandboxBanner()
                    }
                }

                // Network Retry Banner
                if (uiState.networkRetryInfo.plans.isNotEmpty() && !uiState.networkRetryInfo.dismissed) {
                    item {
                        NetworkRetryBanner(
                            retryPlans = uiState.networkRetryInfo.plans,
                            onRunNow = { viewModel.runRetryPlans() },
                            onDismiss = { viewModel.dismissRetryBanner() }
                        )
                    }
                }

                // Missed Purchases Banner
                if (uiState.missedPurchases.isNotEmpty()) {
                    item {
                        MissedPurchasesBanner(
                            missed = uiState.missedPurchases,
                            onExecute = { planId, count -> viewModel.executeMissedPurchases(planId, count) },
                            onDismiss = { planId -> viewModel.dismissMissedPurchases(planId) }
                        )
                    }
                }

                // Portfolio Summary (when 2+ holdings)
                if (uiState.holdings.size >= 2) {
                    item {
                        PortfolioSummaryCard(holdings = uiState.holdings)
                    }
                }

                // Holdings Pager
                item {
                    HoldingsPager(
                        holdings = uiState.holdings,
                        onHoldingClick = onNavigateToPortfolio,
                        // Phase 7+: route to Exchange Management list rather than deep-linking.
                        onImportViaApi = onNavigateToExchangeManagement?.let { nav -> { nav() } }
                    )
                }

                // Market Pulse
                if (uiState.showMarketPulse && (uiState.fearGreedData != null || uiState.athDataByCrypto.isNotEmpty())) {
                    item {
                        MarketPulseCard(
                            fearGreedData = uiState.fearGreedData,
                            athDataByCrypto = uiState.athDataByCrypto,
                            isExpanded = uiState.isMarketPulseExpanded,
                            onToggleExpand = { viewModel.toggleMarketPulseExpanded() }
                        )
                    }
                }

                // My DCA Plans
                item {
                    SectionHeader(
                        title = stringResource(R.string.dashboard_active_plans),
                        action = "+",
                        onAction = onNavigateToPlans
                    )
                }

                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (uiState.activePlans.isEmpty()) {
                    item {
                        EmptyPlansCard(onAddPlan = onNavigateToPlans)
                    }
                } else {
                    itemsIndexed(uiState.activePlans, key = { _, p -> p.plan.id }) { index, planWithBalance ->
                        DcaPlanCard(
                            planWithBalance = planWithBalance,
                            onToggle = { viewModel.togglePlan(planWithBalance.plan.id) },
                            onClick = { onNavigateToPlanDetails?.invoke(planWithBalance.plan.id) },
                            currentTime = currentTime,
                            isDragging = index == portraitDragState.draggedIndex,
                            dragOffset = if (index == portraitDragState.draggedIndex) portraitDragState.dragOffset else 0f,
                            onDragStart = { heightPx -> portraitDragState.startDrag(index, heightPx) },
                            onDrag = { delta -> portraitDragState.drag(delta, uiState.activePlans.size) },
                            onDragEnd = { portraitDragState.endDrag() }
                        )
                    }
                }

                // Quick Actions
                item {
                    QuickActionsRow(
                        onViewHistory = onNavigateToHistory,
                        onRunNow = { viewModel.showRunNowSheet() }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        } // PullToRefreshBox
    }
}

@Composable
internal fun AccBotHeaderLogo() {
    val textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val textColor = MaterialTheme.colorScheme.onBackground
    val density = LocalDensity.current
    val logoSizeDp = 36.dp
    val logoSizePx = with(density) { logoSizeDp.toPx() }.roundToInt()
    var btcCenter by remember { mutableStateOf(Offset.Zero) }

    Layout(
        content = {
            // Child 0: Logo image (drawn behind text)
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = if (btcCenter != Offset.Zero) 1f else 0f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Black,
                                    0.28f to Color.Black,
                                    0.40f to Color.Transparent,
                                    0.60f to Color.Transparent,
                                    0.72f to Color.Black,
                                    1.00f to Color.Black
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )
            // Child 1: Text (drives layout size)
            Text(
                text = "Acc\u20BFot",
                style = textStyle,
                color = textColor,
                onTextLayout = { result ->
                    val rect = result.getBoundingBox(3)
                    btcCenter = rect.center
                }
            )
        }
    ) { measurables, constraints ->
        val imagePlaceable = measurables[0].measure(
            Constraints.fixed(logoSizePx, logoSizePx)
        )
        val textPlaceable = measurables[1].measure(constraints)

        layout(textPlaceable.width, textPlaceable.height) {
            // Place logo centered on ₿ character, behind text
            imagePlaceable.placeRelative(
                x = (btcCenter.x - logoSizePx / 2f).roundToInt(),
                y = (btcCenter.y - logoSizePx / 2f).roundToInt()
            )
            // Place text on top
            textPlaceable.placeRelative(0, 0)
        }
    }
}

@Composable
internal fun SandboxBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Warning.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint = Warning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dashboard_sandbox_banner),
                fontWeight = FontWeight.SemiBold,
                color = Warning,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun MissedPurchasesBanner(
    missed: List<MissedPurchaseInfo>,
    onExecute: (planId: Long, count: Int) -> Unit,
    onDismiss: (planId: Long) -> Unit
) {
    if (missed.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for (info in missed) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Warning.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.notification_missed_purchases_banner,
                            info.missedCount,
                            info.crypto,
                            info.exchangeName
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = Warning,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedButton(onClick = { onDismiss(info.planId) }) {
                        Text(stringResource(R.string.missed_purchases_skip))
                    }
                    FilledTonalButton(
                        onClick = { onExecute(info.planId, info.missedCount) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Warning.copy(alpha = 0.2f),
                            contentColor = Warning
                        )
                    ) {
                        Text(stringResource(R.string.missed_purchases_buy))
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun NetworkRetryBanner(
    retryPlans: List<NetworkRetryPlan>,
    onRunNow: () -> Unit,
    onDismiss: () -> Unit
) {
    if (retryPlans.isEmpty()) return
    val context = LocalContext.current
    val bannerText = retryPlans.joinToString("\n") { plan ->
        context.getString(R.string.notification_network_retry_banner, plan.crypto, plan.exchangeName)
    }
    val attemptCount = retryPlans.sumOf { it.retryCount }
    val earliestRetry = retryPlans.mapNotNull { it.nextRetryAt }.minOrNull()
    val nextRetryText = earliestRetry?.let {
        val time = it.atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))
        context.getString(R.string.notification_network_retry_banner_next, time)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Error.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = bannerText,
                    fontWeight = FontWeight.SemiBold,
                    color = Error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column {
                    Text(
                        text = context.getString(R.string.notification_network_retry_banner_attempts, attemptCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (nextRetryText != null) {
                        Text(
                            text = nextRetryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onRunNow,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Error.copy(alpha = 0.2f),
                        contentColor = Error
                    )
                ) {
                    Text(stringResource(R.string.dashboard_run_now))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HoldingsPager(
    holdings: List<CryptoHoldingWithPrice>,
    onHoldingClick: ((String, String) -> Unit)? = null,
    onImportViaApi: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val successCol = successColor()

    if (holdings.isEmpty()) {
        // Empty state - show placeholder
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.dashboard_total_accumulated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_no_transactions),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_start_plan_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onImportViaApi != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onImportViaApi) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_api_title))
                    }
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { holdings.size })

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                val holding = holdings.getOrNull(pagerState.currentPage) ?: return@clickable
                onHoldingClick?.invoke(holding.crypto, holding.fiat)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.dashboard_total_accumulated),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp)
            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = holdings.size > 1,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val holding = holdings[page]
                HoldingPage(holding = holding, successCol = successCol, compact = compact)
            }

            // Page indicator dots
            if (holdings.size > 1) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(holdings.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isSelected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) successCol
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }

            // Transaction count for current page
            val currentHolding = holdings.getOrNull(pagerState.currentPage)
            if (currentHolding != null) {
                Text(
                    text = stringResource(R.string.dashboard_transactions_total, currentHolding.transactionCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
internal fun HoldingPage(
    holding: CryptoHoldingWithPrice,
    successCol: androidx.compose.ui.graphics.Color,
    compact: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Crypto amount
        Text(
            text = "${NumberFormatters.crypto(holding.totalCryptoAmount)} ${holding.crypto}",
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = successCol
        )

        Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

        if (compact) {
            // Compact: two columns, label: value stacked
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    StatItemInline(
                        label = stringResource(R.string.dashboard_invested),
                        value = "${NumberFormatters.fiat(holding.totalInvested)} ${holding.fiat}"
                    )
                    Spacer(Modifier.height(4.dp))
                    StatItemInline(
                        label = stringResource(R.string.dashboard_avg_price),
                        value = "${NumberFormatters.fiat(holding.averageBuyPrice)} ${holding.fiat}/${holding.crypto}"
                    )
                    Spacer(Modifier.height(4.dp))
                    if (holding.currentPrice != null) {
                        StatItemInline(
                            label = stringResource(R.string.dashboard_current_price),
                            value = "${NumberFormatters.fiat(holding.currentPrice)} ${holding.fiat}/${holding.crypto}"
                        )
                    } else {
                        StatItemInline(
                            label = stringResource(R.string.dashboard_current_price),
                            value = stringResource(R.string.dashboard_price_unavailable)
                        )
                    }
                }
                if (holding.roiAbsolute != null && holding.roiPercent != null) {
                    val isPositive = NumberFormatters.isPositiveRoi(holding.roiAbsolute)
                    val roiColor = if (isPositive) successCol else Error
                    val sign = NumberFormatters.roiSign(holding.roiAbsolute)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$sign${NumberFormatters.fiat(holding.roiAbsolute)} ${holding.fiat}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = roiColor
                        )
                        Text(
                            text = stringResource(R.string.dashboard_roi, "${sign}${NumberFormatters.percent(holding.roiPercent)}%"),
                            style = MaterialTheme.typography.bodySmall,
                            color = roiColor
                        )
                    }
                } else if (holding.currentPrice == null) {
                    Text(
                        text = "ROI –",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Normal: centered rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.dashboard_invested),
                    value = "${NumberFormatters.fiat(holding.totalInvested)} ${holding.fiat}"
                )
                StatItem(
                    label = stringResource(R.string.dashboard_avg_price),
                    value = "${NumberFormatters.fiat(holding.averageBuyPrice)} ${holding.fiat}/${holding.crypto}"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (holding.currentPrice != null) {
                    StatItem(
                        label = stringResource(R.string.dashboard_current_price),
                        value = "${NumberFormatters.fiat(holding.currentPrice)} ${holding.fiat}/${holding.crypto}"
                    )
                } else {
                    StatItem(
                        label = stringResource(R.string.dashboard_current_price),
                        value = stringResource(R.string.dashboard_price_unavailable)
                    )
                }
                if (holding.roiAbsolute != null && holding.roiPercent != null) {
                    val isPositive = NumberFormatters.isPositiveRoi(holding.roiAbsolute)
                    val roiColor = if (isPositive) successCol else Error
                    val sign = NumberFormatters.roiSign(holding.roiAbsolute)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$sign${NumberFormatters.fiat(holding.roiAbsolute)} ${holding.fiat}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = roiColor
                        )
                        Text(
                            text = stringResource(R.string.dashboard_roi, "${sign}${NumberFormatters.percent(holding.roiPercent)}%"),
                            style = MaterialTheme.typography.bodySmall,
                            color = roiColor
                        )
                    }
                } else if (holding.currentPrice == null) {
                    StatItem(
                        label = "ROI",
                        value = "–"
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItemInline(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DcaPlanCard(
    planWithBalance: DcaPlanWithBalance,
    onToggle: () -> Unit,
    onClick: (() -> Unit)? = null,
    currentTime: Long = System.currentTimeMillis(),
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    onDragStart: ((heightPx: Int) -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    val plan = planWithBalance.plan
    val successCol = successColor()
    val accentCol = accentColor()
    val context = LocalContext.current
    var cardHeight by remember { mutableIntStateOf(0) }
    var showDisableDialog by remember { mutableStateOf(false) }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text(stringResource(R.string.dashboard_disable_plan_title)) },
            text = { Text(stringResource(R.string.dashboard_disable_plan_message, "${plan.crypto}/${plan.fiat}")) },
            confirmButton = {
                TextButton(onClick = {
                    showDisableDialog = false
                    onToggle()
                }) {
                    Text(stringResource(R.string.dashboard_disable_plan_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    // Keep references fresh so pointerInput(Unit) always calls the latest lambdas
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                if (isDragging) {
                    shadowElevation = 8f
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
            .onGloballyPositioned { coordinates ->
                cardHeight = coordinates.size.height
            }
            .then(
                if (onDragStart != null) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { currentOnDragStart?.invoke(cardHeight) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDrag?.invoke(dragAmount.y)
                            },
                            onDragEnd = { currentOnDragEnd?.invoke() },
                            onDragCancel = { currentOnDragEnd?.invoke() }
                        )
                    }
                } else Modifier
            )
            .then(if (onClick != null && !isDragging) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isDragging) 4.dp else 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Drag handle - only visible while dragging
                if (isDragging) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                CryptoIcon(crypto = plan.crypto)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    // Custom plan label (if set)
                    if (plan.name.isNotBlank()) {
                        Text(
                            text = plan.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentCol
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${plan.crypto}/${plan.fiat}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(plan.strategy.displayNameRes),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = accentCol
                        )
                    }
                    val frequencyText = if (plan.frequency == DcaFrequency.CUSTOM && plan.cronExpression != null) {
                        CronUtils.describeCron(plan.cronExpression) ?: stringResource(plan.frequency.displayNameRes)
                    } else {
                        stringResource(plan.frequency.displayNameRes)
                    }
                    val multiplierResult = planWithBalance.strategyMultiplier
                    val amountText = if (multiplierResult != null && multiplierResult.multiplier != 1.0f) {
                        val effective = plan.amount.multiply(BigDecimal(multiplierResult.multiplier.toString()))
                            .setScale(plan.amount.scale().coerceAtLeast(0), RoundingMode.HALF_UP)
                        val multiplierStr = if (multiplierResult.multiplier % 1.0f == 0f) {
                            multiplierResult.multiplier.toInt().toString()
                        } else {
                            multiplierResult.multiplier.toString()
                        }
                        stringResource(
                            R.string.dashboard_purchase_amount_formula,
                            effective.toPlainString(),
                            plan.fiat,
                            plan.amount.toPlainString(),
                            multiplierStr,
                            frequencyText
                        )
                    } else {
                        "${plan.amount} ${plan.fiat} • $frequencyText"
                    }
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        // Render connection name as suffix when present (Phase 8 multi-connection)
                        text = if (planWithBalance.connectionName.isNotBlank())
                            "${plan.exchange.displayName} - ${planWithBalance.connectionName}"
                        else
                            plan.exchange.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!plan.isEnabled) {
                        Text(
                            text = stringResource(R.string.dashboard_plan_paused),
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (plan.nextExecutionAt != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            @Suppress("UNUSED_EXPRESSION")
                            currentTime // read state so Compose tracks it as a dependency
                            Text(
                                text = stringResource(R.string.dashboard_next_prefix, TimeUtils.formatTimeUntil(plan.nextExecutionAt, context)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Withdrawal threshold warning
                    if (plan.isEnabled && planWithBalance.isOverWithdrawalThreshold && planWithBalance.exchangeCryptoBalance != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Warning
                            )
                            Text(
                                text = stringResource(
                                    R.string.dashboard_withdrawal_ready,
                                    NumberFormatters.crypto(planWithBalance.exchangeCryptoBalance),
                                    plan.crypto
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Warning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    // Balance duration info
                    if (plan.isEnabled && planWithBalance.remainingDays != null) {
                        val balanceColor = if (planWithBalance.isLowBalance) Warning else MaterialTheme.colorScheme.onSurfaceVariant
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (planWithBalance.isLowBalance) Icons.Default.Warning else Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = balanceColor
                            )
                            val daysText = formatRemainingDays(planWithBalance.remainingDays, context)
                            Text(
                                text = if (planWithBalance.remainingExecutions != null) {
                                    stringResource(R.string.dashboard_remaining_with_exec, daysText, planWithBalance.remainingExecutions)
                                } else {
                                    stringResource(R.string.dashboard_remaining_suffix, daysText)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = balanceColor,
                                fontWeight = if (planWithBalance.isLowBalance) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                    // Goal progress bar
                    if (plan.targetAmount != null && planWithBalance.accumulatedCrypto != null) {
                        val target = plan.targetAmount
                        val accumulated = planWithBalance.accumulatedCrypto
                        val progress = if (target > BigDecimal.ZERO) {
                            accumulated.divide(target, 4, RoundingMode.HALF_UP)
                                .toFloat().coerceIn(0f, 1f)
                        } else 0f
                        val percent = if (target > BigDecimal.ZERO) {
                            accumulated.divide(target, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal(100))
                                .setScale(1, RoundingMode.HALF_UP)
                        } else BigDecimal.ZERO
                        val goalReached = accumulated >= target
                        val goalColor = if (goalReached) successCol else accentCol
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = goalColor,
                            trackColor = goalColor.copy(alpha = 0.2f),
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                        Text(
                            text = if (goalReached) {
                                stringResource(R.string.dashboard_goal_reached)
                            } else {
                                stringResource(
                                    R.string.dashboard_goal_progress,
                                    NumberFormatters.crypto(accumulated),
                                    NumberFormatters.crypto(target),
                                    plan.crypto,
                                    percent.toPlainString()
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = goalColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Switch(
                    checked = plan.isEnabled,
                    onCheckedChange = { newValue ->
                        if (!newValue) {
                            // Disabling: show confirmation dialog
                            showDisableDialog = true
                        } else {
                            // Enabling: no confirmation needed
                            onToggle()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = successCol,
                        checkedTrackColor = successCol.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

private fun formatRemainingDays(days: Double, context: android.content.Context): String {
    return when {
        days < 1 -> {
            val hours = (days * 24).toInt()
            if (hours <= 0) context.getString(R.string.dashboard_less_than_1_hour)
            else context.resources.getQuantityString(R.plurals.dashboard_hours, hours, hours)
        }
        days < 2 -> context.resources.getQuantityString(R.plurals.dashboard_days, 1, 1)
        else -> context.resources.getQuantityString(R.plurals.dashboard_days, days.toInt(), days.toInt())
    }
}

@Composable
internal fun EmptyPlansCard(onAddPlan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        EmptyState(
            icon = Icons.Default.Add,
            title = stringResource(R.string.dashboard_no_plans_title),
            description = stringResource(R.string.dashboard_no_plans_description),
            actionLabel = stringResource(R.string.dashboard_create_plan),
            onAction = onAddPlan
        )
    }
}

@Composable
internal fun QuickActionsRow(
    onViewHistory: () -> Unit,
    onRunNow: () -> Unit,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onViewHistory,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.History, contentDescription = stringResource(R.string.dashboard_history))
            if (!compact) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_history),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(maxFontSize = 14.sp)
                )
            }
        }
        Button(
            onClick = onRunNow,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Bolt, contentDescription = stringResource(R.string.dashboard_run_now))
            if (!compact) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_run_now),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(maxFontSize = 14.sp)
                )
            }
        }
    }
}

@Composable
internal fun PortfolioSummaryCard(
    holdings: List<CryptoHoldingWithPrice>
) {
    val allPricesLoaded = holdings.all { it.currentValue != null }

    val totalInvested = holdings.fold(BigDecimal.ZERO) { acc, h -> acc + h.totalInvested }

    // Determine common fiat – only show summary when all plans use the same currency
    val distinctFiats = holdings.map { it.fiat }.distinct()
    if (distinctFiats.size != 1) return
    val fiat = distinctFiats.first()

    val unavailableText = stringResource(R.string.dashboard_price_unavailable)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_portfolio_total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (allPricesLoaded) {
                val totalValue = holdings.fold(BigDecimal.ZERO) { acc, h -> acc + (h.currentValue ?: BigDecimal.ZERO) }
                val roiResult = NumberFormatters.roiValues(totalInvested, totalValue)
                val roiAbsolute = roiResult?.first ?: BigDecimal.ZERO
                val roiPercent = roiResult?.second
                val successCol = successColor()
                val isPositive = roiAbsolute >= BigDecimal.ZERO
                val roiColor = if (isPositive) successCol else Error
                val sign = if (isPositive) "+" else ""

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${NumberFormatters.fiat(totalValue)} $fiat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stringResource(R.string.dashboard_portfolio_total_invested)}: ${NumberFormatters.fiat(totalInvested)} $fiat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$sign${NumberFormatters.fiat(roiAbsolute)} $fiat",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = roiColor
                        )
                        if (roiPercent != null) {
                            Text(
                                text = stringResource(R.string.dashboard_roi, "$sign${NumberFormatters.percent(roiPercent)}%"),
                                style = MaterialTheme.typography.bodySmall,
                                color = roiColor
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = unavailableText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${stringResource(R.string.dashboard_portfolio_total_invested)}: ${NumberFormatters.fiat(totalInvested)} $fiat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "ROI –",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunNowBottomSheet(
    plans: List<DcaPlanWithBalance>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val enabledPlans = plans.filter { it.plan.isEnabled }
    var selectedIds by remember { mutableStateOf(enabledPlans.map { it.plan.id }.toSet()) }
    val allSelected = selectedIds.size == enabledPlans.size && enabledPlans.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.run_now_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Select All row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedIds = if (allSelected) emptySet()
                        else enabledPlans.map { it.plan.id }.toSet()
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = {
                        selectedIds = if (it) enabledPlans.map { p -> p.plan.id }.toSet()
                        else emptySet()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.run_now_all_plans),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider()

            // Plan list
            enabledPlans.forEach { planWithBalance ->
                val plan = planWithBalance.plan
                val isSelected = plan.id in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedIds = if (isSelected) selectedIds - plan.id
                            else selectedIds + plan.id
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            selectedIds = if (it) selectedIds + plan.id
                            else selectedIds - plan.id
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // Show custom plan name (if set) above the pair label
                        if (plan.name.isNotBlank()) {
                            Text(
                                text = plan.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${plan.crypto}/${plan.fiat}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(plan.strategy.displayNameRes),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val sheetMultiplier = planWithBalance.strategyMultiplier
                        val sheetAmountText = if (sheetMultiplier != null && sheetMultiplier.multiplier != 1.0f) {
                            val effective = plan.amount.multiply(BigDecimal(sheetMultiplier.multiplier.toString()))
                                .setScale(plan.amount.scale().coerceAtLeast(0), RoundingMode.HALF_UP)
                            val multiplierStr = if (sheetMultiplier.multiplier % 1.0f == 0f) {
                                sheetMultiplier.multiplier.toInt().toString()
                            } else {
                                sheetMultiplier.multiplier.toString()
                            }
                            "${effective.toPlainString()} ${plan.fiat} (${plan.amount.toPlainString()} × $multiplierStr) • ${plan.exchange.displayName}"
                        } else {
                            "${plan.amount} ${plan.fiat} • ${plan.exchange.displayName}"
                        }
                        Text(
                            text = sheetAmountText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onConfirm(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.run_now_confirm, selectedIds.size))
            }
        }
    }
}

@Composable
internal fun MarketPulseCard(
    fearGreedData: FearGreedData?,
    athDataByCrypto: Map<String, CryptoData>,
    isExpanded: Boolean = true,
    onToggleExpand: () -> Unit = {}
) {
    val indicatorColor = MaterialTheme.colorScheme.onSurface
    var showMarketPulseInfo by remember { mutableStateOf(false) }

    // Localized F&G classification
    val localizedClassification = fearGreedData?.let { localizedFearGreedClass(it.value) }

    if (showMarketPulseInfo) {
        MarketPulseInfoSheet(onDismiss = { showMarketPulseInfo = false })
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_market_pulse),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showMarketPulseInfo = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.market_pulse_info_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Gauge area
            Column(modifier = Modifier.fillMaxWidth()) {

                // Expanded: show labels above the bar
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // "Fear & Greed" centered above
                        if (fearGreedData != null) {
                            Text(
                                text = stringResource(R.string.dashboard_fear_greed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            // Row: "Fear" (left) – "14 – Extreme Fear" (center) – "Greed" (right)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.dashboard_fear_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                                Text(
                                    text = "${fearGreedData.value} – $localizedClassification",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = fearGreedColor(fearGreedData.value),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_greed_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }

                // ▼ Fear & Greed triangle (always visible)
                if (fearGreedData != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    ) {
                        val w = 8.dp.toPx(); val h = 6.dp.toPx()
                        val x = (fearGreedData.value / 100f).coerceIn(0f, 1f) * size.width
                        val path = Path().apply {
                            moveTo(x - w / 2, size.height - h)
                            lineTo(x + w / 2, size.height - h)
                            lineTo(x, size.height)
                            close()
                        }
                        drawPath(path, color = indicatorColor)
                    }
                }

                // Colored bar (always visible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    gaugeColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                    }
                }

                // ▲ ATH triangles (always visible)
                if (athDataByCrypto.isNotEmpty()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    ) {
                        val w = 8.dp.toPx(); val h = 6.dp.toPx()
                        athDataByCrypto.values.forEach { data ->
                            val x = (1.0f - data.athDistance).coerceIn(0f, 1f) * size.width
                            val path = Path().apply {
                                moveTo(x, 0f)
                                lineTo(x - w / 2, h)
                                lineTo(x + w / 2, h)
                                close()
                            }
                            drawPath(path, color = indicatorColor)
                        }
                    }
                }

                // Expanded: show labels below the bar
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (athDataByCrypto.isNotEmpty()) {
                            // Row: "0" (left) – "BTC -35 %" (center) – "ATH" (right)
                            val singleFmt = stringResource(R.string.ath_distance_format)
                            val cryptoFmt = stringResource(R.string.ath_distance_crypto_format)
                            val athCenterText = if (athDataByCrypto.size == 1) {
                                val entry = athDataByCrypto.entries.first()
                                String.format(singleFmt, entry.value.athDistancePercent)
                            } else {
                                athDataByCrypto.entries.joinToString(", ") { (crypto, data) ->
                                    String.format(cryptoFmt, crypto, data.athDistancePercent)
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                                Text(
                                    text = athCenterText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                Text(
                                    text = "ATH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }

                            // "ATH Distance" / "Vzdálenost od ATH" centered below
                            Text(
                                text = stringResource(R.string.dashboard_ath_distance),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun localizedFearGreedClass(value: Int): String {
    return when {
        value <= 19 -> stringResource(R.string.fg_class_extreme_fear)
        value <= 39 -> stringResource(R.string.fg_class_fear)
        value <= 59 -> stringResource(R.string.fg_class_neutral)
        value <= 79 -> stringResource(R.string.fg_class_greed)
        else -> stringResource(R.string.fg_class_extreme_greed)
    }
}

private val gaugeColors = listOf(
    Color(0xFFE53935),
    Color(0xFFFF9800),
    Color(0xFFFDD835),
    Color(0xFF8BC34A),
    Color(0xFF4CAF50)
)

private fun fearGreedColor(value: Int): Color {
    return when {
        value <= 19 -> Color(0xFFE53935) // Extreme Fear - red
        value <= 39 -> Color(0xFFFF9800) // Fear - orange
        value <= 59 -> Color(0xFFFDD835) // Neutral - yellow
        value <= 79 -> Color(0xFF8BC34A) // Greed - light green
        else -> Color(0xFF4CAF50)        // Extreme Greed - green
    }
}
