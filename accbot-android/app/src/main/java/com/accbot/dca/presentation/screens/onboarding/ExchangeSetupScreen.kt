package com.accbot.dca.presentation.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.QrScannerButton
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
    val uiState by viewModel.uiState.collectAsState()
    var showSecretField by remember { mutableStateOf(false) }

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
                fontSize = 24.sp,
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
                            text = stringResource(R.string.exchange_setup_api_credentials),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall
                        )

                        // Coinmate requires separate Client ID
                        if (uiState.selectedExchange == Exchange.COINMATE) {
                            OutlinedTextField(
                                value = uiState.clientId,
                                onValueChange = viewModel::setClientId,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.credentials_client_id)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = uiState.credentialsError != null,
                                trailingIcon = {
                                    QrScannerButton(onScanResult = viewModel::setClientId)
                                }
                            )
                        }

                        OutlinedTextField(
                            value = uiState.apiKey,
                            onValueChange = viewModel::setApiKey,
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(stringResource(if (uiState.selectedExchange == Exchange.COINMATE) R.string.credentials_public_key else R.string.credentials_api_key))
                            },
                            singleLine = true,
                            isError = uiState.credentialsError != null,
                            trailingIcon = {
                                QrScannerButton(onScanResult = viewModel::setApiKey)
                            }
                        )

                        OutlinedTextField(
                            value = uiState.apiSecret,
                            onValueChange = viewModel::setApiSecret,
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(stringResource(if (uiState.selectedExchange == Exchange.COINMATE) R.string.credentials_private_key else R.string.credentials_api_secret))
                            },
                            singleLine = true,
                            visualTransformation = if (showSecretField) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                Row {
                                    QrScannerButton(onScanResult = viewModel::setApiSecret)
                                    IconButton(onClick = { showSecretField = !showSecretField }) {
                                        Icon(
                                            imageVector = if (showSecretField) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = stringResource(R.string.credentials_toggle_visibility)
                                        )
                                    }
                                }
                            },
                            isError = uiState.credentialsError != null
                        )

                        // Passphrase for exchanges that need it (only KuCoin)
                        if (uiState.selectedExchange == Exchange.KUCOIN) {
                            OutlinedTextField(
                                value = uiState.passphrase,
                                onValueChange = viewModel::setPassphrase,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.credentials_passphrase)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                isError = uiState.credentialsError != null,
                                trailingIcon = {
                                    QrScannerButton(onScanResult = viewModel::setPassphrase)
                                }
                            )
                        }

                        // Error message
                        if (uiState.credentialsError != null) {
                            Text(
                                text = uiState.credentialsError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Security note
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = successColor(),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.credentials_encrypted_aes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
private fun ExchangeGridItem(
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
            // Exchange initial as icon placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) successColor().copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                val successCol = successColor()
                Text(
                    text = exchange.displayName.first().toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isSelected) successCol else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
