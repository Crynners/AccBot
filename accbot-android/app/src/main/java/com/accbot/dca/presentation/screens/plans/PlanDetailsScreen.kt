package com.accbot.dca.presentation.screens.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.supportsApiImport
import com.accbot.dca.presentation.components.*
import com.accbot.dca.presentation.screens.plans.components.OpenSellsList
import com.accbot.dca.presentation.screens.plans.components.PnLCard
import com.accbot.dca.presentation.screens.plans.sell.SellWizardBottomSheet
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailsScreen(
    planId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToHistory: ((crypto: String, fiat: String) -> Unit)? = null,
    onNavigateToTransactionDetails: ((Long) -> Unit)? = null,
    viewModel: PlanDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sellUiVisible by viewModel.sellUiVisible.collectAsStateWithLifecycle()
    val planPnL by viewModel.planPnL.collectAsStateWithLifecycle()
    val openSells by viewModel.openSells.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var deletePlanConfirmText by rememberSaveable { mutableStateOf("") }
    var showStrategyInfo by rememberSaveable { mutableStateOf(false) }
    var showLotSizeInfo by rememberSaveable { mutableStateOf(false) }
    var showDeleteTransactionsDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTransactionsConfirmText by rememberSaveable { mutableStateOf("") }
    var dangerZoneExpanded by rememberSaveable { mutableStateOf(false) }
    var sellWizardOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(planId) {
        viewModel.loadPlan(planId)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    if (sellWizardOpen) {
        SellWizardBottomSheet(
            planId = planId,
            onDismiss = { sellWizardOpen = false }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        val plan = uiState.plan
        val confirmPair = plan?.let { "${it.crypto}/${it.fiat}" } ?: ""
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deletePlanConfirmText = ""
            },
            title = { Text(stringResource(R.string.plan_details_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.plan_details_delete_text))
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = deletePlanConfirmText,
                        onValueChange = { deletePlanConfirmText = it },
                        label = { Text(stringResource(R.string.plan_details_delete_confirm_hint, confirmPair)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlan { onNavigateBack() }
                        showDeleteDialog = false
                        deletePlanConfirmText = ""
                    },
                    enabled = deletePlanConfirmText == confirmPair
                ) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = if (deletePlanConfirmText == confirmPair) Error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deletePlanConfirmText = ""
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Delete all transactions confirmation dialog
    if (showDeleteTransactionsDialog) {
        val txCount = uiState.transactions.size
        AlertDialog(
            onDismissRequest = {
                showDeleteTransactionsDialog = false
                deleteTransactionsConfirmText = ""
            },
            title = { Text(stringResource(R.string.plan_details_delete_transactions_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.plan_details_delete_transactions_text, txCount))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = deleteTransactionsConfirmText,
                        onValueChange = { deleteTransactionsConfirmText = it },
                        label = { Text(stringResource(R.string.plan_details_delete_transactions_hint, txCount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllTransactions { count ->
                            showDeleteTransactionsDialog = false
                            deleteTransactionsConfirmText = ""
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.plan_details_delete_transactions_success, count)
                                )
                            }
                        }
                    },
                    enabled = deleteTransactionsConfirmText == txCount.toString()
                ) {
                    Text(
                        stringResource(R.string.plan_details_delete_transactions_button),
                        color = if (deleteTransactionsConfirmText == txCount.toString()) Error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteTransactionsDialog = false
                    deleteTransactionsConfirmText = ""
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Import configuration dialog
    if (uiState.showImportDialog) {
        ImportConfigDialog(
            sinceMillis = uiState.importSinceMillis,
            onSinceDateChanged = { viewModel.setImportSinceDate(it) },
            onConfirm = { viewModel.confirmImport() },
            onDismiss = { viewModel.dismissImportDialog() },
            otherPlansOnSameConnection = uiState.otherPlansOnSameConnection
        )
    }

    // API import result dialog
    uiState.apiImportResult?.let { result ->
        ApiImportResultDialog(result = result, onDismiss = { viewModel.dismissImportResult() })
    }

    // Delete blocked: plan still has open sell orders. User must cancel them first.
    uiState.deleteBlockedOpenSells?.let { count ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteBlockedDialog() },
            title = { Text(stringResource(R.string.plan_details_delete_blocked_title)) },
            text = { Text(stringResource(R.string.plan_details_delete_blocked_text, count)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDeleteBlockedDialog() }) {
                    Text(stringResource(R.string.common_done))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.plan_details_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.plan_details_edit))
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
                    LoadingState(message = stringResource(R.string.plan_details_loading))
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
                        onRetry = { viewModel.loadPlan(planId) }
                    )
                }
            }
            uiState.plan != null -> {
                val plan = uiState.plan!!

                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // 1. Header card (simplified – no strategy name)
                    item {
                        Card(
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
                                CryptoIcon(crypto = plan.crypto, size = 64)

                                Spacer(modifier = Modifier.height(16.dp))

                                // Editable plan name (inline)
                                var isRenamingPlan by rememberSaveable { mutableStateOf(false) }
                                var planNameText by rememberSaveable(plan.name) { mutableStateOf(plan.name) }

                                if (isRenamingPlan) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = planNameText,
                                            onValueChange = { planNameText = it },
                                            singleLine = true,
                                            placeholder = { Text(stringResource(R.string.plan_details_name_hint)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = {
                                            viewModel.renamePlan(planNameText.trim())
                                            isRenamingPlan = false
                                        }) {
                                            Text(stringResource(R.string.common_save))
                                        }
                                    }
                                } else if (plan.name.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { isRenamingPlan = true }
                                    ) {
                                        Text(
                                            text = plan.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor()
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.common_rename),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    // No name yet - show "add name" link
                                    TextButton(onClick = { isRenamingPlan = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.plan_details_add_name),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Text(
                                    text = "${plan.crypto}/${plan.fiat}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = plan.exchange.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Status toggle
                                val successCol = successColor()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable(role = Role.Switch) { viewModel.togglePlanEnabled() }
                                        .semantics(mergeDescendants = true) { role = Role.Switch }
                                ) {
                                    Text(
                                        text = if (plan.isEnabled) stringResource(R.string.plan_details_active) else stringResource(R.string.plan_details_paused),
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (plan.isEnabled) successCol else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = plan.isEnabled,
                                        onCheckedChange = null,
                                        modifier = Modifier.clearAndSetSemantics {},
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = successCol,
                                            checkedTrackColor = successCol.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 2. Performance card (merged Stats + Performance)
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.plan_details_performance),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.semantics { heading() }
                                )

                                // 2x2 compact stat grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CompactStat(
                                        label = stringResource(R.string.plan_details_invested),
                                        value = "${NumberFormatters.fiat(uiState.totalInvested)} ${plan.fiat}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactStat(
                                        label = stringResource(R.string.plan_details_current_value),
                                        value = if (uiState.currentValue != null)
                                            "${NumberFormatters.fiat(uiState.currentValue!!)} ${plan.fiat}"
                                        else
                                            "${NumberFormatters.fiat(uiState.totalInvested)} ${plan.fiat}",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CompactStat(
                                        label = stringResource(R.string.plan_details_avg_price),
                                        value = "${NumberFormatters.fiat(uiState.averagePrice)} ${plan.fiat}/${plan.crypto}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactStat(
                                        label = stringResource(R.string.plan_details_transactions),
                                        value = uiState.transactionCount.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Price / ROI section (only when transactions exist)
                                if (uiState.transactionCount > 0) {
                                    HorizontalDivider()

                                    if (uiState.isPriceLoading) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.common_loading),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    } else {
                                        val price = uiState.currentPrice
                                        if (price != null) {
                                            PlanConfigRow(
                                                icon = Icons.Default.Paid,
                                                label = stringResource(R.string.plan_details_current_price),
                                                value = "${NumberFormatters.fiat(price)} ${plan.fiat}/${plan.crypto}"
                                            )
                                        }

                                        val roiAbs = uiState.roiAbsolute
                                        val roiPct = uiState.roiPercent
                                        if (roiAbs != null && roiPct != null) {
                                            val isPositive = roiAbs >= BigDecimal.ZERO
                                            val sign = if (isPositive) "+" else ""
                                            val roiColor = if (isPositive) successColor() else Error
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                                    contentDescription = null,
                                                    tint = roiColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = stringResource(R.string.plan_details_roi),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "$sign${NumberFormatters.fiat(roiAbs)} ${plan.fiat} (${sign}${NumberFormatters.percent(roiPct)}%)",
                                                        fontWeight = FontWeight.Medium,
                                                        color = roiColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Plan Setup card (merged Configuration + Strategy)
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.plan_details_configuration),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.semantics { heading() }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachMoney,
                                            contentDescription = null,
                                            tint = accentColor(),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = stringResource(R.string.plan_details_amount),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${plan.amount} ${plan.fiat}",
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    if (plan.exchange == Exchange.BINANCE) {
                                        IconButton(
                                            onClick = { showLotSizeInfo = true }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = accentColor(),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                val frequencyDisplayText = if (plan.frequency == com.accbot.dca.domain.model.DcaFrequency.CUSTOM && plan.cronExpression != null) {
                                    com.accbot.dca.domain.util.CronUtils.describeCron(plan.cronExpression) ?: stringResource(plan.frequency.displayNameRes)
                                } else {
                                    stringResource(plan.frequency.displayNameRes)
                                }
                                PlanConfigRow(
                                    icon = Icons.Default.Schedule,
                                    label = stringResource(R.string.plan_details_frequency),
                                    value = frequencyDisplayText
                                )

                                PlanConfigRow(
                                    icon = Icons.Default.Timer,
                                    label = stringResource(R.string.plan_details_next_execution),
                                    value = uiState.timeUntilNextExecution
                                )

                                // Strategy (merged from separate card)
                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = when (plan.strategy) {
                                                is DcaStrategy.Classic -> Icons.Default.Repeat
                                                is DcaStrategy.AthBased -> Icons.AutoMirrored.Filled.TrendingDown
                                                is DcaStrategy.FearAndGreed -> Icons.Default.Psychology
                                            },
                                            contentDescription = null,
                                            tint = accentColor(),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = stringResource(plan.strategy.displayNameRes),
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = stringResource(plan.strategy.descriptionRes),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { showStrategyInfo = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = stringResource(R.string.add_plan_strategy_info),
                                            tint = accentColor(),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                if (plan.withdrawalEnabled && plan.withdrawalAddress != null) {
                                    HorizontalDivider()
                                    PlanConfigRow(
                                        icon = Icons.AutoMirrored.Filled.Send,
                                        label = stringResource(R.string.plan_details_auto_withdrawal),
                                        value = "${plan.withdrawalAddress.take(8)}...${plan.withdrawalAddress.takeLast(8)}"
                                    )
                                }
                            }
                        }

                        // Strategy Info Bottom Sheet
                        if (showStrategyInfo) {
                            StrategyInfoBottomSheet(
                                strategy = plan.strategy,
                                onDismiss = { showStrategyInfo = false }
                            )
                        }

                        // Binance lot size info dialog
                        if (showLotSizeInfo) {
                            AlertDialog(
                                onDismissRequest = { showLotSizeInfo = false },
                                confirmButton = {
                                    TextButton(onClick = { showLotSizeInfo = false }) {
                                        Text(stringResource(R.string.common_done))
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = accentColor()
                                    )
                                },
                                text = {
                                    val stepSize = Exchange.binanceLotStepSize[plan.crypto] ?: "0.00001"
                                    Text(stringResource(R.string.binance_lot_size_note, plan.crypto, stepSize))
                                }
                            )
                        }
                    }

                    // 4. Exchange Balance card (with heading)
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.plan_details_exchange_balance_title),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.semantics { heading() }
                                )

                                if (uiState.isBalanceLoading) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.common_loading),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else {
                                    val balance = uiState.fiatBalance
                                    if (balance != null) {
                                        PlanConfigRow(
                                            icon = Icons.Default.AccountBalance,
                                            label = stringResource(R.string.plan_details_balance),
                                            value = "${NumberFormatters.fiat(balance)} ${plan.fiat}"
                                        )

                                        val cryptoBalance = uiState.cryptoBalance
                                        if (cryptoBalance != null) {
                                            PlanConfigRow(
                                                icon = Icons.Default.CurrencyBitcoin,
                                                label = stringResource(R.string.plan_details_crypto_balance),
                                                value = "${NumberFormatters.crypto(cryptoBalance)} ${plan.crypto}"
                                            )
                                        }

                                        val days = uiState.remainingDays
                                        val exec = uiState.remainingExecutions
                                        if (days != null && exec != null) {
                                            PlanConfigRow(
                                                icon = Icons.Default.DateRange,
                                                label = stringResource(R.string.plan_details_estimated_duration),
                                                value = stringResource(R.string.plan_details_days_remaining, days, exec)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4.5 Sell section (P&L card, open orders, create-sell button).
                    // Shown only when plan opted in + global trading enabled + exchange supports it.
                    if (sellUiVisible) {
                        planPnL?.let { pnl ->
                            item {
                                PnLCard(
                                    pnl = pnl,
                                    fiat = plan.fiat,
                                    crypto = plan.crypto,
                                    targetAmount = plan.targetProfitAmount
                                )
                            }
                        }

                        if (openSells.isNotEmpty()) {
                            item {
                                OpenSellsList(
                                    openSells = openSells,
                                    onCancelClick = viewModel::cancelSell
                                )
                            }
                        }

                        item {
                            val heldCrypto = planPnL?.currentCryptoHeld ?: BigDecimal.ZERO
                            Button(
                                onClick = { sellWizardOpen = true },
                                enabled = heldCrypto > BigDecimal.ZERO,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sell,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.plan_details_create_sell_order))
                            }
                        }
                    }

                    // 5. Transactions section (with Import API in header)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.plan_details_recent_transactions),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (plan.exchange.supportsApiImport) {
                                if (uiState.isApiImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    FilledTonalIconButton(
                                        onClick = { viewModel.showImportDialog() },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = accentColor().copy(alpha = 0.15f),
                                            contentColor = accentColor()
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDownload,
                                            contentDescription = stringResource(R.string.import_api_title),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.transactions.isNotEmpty()) {
                        items(uiState.transactions.take(10), key = { it.id }) { transaction ->
                            TransactionCard(
                                transaction = transaction,
                                onClick = onNavigateToTransactionDetails?.let { nav -> { nav(transaction.id) } }
                            )
                        }

                        if (uiState.transactions.size > 10) {
                            item {
                                TextButton(
                                    onClick = {
                                        uiState.plan?.let { plan ->
                                            onNavigateToHistory?.invoke(plan.crypto, plan.fiat)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.plan_details_view_all, uiState.transactions.size))
                                }
                            }
                        }
                    } else {
                        item {
                            EmptyState(
                                icon = Icons.Default.Receipt,
                                title = stringResource(R.string.plan_details_no_transactions_title),
                                description = stringResource(R.string.plan_details_no_transactions_desc)
                            )
                        }
                    }

                    // 6. Danger Zone (collapsible)
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dangerZoneExpanded = !dangerZoneExpanded }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_danger_zone),
                                style = MaterialTheme.typography.labelMedium,
                                color = Error,
                                modifier = Modifier.semantics { heading() }
                            )
                            Icon(
                                imageVector = if (dangerZoneExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (dangerZoneExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                                tint = Error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (dangerZoneExpanded) {
                        if (uiState.transactions.isNotEmpty()) {
                            item {
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDeleteTransactionsDialog = true },
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = Error.copy(alpha = 0.1f)
                                    ),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(Error)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = null,
                                            tint = Error
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.plan_details_delete_transactions_button),
                                            fontWeight = FontWeight.SemiBold,
                                            color = Error
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDeleteDialog = true },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = Error.copy(alpha = 0.1f)
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Error)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Error
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.plan_details_delete_title),
                                            fontWeight = FontWeight.SemiBold,
                                            color = Error
                                        )
                                        Text(
                                            text = stringResource(R.string.plan_details_delete_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
                } // PullToRefreshBox
            }
        }
    }
}

@Composable
internal fun PlanConfigRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor(),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CompactStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
