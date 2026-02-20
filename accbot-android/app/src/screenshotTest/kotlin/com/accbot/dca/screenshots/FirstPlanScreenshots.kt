package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.SelectableChip
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstPlanPreviewContent() {
    val exchange = Exchange.COINMATE
    val selectedCrypto = "BTC"
    val selectedFiat = "EUR"
    val amount = "50"
    val selectedFrequency = DcaFrequency.DAILY
    val quickAmounts = listOf("25", "50", "100", "250", "500")

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.first_plan_title)) },
                actions = {
                    TextButton(onClick = {}) {
                        Text(stringResource(R.string.common_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { 0.75f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.first_plan_setup),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.first_plan_setup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Crypto selection
            Text(
                text = stringResource(R.string.first_plan_what_to_buy),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                exchange.supportedCryptos.forEach { crypto ->
                    SelectableChip(
                        text = crypto,
                        selected = crypto == selectedCrypto,
                        onClick = {}
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Fiat selection
            Text(
                text = stringResource(R.string.first_plan_currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                exchange.supportedFiats.forEach { fiat ->
                    SelectableChip(
                        text = fiat,
                        selected = fiat == selectedFiat,
                        onClick = {}
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Amount
            Text(
                text = stringResource(R.string.add_plan_amount_per_purchase),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                quickAmounts.forEach { qa ->
                    SelectableChip(
                        text = "$qa $selectedFiat",
                        selected = qa == amount,
                        onClick = {}
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.first_plan_custom_amount)) },
                suffix = { Text(selectedFiat) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Frequency
            Text(
                text = stringResource(R.string.first_plan_how_often),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DcaFrequency.DAILY, DcaFrequency.WEEKLY).forEach { frequency ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {},
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedFrequency == frequency) {
                                successColor().copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(frequency.displayNameRes),
                                    fontWeight = if (selectedFrequency == frequency) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedFrequency == frequency) successColor() else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        if (frequency == DcaFrequency.DAILY) R.string.first_plan_recommended
                                        else R.string.first_plan_lower_frequency
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = selectedFrequency == frequency,
                                onClick = {},
                                colors = RadioButtonDefaults.colors(selectedColor = successColor())
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Summary card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = accentColor().copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.first_plan_your_plan),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.first_plan_summary,
                            amount, selectedFiat, selectedCrypto,
                            stringResource(selectedFrequency.displayNameRes).lowercase()
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor()
                )
            ) {
                Text(
                    text = stringResource(R.string.first_plan_create),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "FirstPlan_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun FirstPlanPhoneEn() {
    AccBotTheme(darkTheme = true) { FirstPlanPreviewContent() }
}

@PreviewTest
@Preview(name = "FirstPlan_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun FirstPlanPhoneCs() {
    AccBotTheme(darkTheme = true) { FirstPlanPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "FirstPlan_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun FirstPlanTablet7En() {
    AccBotTheme(darkTheme = true) { FirstPlanPreviewContent() }
}

@PreviewTest
@Preview(name = "FirstPlan_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun FirstPlanTablet7Cs() {
    AccBotTheme(darkTheme = true) { FirstPlanPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "FirstPlan_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun FirstPlanTablet10En() {
    AccBotTheme(darkTheme = true) { FirstPlanPreviewContent() }
}

@PreviewTest
@Preview(name = "FirstPlan_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun FirstPlanTablet10Cs() {
    AccBotTheme(darkTheme = true) { FirstPlanPreviewContent() }
}
