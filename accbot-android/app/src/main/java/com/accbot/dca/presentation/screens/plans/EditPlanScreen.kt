package com.accbot.dca.presentation.screens.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.LoadingState
import com.accbot.dca.presentation.components.ErrorState
import com.accbot.dca.presentation.plan.PlanFormContent
import com.accbot.dca.presentation.ui.theme.accentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanScreen(
    planId: Long,
    onNavigateBack: () -> Unit,
    onPlanUpdated: () -> Unit,
    viewModel: EditPlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardAlert by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (uiState.hasChanges) {
            showDiscardAlert = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = uiState.hasChanges) {
        showDiscardAlert = true
    }

    LaunchedEffect(planId) {
        viewModel.loadPlan(planId)
    }

    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            title = { Text(stringResource(R.string.common_discard_changes_title)) },
            text = { Text(stringResource(R.string.common_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardAlert = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.common_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAlert = false }) {
                    Text(stringResource(R.string.common_keep_editing))
                }
            }
        )
    }

    // Confirmation dialog when disabling sells while open sell orders exist on the exchange.
    // See EditPlanViewModel.setAllowSells for the trigger.
    uiState.showDisableSellsDialog?.let { openCount ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDisableSellsDialog() },
            title = { Text(stringResource(R.string.edit_plan_disable_sells_dialog_title)) },
            text = {
                Text(stringResource(R.string.edit_plan_disable_sells_dialog_text, openCount))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDisableSells() }) {
                    Text(stringResource(R.string.edit_plan_disable_sells_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDisableSellsDialog() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.edit_plan_title),
                onNavigateBack = handleBack
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
            uiState.error != null && !uiState.isSaving -> {
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
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.TopCenter
                ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // Plan info (read-only)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.edit_plan_details),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.edit_plan_pair_info, uiState.crypto, uiState.fiat, uiState.exchangeName),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.edit_plan_cannot_change),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Plan form (amount, frequency, strategy, withdrawal, target)
                    item {
                        PlanFormContent(
                            state = uiState.planForm,
                            availableCryptos = listOf(uiState.crypto),
                            availableFiats = listOf(uiState.fiat),
                            showCryptoFiatSelection = false,
                            showNameField = false,
                            onNameChanged = viewModel.planForm::setName,
                            onCryptoSelected = viewModel.planForm::selectCrypto,
                            onFiatSelected = viewModel.planForm::selectFiat,
                            onAmountChanged = viewModel.planForm::setAmount,
                            onFrequencySelected = viewModel.planForm::selectFrequency,
                            onCronExpressionChanged = viewModel.planForm::setCronExpression,
                            onStrategySelected = viewModel.planForm::selectStrategy,
                            onWithdrawalEnabledChanged = viewModel.planForm::setWithdrawalEnabled,
                            onWithdrawalAddressChanged = viewModel.planForm::setWithdrawalAddress,
                            onTargetAmountChanged = viewModel.planForm::setTargetAmount,
                            exchange = uiState.exchange,
                            errorMessage = if (uiState.isSaving) uiState.error else null,
                            showSellSection = uiState.tradingEnabled,
                            onAllowSellsChanged = viewModel::setAllowSells,
                            onTargetProfitAmountChanged = viewModel.planForm::setTargetProfitAmount
                        )
                    }

                    // Save button
                    item {
                        Button(
                            onClick = { viewModel.savePlan(onPlanUpdated) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = uiState.isValid && !uiState.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.edit_plan_save),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
                } // Box
            }
        }
    }
}
