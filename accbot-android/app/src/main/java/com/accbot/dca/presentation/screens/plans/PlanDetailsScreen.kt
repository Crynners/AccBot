package com.accbot.dca.presentation.screens.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.supportsApiImport
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.domain.model.supportsImport
import com.accbot.dca.presentation.components.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailsScreen(
    planId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToImport: (() -> Unit)? = null,
    onNavigateToHistory: ((crypto: String, fiat: String) -> Unit)? = null,
    viewModel: PlanDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var deletePlanConfirmText by rememberSaveable { mutableStateOf("") }
    var showStrategyInfo by rememberSaveable { mutableStateOf(false) }
    var showDeleteTransactionsDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTransactionsConfirmText by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(planId) {
        viewModel.loadPlan(planId)
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
            onDismiss = { viewModel.dismissImportDialog() }
        )
    }

    // API import result dialog
    uiState.apiImportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportResult() },
            title = {
                Text(
                    when (result) {
                        is ApiImportResultState.Success -> stringResource(R.string.import_api_success_title)
                        is ApiImportResultState.Error -> stringResource(R.string.import_api_error_title)
                    }
                )
            },
            text = {
                Text(
                    when (result) {
                        is ApiImportResultState.Success -> {
                            if (result.imported == 0) {
                                stringResource(R.string.import_api_no_new)
                            } else {
                                stringResource(R.string.import_api_success_message, result.imported, result.skipped)
                            }
                        }
                        is ApiImportResultState.Error -> result.message
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportResult() }) {
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // Plan header card
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

                                Text(
                                    text = "${plan.crypto}/${plan.fiat}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = stringResource(plan.strategy.displayNameRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = accentColor()
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

                    // Plan configuration
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

                                PlanConfigRow(
                                    icon = Icons.Default.AttachMoney,
                                    label = stringResource(R.string.plan_details_amount),
                                    value = "${plan.amount} ${plan.fiat}"
                                )

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

                                if (plan.withdrawalEnabled && plan.withdrawalAddress != null) {
                                    PlanConfigRow(
                                        icon = Icons.AutoMirrored.Filled.Send,
                                        label = stringResource(R.string.plan_details_auto_withdrawal),
                                        value = "${plan.withdrawalAddress.take(8)}...${plan.withdrawalAddress.takeLast(8)}"
                                    )
                                }
                            }
                        }
                    }

                    // DCA Strategy
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.add_plan_dca_strategy),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.semantics { heading() }
                                    )
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

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            }
                        }

                        // Strategy Info Bottom Sheet
                        if (showStrategyInfo) {
                            StrategyInfoBottomSheet(
                                strategy = plan.strategy,
                                onDismiss = { showStrategyInfo = false }
                            )
                        }
                    }

                    // Statistics
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = stringResource(R.string.plan_details_invested),
                                value = "${NumberFormatters.fiat(uiState.totalInvested)} ${plan.fiat}",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = stringResource(R.string.plan_details_accumulated),
                                value = "${NumberFormatters.crypto(uiState.totalCrypto)} ${plan.crypto}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = stringResource(R.string.plan_details_avg_price),
                                value = "${NumberFormatters.fiat(uiState.averagePrice)} ${plan.fiat}/${plan.crypto}",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = stringResource(R.string.plan_details_transactions),
                                value = uiState.transactionCount.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Performance card
                    if (uiState.transactionCount > 0) {
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
                                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                                label = stringResource(R.string.plan_details_current_price),
                                                value = "${NumberFormatters.fiat(price)} ${plan.fiat}/${plan.crypto}"
                                            )
                                        }

                                        val value = uiState.currentValue
                                        if (value != null) {
                                            PlanConfigRow(
                                                icon = Icons.Default.AccountBalanceWallet,
                                                label = stringResource(R.string.plan_details_current_value),
                                                value = "${NumberFormatters.fiat(value)} ${plan.fiat}"
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

                    // Balance card
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

                    // Transaction history
                    if (uiState.transactions.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(R.string.plan_details_recent_transactions))
                        }

                        items(uiState.transactions.take(10), key = { it.id }) { transaction ->
                            TransactionCard(transaction = transaction)
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

                    // Delete all transactions button
                    if (uiState.transactions.isNotEmpty()) {
                        item {
                            OutlinedButton(
                                onClick = { showDeleteTransactionsDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Error
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Error.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.plan_details_delete_transactions_button))
                            }
                        }
                    }

                    // Import via API card
                    if (plan.exchange.supportsApiImport) {
                        item {
                            Card(
                                onClick = { viewModel.showImportDialog() },
                                enabled = !uiState.isApiImporting,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = accentColor(),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.import_api_title),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (uiState.isApiImporting) {
                                            Text(
                                                text = uiState.apiImportProgress,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = accentColor()
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.import_api_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (uiState.isApiImporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Import History card (only for exchanges that support CSV import)
                    if (onNavigateToImport != null && plan.exchange.supportsImport) {
                        item {
                            Card(
                                onClick = onNavigateToImport,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        tint = accentColor(),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.import_csv_title),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = stringResource(R.string.import_csv_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Danger Zone
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_danger_zone),
                            style = MaterialTheme.typography.labelMedium,
                            color = Error,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .semantics { heading() }
                        )
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

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportConfigDialog(
    sinceMillis: Long?,
    onSinceDateChanged: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = sinceMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onSinceDateChanged(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.common_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_api_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_api_from_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (sinceMillis != null)
                                dateFormatter.format(Instant.ofEpochMilli(sinceMillis))
                            else
                                stringResource(R.string.import_api_all_history),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (sinceMillis != null) {
                            IconButton(
                                onClick = { onSinceDateChanged(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.import_api_all_history),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.import_api_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
