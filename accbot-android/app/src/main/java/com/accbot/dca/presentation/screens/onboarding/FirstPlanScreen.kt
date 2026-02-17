package com.accbot.dca.presentation.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.presentation.components.SelectableChip
import com.accbot.dca.R
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstPlanScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Quick amount presets
    val quickAmounts = listOf("25", "50", "100", "250", "500")

    // Simplified frequency options for onboarding
    val frequencyOptions = listOf(
        DcaFrequency.DAILY,
        DcaFrequency.WEEKLY
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.first_plan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSkip) {
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
            if (uiState.selectedExchange != null) {
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
                    uiState.selectedExchange!!.supportedCryptos.forEach { crypto ->
                        SelectableChip(
                            text = crypto,
                            selected = crypto == uiState.selectedCrypto,
                            onClick = { viewModel.selectCrypto(crypto) }
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
                    uiState.selectedExchange!!.supportedFiats.forEach { fiat ->
                        SelectableChip(
                            text = fiat,
                            selected = fiat == uiState.selectedFiat,
                            onClick = { viewModel.selectFiat(fiat) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Amount input with presets
                Text(
                    text = stringResource(R.string.add_plan_amount_per_purchase),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick amount buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    quickAmounts.forEach { amount ->
                        SelectableChip(
                            text = "$amount ${uiState.selectedFiat}",
                            selected = uiState.amount == amount,
                            onClick = { viewModel.setAmount(amount) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.amount,
                    onValueChange = viewModel::setAmount,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.first_plan_custom_amount)) },
                    suffix = { Text(uiState.selectedFiat) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = uiState.amountBelowMinimum,
                    supportingText = uiState.minOrderSize?.let { min ->
                        {
                            Text(
                                text = stringResource(R.string.min_order_size, min.toPlainString(), uiState.selectedFiat),
                                color = if (uiState.amountBelowMinimum) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Frequency selection
                Text(
                    text = stringResource(R.string.first_plan_how_often),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    frequencyOptions.forEach { frequency ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectFrequency(frequency) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.selectedFrequency == frequency) {
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
                                        fontWeight = if (uiState.selectedFrequency == frequency) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = if (uiState.selectedFrequency == frequency) {
                                            successColor()
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = stringResource(if (frequency == DcaFrequency.DAILY) {
                                            R.string.first_plan_recommended
                                        } else {
                                            R.string.first_plan_lower_frequency
                                        }),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = uiState.selectedFrequency == frequency,
                                    onClick = { viewModel.selectFrequency(frequency) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = successColor()
                                    )
                                )
                            }
                        }
                    }
                }

                // Error message
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                            text = stringResource(R.string.first_plan_summary, uiState.amount, uiState.selectedFiat, uiState.selectedCrypto, stringResource(uiState.selectedFrequency.displayNameRes).lowercase()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // No exchange configured
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.first_plan_no_exchange),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.first_plan_no_exchange_desc),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.createFirstPlan(onSuccess = onContinue)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState.selectedExchange != null &&
                        uiState.amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                        !uiState.amountBelowMinimum &&
                        !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor()
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.first_plan_create),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
