package com.accbot.dca.presentation.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.presentation.components.SelectableChip
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.NumberFormatters
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTransactionDetails: ((Long) -> Unit)? = null,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    // Handle export data - write to file and share (UI layer handles Context)
    LaunchedEffect(uiState.exportData) {
        uiState.exportData?.let { exportData ->
            try {
                val file = File(context.cacheDir, exportData.fileName)
                FileWriter(file).use { writer ->
                    writer.write(exportData.content)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.history_export_transactions)))
                Toast.makeText(context, context.getString(R.string.history_export_started), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: context.getString(R.string.history_export_failed), Toast.LENGTH_SHORT).show()
            }
            viewModel.clearExportState()
        }
    }

    // Show toast on export error
    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.clearExportState()
        }
    }

    // Filter bottom sheet
    if (uiState.showFilterSheet) {
        FilterBottomSheet(
            currentFilter = uiState.filter,
            availableCryptos = uiState.availableCryptos,
            availableExchanges = uiState.availableExchanges,
            onApplyFilter = { filter ->
                viewModel.setFilter(filter)
                viewModel.hideFilterSheet()
            },
            onClearFilter = {
                viewModel.clearFilter()
                viewModel.hideFilterSheet()
            },
            onDismiss = { viewModel.hideFilterSheet() }
        )
    }

    // Delete transaction confirmation dialog
    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(transaction)
                        transactionToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.common_delete), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val hasActiveFilter = uiState.filter.crypto != null ||
            uiState.filter.exchange != null ||
            uiState.filter.status != null ||
            uiState.filter.dateFrom != null ||
            uiState.filter.dateTo != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // Sort button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.history_sort))
                        }
                        SortDropdownMenu(
                            expanded = showSortMenu,
                            currentSort = uiState.sortOption,
                            onSortSelected = { option ->
                                viewModel.setSortOption(option)
                                showSortMenu = false
                            },
                            onDismiss = { showSortMenu = false }
                        )
                    }
                    // Filter button
                    IconButton(onClick = { viewModel.toggleFilterSheet() }) {
                        BadgedBox(
                            badge = {
                                if (hasActiveFilter) {
                                    Badge { Text("!") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.history_filter))
                        }
                    }
                    // Export button
                    IconButton(
                        onClick = { viewModel.exportToCsv() },
                        enabled = !uiState.isExporting && uiState.transactions.isNotEmpty()
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.history_export_csv))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Active filter chips
            if (hasActiveFilter) {
                ActiveFilterChips(
                    filter = uiState.filter,
                    onUpdateFilter = { viewModel.setFilter(it) },
                    onClearFilter = { viewModel.clearFilter() }
                )
            }

            if (uiState.transactions.isEmpty()) {
                EmptyHistoryState(
                    modifier = Modifier.fillMaxSize(),
                    hasFilter = hasActiveFilter
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(
                        items = uiState.transactions,
                        key = { it.id }
                    ) { transaction ->
                        SwipeableTransactionCard(
                            transaction = transaction,
                            onClick = {
                                onNavigateToTransactionDetails?.invoke(transaction.id)
                            },
                            onDelete = {
                                transactionToDelete = transaction
                            }
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.history_transactions_count, uiState.transactions.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sortLabels = mapOf(
        SortOption.DATE_NEWEST to stringResource(R.string.history_sort_date_newest),
        SortOption.DATE_OLDEST to stringResource(R.string.history_sort_date_oldest),
        SortOption.AMOUNT_HIGHEST to stringResource(R.string.history_sort_amount_highest),
        SortOption.AMOUNT_LOWEST to stringResource(R.string.history_sort_amount_lowest),
        SortOption.PRICE_HIGHEST to stringResource(R.string.history_sort_price_highest),
        SortOption.PRICE_LOWEST to stringResource(R.string.history_sort_price_lowest)
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        sortLabels.forEach { (option, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = { onSortSelected(option) },
                leadingIcon = if (option == currentSort) {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = accentColor()) }
                } else null
            )
        }
    }
}

@Composable
private fun ActiveFilterChips(
    filter: HistoryFilter,
    onUpdateFilter: (HistoryFilter) -> Unit,
    onClearFilter: () -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy")
            .withZone(ZoneId.systemDefault())
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filter.crypto?.let { crypto ->
            item {
                SelectableChip(
                    text = crypto,
                    selected = true,
                    onClick = { onUpdateFilter(filter.copy(crypto = null)) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_remove), modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        filter.exchange?.let { exchange ->
            item {
                SelectableChip(
                    text = exchange,
                    selected = true,
                    onClick = { onUpdateFilter(filter.copy(exchange = null)) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_remove), modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        filter.status?.let { status ->
            item {
                SelectableChip(
                    text = status.name,
                    selected = true,
                    onClick = { onUpdateFilter(filter.copy(status = null)) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_remove), modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        filter.dateFrom?.let { from ->
            item {
                SelectableChip(
                    text = "${stringResource(R.string.history_filter_date_from)}: ${dateFormatter.format(Instant.ofEpochMilli(from))}",
                    selected = true,
                    onClick = { onUpdateFilter(filter.copy(dateFrom = null)) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_remove), modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        filter.dateTo?.let { to ->
            item {
                SelectableChip(
                    text = "${stringResource(R.string.history_filter_date_to)}: ${dateFormatter.format(Instant.ofEpochMilli(to))}",
                    selected = true,
                    onClick = { onUpdateFilter(filter.copy(dateTo = null)) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_remove), modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        item {
            TextButton(onClick = onClearFilter) {
                Text(stringResource(R.string.history_clear_all), color = accentColor())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false // Return false to snap the card back; dialog handles confirmation
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipe-bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        TransactionCard(
            transaction = transaction,
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    currentFilter: HistoryFilter,
    availableCryptos: List<String>,
    availableExchanges: List<String>,
    onApplyFilter: (HistoryFilter) -> Unit,
    onClearFilter: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCrypto by remember { mutableStateOf(currentFilter.crypto) }
    var selectedExchange by remember { mutableStateOf(currentFilter.exchange) }
    var selectedStatus by remember { mutableStateOf(currentFilter.status) }
    var selectedDateFrom by remember { mutableStateOf(currentFilter.dateFrom) }
    var selectedDateTo by remember { mutableStateOf(currentFilter.dateTo) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy")
            .withZone(ZoneId.systemDefault())
    }

    // Date From picker dialog
    if (showDateFromPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateFrom)
        DatePickerDialog(
            onDismissRequest = { showDateFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateFrom = datePickerState.selectedDateMillis
                    showDateFromPicker = false
                }) {
                    Text(stringResource(R.string.common_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateFromPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Date To picker dialog
    if (showDateToPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTo)
        DatePickerDialog(
            onDismissRequest = { showDateToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateTo = datePickerState.selectedDateMillis
                    showDateToPicker = false
                }) {
                    Text(stringResource(R.string.common_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateToPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.history_filter_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Crypto filter
            if (availableCryptos.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.history_filter_crypto),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SelectableChip(
                            text = stringResource(R.string.common_all),
                            selected = selectedCrypto == null,
                            onClick = { selectedCrypto = null }
                        )
                    }
                    items(availableCryptos, key = { it }) { crypto ->
                        SelectableChip(
                            text = crypto,
                            selected = selectedCrypto == crypto,
                            onClick = { selectedCrypto = crypto }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Exchange filter
            if (availableExchanges.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.history_filter_exchange),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SelectableChip(
                            text = stringResource(R.string.common_all),
                            selected = selectedExchange == null,
                            onClick = { selectedExchange = null }
                        )
                    }
                    items(availableExchanges, key = { it }) { exchange ->
                        SelectableChip(
                            text = exchange,
                            selected = selectedExchange == exchange,
                            onClick = { selectedExchange = exchange }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Status filter
            Text(
                text = stringResource(R.string.history_filter_status),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SelectableChip(
                        text = stringResource(R.string.common_all),
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null }
                    )
                }
                items(TransactionStatus.entries, key = { it.name }) { status ->
                    SelectableChip(
                        text = status.name,
                        selected = selectedStatus == status,
                        onClick = { selectedStatus = status }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date Range filter
            Text(
                text = stringResource(R.string.history_filter_date_range),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showDateFromPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedDateFrom?.let { dateFormatter.format(Instant.ofEpochMilli(it)) }
                            ?: stringResource(R.string.history_filter_date_from),
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = { showDateToPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedDateTo?.let { dateFormatter.format(Instant.ofEpochMilli(it)) }
                            ?: stringResource(R.string.history_filter_date_to),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onClearFilter,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_clear))
                }
                Button(
                    onClick = {
                        onApplyFilter(
                            HistoryFilter(
                                crypto = selectedCrypto,
                                exchange = selectedExchange,
                                status = selectedStatus,
                                dateFrom = selectedDateFrom,
                                dateTo = selectedDateTo
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_apply))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EmptyHistoryState(
    modifier: Modifier = Modifier,
    hasFilter: Boolean = false
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (hasFilter) Icons.Default.SearchOff else Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasFilter) stringResource(R.string.history_empty_filtered_title) else stringResource(R.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasFilter) stringResource(R.string.history_empty_filtered_description) else stringResource(R.string.history_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (transaction.status) {
                            TransactionStatus.COMPLETED -> Icons.Default.CheckCircle
                            TransactionStatus.FAILED -> Icons.Default.Error
                            TransactionStatus.PENDING -> Icons.Default.Schedule
                            TransactionStatus.PARTIAL -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = when (transaction.status) {
                            TransactionStatus.COMPLETED -> successColor()
                            TransactionStatus.FAILED -> Error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${transaction.crypto}/${transaction.fiat}",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dateFormatter.format(transaction.executedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = transaction.exchange.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (transaction.status == TransactionStatus.FAILED && transaction.errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = transaction.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error,
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (transaction.status == TransactionStatus.COMPLETED) {
                    Text(
                        text = "+${NumberFormatters.crypto(transaction.cryptoAmount)}",
                        fontWeight = FontWeight.SemiBold,
                        color = successColor()
                    )
                    Text(
                        text = transaction.crypto,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "-${NumberFormatters.fiat(transaction.fiatAmount)} ${transaction.fiat}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Chevron to indicate clickable
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
