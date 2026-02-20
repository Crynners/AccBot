package com.accbot.dca.presentation.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.components.SandboxModeIndicator
import com.accbot.dca.R
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeSetupScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exchange_setup_title)) },
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
                progress = { 0.5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.exchange_setup_choose),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.exchange_setup_choose_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sandbox mode indicator
            if (uiState.isSandboxMode) {
                SandboxModeIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Exchange grid - using Column with chunked() to avoid nested scroll
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.availableExchanges.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { exchange ->
                            ExchangeGridItem(
                                exchange = exchange,
                                isSelected = uiState.selectedExchange == exchange,
                                onClick = { viewModel.selectExchange(exchange) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Spacer for odd count to maintain layout
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Credentials input (only show when exchange is selected)
            if (uiState.selectedExchange != null) {
                Spacer(modifier = Modifier.height(24.dp))

                CredentialsInputCard(
                    exchange = uiState.selectedExchange!!,
                    clientId = uiState.clientId,
                    apiKey = uiState.apiKey,
                    apiSecret = uiState.apiSecret,
                    passphrase = uiState.passphrase,
                    onClientIdChange = viewModel::setClientId,
                    onApiKeyChange = viewModel::setApiKey,
                    onApiSecretChange = viewModel::setApiSecret,
                    onPassphraseChange = viewModel::setPassphrase,
                    errorMessage = uiState.credentialsError,
                    isValidating = uiState.isValidatingCredentials
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue button
            Button(
                onClick = {
                    if (uiState.selectedExchange != null) {
                        viewModel.validateAndSaveCredentials(onSuccess = onContinue)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState.selectedExchange != null &&
                        uiState.apiKey.isNotBlank() &&
                        uiState.apiSecret.isNotBlank() &&
                        (uiState.selectedExchange != Exchange.COINMATE || uiState.clientId.isNotBlank()) &&
                        !uiState.isValidatingCredentials,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor()
                )
            ) {
                if (uiState.isValidatingCredentials) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.exchange_setup_connect),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExchangeGridItem(
    exchange: Exchange,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                successColor().copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(successColor())
            )
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(exchange.logoRes),
                contentDescription = exchange.displayName,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = exchange.displayName,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) successColor() else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
