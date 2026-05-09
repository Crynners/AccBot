package com.accbot.dca.presentation.screens.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.ErrorState
import com.accbot.dca.presentation.components.LoadingState
import com.accbot.dca.presentation.components.WarningOrange
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.DateFormatters
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsScreen(
    transactionId: Long,
    onNavigateBack: () -> Unit,
    viewModel: TransactionDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(transactionId) {
        viewModel.loadTransaction(transactionId)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.transaction_details_title),
                onNavigateBack = onNavigateBack
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
                    LoadingState(message = stringResource(R.string.transaction_details_loading))
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
                        onRetry = { viewModel.loadTransaction(transactionId) }
                    )
                }
            }
            uiState.transaction != null -> {
                val transaction = uiState.transaction!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Status header
                    StatusHeader(status = transaction.status)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Amount card
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
                            Text(
                                text = stringResource(R.string.transaction_details_amount_received),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "+${NumberFormatters.crypto(transaction.cryptoAmount)} ${transaction.crypto}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = successColor()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "-${NumberFormatters.fiat(transaction.fiatAmount)} ${transaction.fiat}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Details card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.transaction_details_section),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )

                            DetailRow(
                                icon = Icons.Default.AccountBalance,
                                label = stringResource(R.string.transaction_details_exchange),
                                value = transaction.exchange.displayName
                            )

                            DetailRow(
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                label = stringResource(R.string.transaction_details_price),
                                value = "${NumberFormatters.fiat(transaction.price)} ${transaction.fiat}/${transaction.crypto}"
                            )

                            DetailRow(
                                icon = Icons.Default.Receipt,
                                label = stringResource(R.string.transaction_details_fee),
                                value = "${NumberFormatters.fiatFee(transaction.fee)} ${transaction.feeAsset.ifEmpty { transaction.fiat }}"
                            )

                            val atSeparator = stringResource(R.string.transaction_details_at)
                            DetailRow(
                                icon = Icons.Default.Schedule,
                                label = stringResource(R.string.transaction_details_date_time),
                                value = DateFormatters.fullDate.format(transaction.executedAt) +
                                    " $atSeparator " + DateFormatters.timeOnly.format(transaction.executedAt)
                            )

                            if (transaction.exchangeOrderId != null) {
                                DetailRowWithCopy(
                                    icon = Icons.Default.Tag,
                                    label = stringResource(R.string.transaction_details_order_id),
                                    value = transaction.exchangeOrderId,
                                    context = context
                                )
                            }
                        }
                    }

                    // SELL-specific section (limit price, fill progress, cancel)
                    if (transaction.side == TransactionSide.SELL) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SellDetailsCard(transaction = transaction)

                        if (transaction.status == TransactionStatus.PENDING ||
                            transaction.status == TransactionStatus.PARTIAL
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            var showConfirm by remember { mutableStateOf(false) }
                            Button(
                                onClick = { showConfirm = true },
                                enabled = !uiState.isCancelling,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                if (uiState.isCancelling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.transaction_details_cancel_order))
                            }

                            if (showConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showConfirm = false },
                                    title = { Text(stringResource(R.string.transaction_details_cancel_confirm_title)) },
                                    text = { Text(stringResource(R.string.transaction_details_cancel_confirm_text)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showConfirm = false
                                            viewModel.cancelOrder(transaction.id)
                                        }) {
                                            Text(
                                                stringResource(R.string.transaction_details_cancel_order),
                                                color = Error
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showConfirm = false }) {
                                            Text(stringResource(R.string.common_back))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Error message card (if failed)
                    if (transaction.status == TransactionStatus.FAILED && transaction.errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Error.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = Error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.transaction_details_error_message),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Error
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = transaction.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Warning message card (retry warnings)
                    if (transaction.warningMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = WarningOrange.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = WarningOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.transaction_details_warning_message),
                                        fontWeight = FontWeight.SemiBold,
                                        color = WarningOrange
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = transaction.warningMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(status: TransactionStatus) {
    val successCol = successColor()
    val accentCol = accentColor()
    val (icon, color, text) = when (status) {
        TransactionStatus.COMPLETED -> Triple(Icons.Default.CheckCircle, successCol, stringResource(R.string.transaction_status_completed))
        TransactionStatus.FAILED -> Triple(Icons.Default.Error, Error, stringResource(R.string.transaction_status_failed))
        TransactionStatus.PENDING -> Triple(Icons.Default.Schedule, accentCol, stringResource(R.string.transaction_status_pending))
        TransactionStatus.PARTIAL -> Triple(Icons.Default.RemoveCircle, Color(0xFFFFA500), stringResource(R.string.transaction_status_partial))
        TransactionStatus.CANCELLED -> Triple(Icons.Default.Cancel, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.transaction_status_cancelled))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
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
private fun SellDetailsCard(transaction: com.accbot.dca.domain.model.Transaction) {
    val requested = transaction.requestedCryptoAmount ?: BigDecimal.ZERO
    val filled = transaction.cryptoAmount
    val progressPct = if (requested > BigDecimal.ZERO) {
        filled.divide(requested, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toInt()
    } else 0
    val showAvgFill = filled.signum() > 0 && transaction.price.signum() > 0

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
                text = stringResource(R.string.transaction_details_sell_section),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )

            DetailRow(
                icon = Icons.Default.PriceCheck,
                label = stringResource(R.string.transaction_details_limit_price),
                value = transaction.limitPrice?.let {
                    "${NumberFormatters.fiat(it)} ${transaction.fiat}/${transaction.crypto}"
                } ?: "-"
            )

            DetailRow(
                icon = Icons.Default.Done,
                label = stringResource(R.string.transaction_details_filled),
                value = "${NumberFormatters.crypto(filled)} / ${NumberFormatters.crypto(requested)} ${transaction.crypto} (${progressPct}%)"
            )

            if (showAvgFill) {
                DetailRow(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    label = stringResource(R.string.transaction_details_avg_fill_price),
                    value = "${NumberFormatters.fiat(transaction.price)} ${transaction.fiat}/${transaction.crypto}"
                )
            }
        }
    }
}

@Composable
private fun DetailRowWithCopy(
    icon: ImageVector,
    label: String,
    value: String,
    context: Context
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
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Order ID", value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.transaction_details_copied), Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.common_copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
