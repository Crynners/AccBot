package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
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
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.presentation.components.*
import com.accbot.dca.presentation.screens.plans.PlanConfigRow
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal

@Composable
private fun PlanDetailsPreviewContent() {
    val state = SampleData.planDetailsUiState
    val plan = state.plan!!

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.plan_details_title),
                onNavigateBack = {},
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Plan header
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = plan.exchange.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.plan_details_active),
                                fontWeight = FontWeight.SemiBold,
                                color = successColor()
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = true,
                                onCheckedChange = {},
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = successColor(),
                                    checkedTrackColor = successColor().copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            // Configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            style = MaterialTheme.typography.titleSmall
                        )
                        PlanConfigRow(
                            icon = Icons.Default.AttachMoney,
                            label = stringResource(R.string.plan_details_amount),
                            value = "${plan.amount} ${plan.fiat}"
                        )
                        PlanConfigRow(
                            icon = Icons.Default.Schedule,
                            label = stringResource(R.string.plan_details_frequency),
                            value = stringResource(plan.frequency.displayNameRes)
                        )
                        PlanConfigRow(
                            icon = Icons.Default.Timer,
                            label = stringResource(R.string.plan_details_next_execution),
                            value = state.timeUntilNextExecution
                        )
                    }
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
                        value = "${NumberFormatters.fiat(state.totalInvested)} ${plan.fiat}",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.plan_details_accumulated),
                        value = "${NumberFormatters.crypto(state.totalCrypto)} ${plan.crypto}",
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
                        value = "${NumberFormatters.fiat(state.averagePrice)} ${plan.fiat}",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.plan_details_transactions),
                        value = state.transactionCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Performance
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            style = MaterialTheme.typography.titleSmall
                        )
                        PlanConfigRow(
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            label = stringResource(R.string.plan_details_current_price),
                            value = "${NumberFormatters.fiat(state.currentPrice!!)} ${plan.fiat}"
                        )
                        PlanConfigRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = stringResource(R.string.plan_details_current_value),
                            value = "${NumberFormatters.fiat(state.currentValue!!)} ${plan.fiat}"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = successColor(),
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
                                    text = "+${NumberFormatters.fiat(state.roiAbsolute!!)} ${plan.fiat} (+${NumberFormatters.percent(state.roiPercent!!)}%)",
                                    fontWeight = FontWeight.Medium,
                                    color = successColor()
                                )
                            }
                        }
                    }
                }
            }

            // Recent transactions
            item {
                SectionHeader(title = stringResource(R.string.plan_details_recent_transactions))
            }

            items(state.transactions.take(5), key = { it.id }) { transaction ->
                TransactionCard(transaction = transaction)
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "PlanDetails_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PlanDetailsPhoneEn() {
    AccBotTheme(darkTheme = true) { PlanDetailsPreviewContent() }
}

@PreviewTest
@Preview(name = "PlanDetails_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun PlanDetailsPhoneCs() {
    AccBotTheme(darkTheme = true) { PlanDetailsPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "PlanDetails_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PlanDetailsTablet7En() {
    AccBotTheme(darkTheme = true) { PlanDetailsPreviewContent() }
}

@PreviewTest
@Preview(name = "PlanDetails_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun PlanDetailsTablet7Cs() {
    AccBotTheme(darkTheme = true) { PlanDetailsPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "PlanDetails_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PlanDetailsTablet10En() {
    AccBotTheme(darkTheme = true) { PlanDetailsPreviewContent() }
}

@PreviewTest
@Preview(name = "PlanDetails_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun PlanDetailsTablet10Cs() {
    AccBotTheme(darkTheme = true) { PlanDetailsPreviewContent() }
}
