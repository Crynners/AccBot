package com.accbot.dca.presentation.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.R
import com.accbot.dca.presentation.plan.PlanFormContent
import com.accbot.dca.presentation.ui.theme.accentColor
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstPlanScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.first_plan_title), fontWeight = FontWeight.Bold) },
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
                style = MaterialTheme.typography.titleLarge,
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

            if (uiState.selectedExchange != null) {
                // Plan form (crypto, fiat, amount, frequency, strategy, withdrawal, target)
                PlanFormContent(
                    state = uiState.planForm,
                    availableCryptos = uiState.selectedExchange!!.supportedCryptos,
                    availableFiats = uiState.selectedExchange!!.supportedFiats,
                    onCryptoSelected = viewModel.planForm::selectCrypto,
                    onFiatSelected = viewModel.planForm::selectFiat,
                    onAmountChanged = viewModel.planForm::setAmount,
                    onFrequencySelected = viewModel.planForm::selectFrequency,
                    onCronExpressionChanged = viewModel.planForm::setCronExpression,
                    onStrategySelected = viewModel.planForm::selectStrategy,
                    onWithdrawalEnabledChanged = viewModel.planForm::setWithdrawalEnabled,
                    onWithdrawalAddressChanged = viewModel.planForm::setWithdrawalAddress,
                    onTargetAmountChanged = viewModel.planForm::setTargetAmount,
                    errorMessage = uiState.error
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                                uiState.planForm.amount,
                                uiState.planForm.selectedFiat,
                                uiState.planForm.selectedCrypto,
                                stringResource(uiState.planForm.selectedFrequency.displayNameRes).lowercase()
                            ),
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
                        uiState.planForm.isFormValid &&
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
