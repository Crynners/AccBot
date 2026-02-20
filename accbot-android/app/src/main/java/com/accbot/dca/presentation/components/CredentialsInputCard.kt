package com.accbot.dca.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

/**
 * Reusable credentials input card component.
 * Used in AddPlanScreen and AddExchangeScreen to eliminate code duplication.
 *
 * Features:
 * - Client ID field for Coinmate
 * - API Key/Secret fields with visibility toggle
 * - Passphrase field for KuCoin
 * - QR scanner buttons for each field
 * - Security note at the bottom
 * - Optional error message display
 *
 * @param exchange The selected exchange (determines which fields to show)
 * @param clientId Current client ID value
 * @param apiKey Current API key value
 * @param apiSecret Current API secret value
 * @param passphrase Current passphrase value
 * @param onClientIdChange Callback when client ID changes
 * @param onApiKeyChange Callback when API key changes
 * @param onApiSecretChange Callback when API secret changes
 * @param onPassphraseChange Callback when passphrase changes
 * @param errorMessage Optional error message to display
 * @param isValidating Whether validation is in progress (shows loading indicator)
 * @param modifier Modifier for the card
 */
@Composable
fun CredentialsInputCard(
    exchange: Exchange,
    clientId: String,
    apiKey: String,
    apiSecret: String,
    passphrase: String,
    onClientIdChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiSecretChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    isValidating: Boolean = false
) {
    var showSecret by remember { mutableStateOf(false) }
    var showMultiScanner by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Determine field requirements based on exchange
    val needsClientId = exchange == Exchange.COINMATE
    val needsPassphrase = exchange == Exchange.KUCOIN || exchange == Exchange.COINBASE
    // The last field gets ImeAction.Done, others get ImeAction.Next
    val lastFieldImeAction = ImeAction.Done
    val lastFieldKeyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
    val nextFieldKeyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    // Build target fields for multi-field scanner
    val targetFields = remember(exchange) {
        buildList {
            when {
                needsClientId -> {
                    add(ScanTargetField(label = "Client ID", key = "clientId"))
                    add(ScanTargetField(label = "Public Key", key = "apiKey"))
                    add(ScanTargetField(label = "Private Key", key = "apiSecret"))
                }
                needsPassphrase -> {
                    add(ScanTargetField(label = "API Key", key = "apiKey"))
                    add(ScanTargetField(label = "API Secret", key = "apiSecret"))
                    add(ScanTargetField(label = "Passphrase", key = "passphrase"))
                }
                else -> {
                    add(ScanTargetField(label = "API Key", key = "apiKey"))
                    add(ScanTargetField(label = "API Secret", key = "apiSecret"))
                }
            }
        }
    }

    if (showMultiScanner) {
        MultiFieldScannerDialog(
            targetFields = targetFields,
            onDismiss = { showMultiScanner = false },
            onResult = { results ->
                results["clientId"]?.let { onClientIdChange(it) }
                results["apiKey"]?.let { onApiKeyChange(it) }
                results["apiSecret"]?.let { onApiSecretChange(it) }
                results["passphrase"]?.let { onPassphraseChange(it) }
                showMultiScanner = false
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scan All Credentials button
            OutlinedButton(
                onClick = { showMultiScanner = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isValidating,
                border = BorderStroke(1.dp, accentColor()),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = accentColor(),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.credentials_scan_all),
                    color = accentColor()
                )
            }

            // Coinmate requires separate Client ID
            if (needsClientId) {
                OutlinedTextField(
                    value = clientId,
                    onValueChange = onClientIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.credentials_client_id)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = nextFieldKeyboardActions,
                    isError = errorMessage != null && clientId.isBlank(),
                    enabled = !isValidating,
                    supportingText = if (clientId.isBlank()) {
                        { Text(stringResource(R.string.credentials_required_for, exchange.displayName)) }
                    } else null,
                    trailingIcon = {
                        QrScannerButton(onScanResult = onClientIdChange)
                    }
                )
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(if (needsClientId) R.string.credentials_public_key else R.string.credentials_api_key)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = nextFieldKeyboardActions,
                isError = errorMessage != null,
                enabled = !isValidating,
                trailingIcon = {
                    QrScannerButton(onScanResult = onApiKeyChange)
                }
            )

            OutlinedTextField(
                value = apiSecret,
                onValueChange = onApiSecretChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(if (needsClientId) R.string.credentials_private_key else R.string.credentials_api_secret)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (needsPassphrase) ImeAction.Next else lastFieldImeAction),
                keyboardActions = if (needsPassphrase) nextFieldKeyboardActions else lastFieldKeyboardActions,
                visualTransformation = if (showSecret) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                isError = errorMessage != null,
                enabled = !isValidating,
                trailingIcon = {
                    Row {
                        QrScannerButton(onScanResult = onApiSecretChange)
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(R.string.credentials_toggle_visibility)
                            )
                        }
                    }
                }
            )

            // KuCoin requires passphrase
            if (needsPassphrase) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.credentials_passphrase)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = lastFieldImeAction),
                    keyboardActions = lastFieldKeyboardActions,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = errorMessage != null && passphrase.isBlank(),
                    enabled = !isValidating,
                    supportingText = if (passphrase.isBlank()) {
                        { Text(stringResource(R.string.credentials_required_for, exchange.displayName)) }
                    } else null,
                    trailingIcon = {
                        QrScannerButton(onScanResult = onPassphraseChange)
                    }
                )
            }

            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
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
                    text = stringResource(R.string.credentials_stored_locally),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Check if credentials are complete for the given exchange.
 * Useful for enabling/disabling submit buttons.
 */
fun areCredentialsComplete(
    exchange: Exchange,
    clientId: String,
    apiKey: String,
    apiSecret: String,
    passphrase: String
): Boolean {
    if (apiKey.isBlank() || apiSecret.isBlank()) return false
    if (exchange == Exchange.COINMATE && clientId.isBlank()) return false
    if ((exchange == Exchange.KUCOIN || exchange == Exchange.COINBASE) && passphrase.isBlank()) return false
    return true
}
