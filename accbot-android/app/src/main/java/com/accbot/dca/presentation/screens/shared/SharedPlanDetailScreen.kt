package com.accbot.dca.presentation.screens.shared

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanTrade
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.ApiImportResultDialog
import com.accbot.dca.presentation.components.ImportConfigDialog
import com.accbot.dca.presentation.components.CryptoIcon
import com.accbot.dca.presentation.components.ErrorState
import com.accbot.dca.presentation.components.LoadingState
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.DateFormatters
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal

@Composable
fun SharedPlanDetailScreen(
    shareId: String,
    onNavigateBack: () -> Unit,
    viewModel: SharedPlanDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(shareId) { viewModel.load(shareId) }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onNavigateBack() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.shared_plan_delete_title)) },
            text = { Text(stringResource(R.string.shared_plan_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.common_delete), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    val historyPlan = uiState.plan
    if (uiState.historyDialog && historyPlan != null) {
        ImportConfigDialog(
            range = uiState.historyRange,
            onRangeChange = viewModel::setHistoryRange,
            onConfirm = viewModel::downloadHistory,
            onDismiss = viewModel::dismissHistoryDialog,
            canImportSinceLastPurchase = historyPlan.trades.isNotEmpty(),
            lastPurchaseMillis = historyPlan.trades.lastOrNull()?.executedAt?.toEpochMilli(),
            minSelectableDateMillis = historyPlan.startedAt.toEpochMilli(),
            allHistoryTitle = stringResource(R.string.shared_plan_history_from_start),
            allHistoryHint = DateFormatters.shortDateTime.format(historyPlan.startedAt),
            warning = stringResource(R.string.shared_plan_history_warning)
        )
    }
    uiState.downloadResult?.let { ApiImportResultDialog(result = it, onDismiss = viewModel::consumeDownloadResult) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.shared_plan_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    if (uiState.plan?.liveUpdates == true) {
                        if (uiState.refreshing || uiState.downloading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = viewModel::openHistoryDialog) {
                                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.shared_plan_download_history))
                            }
                            IconButton(onClick = viewModel::refresh) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.shared_plan_refresh))
                            }
                        }
                    }
                    if (uiState.plan != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val plan = uiState.plan
        when {
            uiState.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
            plan == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ErrorState(message = stringResource(R.string.shared_plan_not_found))
            }
            else -> SharedPlanContent(plan, uiState.currentPrice, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SharedPlanContent(plan: SharedPlan, currentPrice: BigDecimal?, modifier: Modifier) {
    val summary = plan.summary
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ReadOnlyBanner(plan)
        }
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CryptoIcon(crypto = plan.crypto, size = 40)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(plan.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${plan.crypto}/${plan.fiat} · ${plan.exchange?.displayName ?: plan.exchangeName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                plan.amount?.let {
                    LabeledValue(stringResource(R.string.shared_plan_amount), "${NumberFormatters.fiat(it)} ${plan.fiat}")
                }
                LabeledValue(stringResource(R.string.shared_plan_schedule), scheduleText(plan))
                LabeledValue(stringResource(R.string.shared_plan_strategy), stringResource(plan.strategy.displayNameRes))
                LabeledValue(
                    stringResource(R.string.shared_plan_state),
                    stringResource(if (plan.isEnabled) R.string.shared_plan_state_active else R.string.shared_plan_state_paused)
                )
                LabeledValue(stringResource(R.string.shared_plan_created), DateFormatters.shortDate.format(plan.createdAt))
            }
        }
        item {
            InfoCard {
                Text(stringResource(R.string.shared_plan_summary), fontWeight = FontWeight.SemiBold)
                LabeledValue(stringResource(R.string.shared_plan_buys), summary.buyCount.toString())
                if (summary.sellCount > 0) {
                    LabeledValue(stringResource(R.string.shared_plan_sells), summary.sellCount.toString())
                }
                summary.firstTradeAt?.let {
                    LabeledValue(stringResource(R.string.shared_plan_first_buy), DateFormatters.shortDate.format(it))
                }
                summary.totalInvested?.let {
                    LabeledValue(stringResource(R.string.shared_plan_invested), "${NumberFormatters.fiat(it)} ${plan.fiat}")
                }
                summary.heldCrypto?.let {
                    LabeledValue(stringResource(R.string.shared_plan_held), "${NumberFormatters.crypto(it)} ${plan.crypto}")
                }
                summary.totalSoldFiat?.takeIf { summary.sellCount > 0 }?.let {
                    LabeledValue(stringResource(R.string.shared_plan_sold), "${NumberFormatters.fiat(it)} ${plan.fiat}")
                }
                summary.avgBuyPrice?.let {
                    LabeledValue(stringResource(R.string.shared_plan_avg_price), "${NumberFormatters.fiat(it)} ${plan.fiat}")
                }
                currentPrice?.let {
                    LabeledValue(stringResource(R.string.shared_plan_current_price), "${NumberFormatters.fiat(it)} ${plan.fiat}")
                }
                summary.currentValue(currentPrice)?.let {
                    LabeledValue(stringResource(R.string.shared_plan_current_value), "${NumberFormatters.fiat(it)} ${plan.fiat}")
                }
                summary.performancePercent(currentPrice)?.let {
                    LabeledValue(
                        label = stringResource(R.string.shared_plan_performance),
                        value = "${NumberFormatters.roiSign(it)}${NumberFormatters.percent(it)} %",
                        valueColor = if (it >= BigDecimal.ZERO) successColor() else Error
                    )
                }
                if (!plan.includesAmounts) {
                    Text(
                        stringResource(R.string.shared_plan_amounts_hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.shared_plan_trades, plan.trades.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (plan.trades.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.shared_plan_no_trades),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        items(plan.trades.asReversed()) { trade ->
            TradeRow(trade, plan)
        }
    }
}

@Composable
private fun ReadOnlyBanner(plan: SharedPlan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.shared_plan_read_only),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                val refreshedAt = plan.refreshedAt
                Text(
                    when {
                        !plan.liveUpdates -> stringResource(
                            R.string.shared_plan_read_only_text, DateFormatters.shortDateTime.format(plan.sharedAt)
                        )
                        refreshedAt != null -> stringResource(
                            R.string.shared_plan_live_text, DateFormatters.shortDateTime.format(refreshedAt)
                        )
                        else -> stringResource(R.string.shared_plan_live_text_never)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (plan.liveUpdates && plan.refreshFailed) {
                    Text(
                        stringResource(R.string.shared_plan_refresh_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
                if (plan.liveUpdates) {
                    if (plan.fullAccessKey) {
                        Text(
                            stringResource(R.string.shared_plan_key_full_access),
                            style = MaterialTheme.typography.labelSmall,
                            color = Warning
                        )
                    } else {
                        Text(
                            stringResource(
                                if (plan.verifiedReadOnly) R.string.shared_plan_key_verified
                                else R.string.shared_plan_key_unverified
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        stringResource(R.string.shared_plan_account_scope),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (plan.isSandbox) {
                    Text(
                        stringResource(R.string.shared_plans_sandbox_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = Warning
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeRow(trade: SharedPlanTrade, plan: SharedPlan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (trade.side == TransactionSide.BUY) R.string.shared_plan_side_buy else R.string.shared_plan_side_sell),
                    fontWeight = FontWeight.SemiBold,
                    color = if (trade.side == TransactionSide.BUY) successColor() else Warning
                )
                Text(
                    DateFormatters.shortDateTime.format(trade.executedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                trade.cryptoAmount?.let {
                    Text("${NumberFormatters.crypto(it)} ${plan.crypto}", fontWeight = FontWeight.Medium)
                }
                trade.fiatAmount?.let {
                    Text(
                        "${NumberFormatters.fiat(it)} ${plan.fiat}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.shared_plan_at_price, "${NumberFormatters.fiat(trade.price)} ${plan.fiat}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor, textAlign = TextAlign.End)
    }
}

@Composable
private fun scheduleText(plan: SharedPlan): String {
    val cron = plan.cronExpression
    if (cron != null && (plan.frequency == DcaFrequency.CUSTOM || plan.frequency == null)) {
        return CronUtils.describeCron(cron) ?: cron
    }
    return plan.frequency?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.shared_plan_schedule_unknown)
}
