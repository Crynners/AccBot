package com.accbot.dca.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.presentation.components.CryptoIcon
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.components.SectionHeader
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.TimeUtils
import com.accbot.dca.presentation.utils.NumberFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToPlans: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlanDetails: ((Long) -> Unit)? = null,
    onNavigateToPortfolio: ((String, String) -> Unit)? = null,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
    ) { paddingValues ->
        if (isLandscape) {
            // Landscape: two-column layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.isSandboxMode) {
                        SandboxBanner()
                    }

                    HoldingsPager(
                        holdings = uiState.holdings,
                        isPriceLoading = uiState.isPriceLoading,
                        onRefreshPrices = { viewModel.refreshPrices() },
                        onHoldingClick = onNavigateToPortfolio,
                        compact = true
                    )

                    QuickActionsRow(
                        onViewHistory = onNavigateToHistory,
                        onRunNow = { viewModel.showRunNowSheet() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Right column: DCA Plans
                LazyColumn(
                    modifier = Modifier.weight(0.5f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

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
                        items(uiState.activePlans, key = { it.plan.id }) { planWithBalance ->
                            DcaPlanCard(
                                planWithBalance = planWithBalance,
                                onToggle = { viewModel.togglePlan(planWithBalance.plan.id) },
                                onClick = { onNavigateToPlanDetails?.invoke(planWithBalance.plan.id) }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Sandbox Mode Banner
                if (uiState.isSandboxMode) {
                    item {
                        SandboxBanner()
                    }
                }

                // Holdings Pager
                item {
                    HoldingsPager(
                        holdings = uiState.holdings,
                        isPriceLoading = uiState.isPriceLoading,
                        onRefreshPrices = { viewModel.refreshPrices() },
                        onHoldingClick = onNavigateToPortfolio
                    )
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
                    items(uiState.activePlans, key = { it.plan.id }) { planWithBalance ->
                        DcaPlanCard(
                            planWithBalance = planWithBalance,
                            onToggle = { viewModel.togglePlan(planWithBalance.plan.id) },
                            onClick = { onNavigateToPlanDetails?.invoke(planWithBalance.plan.id) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HoldingsPager(
    holdings: List<CryptoHoldingWithPrice>,
    isPriceLoading: Boolean,
    onRefreshPrices: () -> Unit,
    onHoldingClick: ((String, String) -> Unit)? = null,
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
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { holdings.size })

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
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
            // Header with refresh button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_total_accumulated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onRefreshPrices,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isPriceLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.dashboard_refresh_prices),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                    if (holding.currentPrice != null) {
                        Spacer(Modifier.height(4.dp))
                        StatItemInline(
                            label = stringResource(R.string.dashboard_current_price),
                            value = "${NumberFormatters.fiat(holding.currentPrice)} ${holding.fiat}/${holding.crypto}"
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

            if (holding.currentPrice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = stringResource(R.string.dashboard_current_price),
                        value = "${NumberFormatters.fiat(holding.currentPrice)} ${holding.fiat}/${holding.crypto}"
                    )
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
                    }
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
    onClick: (() -> Unit)? = null
) {
    val plan = planWithBalance.plan
    val successCol = successColor()
    val accentCol = accentColor()
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                CryptoIcon(crypto = plan.crypto)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${plan.crypto}/${plan.fiat}",
                        fontWeight = FontWeight.SemiBold
                    )
                    val frequencyText = if (plan.frequency == DcaFrequency.CUSTOM && plan.cronExpression != null) {
                        CronUtils.describeCron(plan.cronExpression) ?: stringResource(plan.frequency.displayNameRes)
                    } else {
                        stringResource(plan.frequency.displayNameRes)
                    }
                    Text(
                        text = "${plan.amount} ${plan.fiat} • $frequencyText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = plan.exchange.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (plan.strategy !is DcaStrategy.Classic) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(plan.strategy.displayNameRes).replace(" DCA", ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = accentCol,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (plan.isEnabled && plan.nextExecutionAt != null) {
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
                            Text(
                                text = stringResource(R.string.dashboard_next_prefix, TimeUtils.formatTimeUntil(plan.nextExecutionAt, context)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }
            }
            Switch(
                checked = plan.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = successCol,
                    checkedTrackColor = successCol.copy(alpha = 0.5f)
                )
            )
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
                    textAlign = TextAlign.Center
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
                    textAlign = TextAlign.Center
                )
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
                        Text(
                            text = "${plan.crypto}/${plan.fiat}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${plan.amount} ${plan.fiat} • ${plan.exchange.displayName}",
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
