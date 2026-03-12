package com.accbot.dca.presentation.screens.backup

import android.graphics.Bitmap
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accbot.dca.R
import com.accbot.dca.domain.model.EncryptionMode
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.ui.theme.Warning
import java.io.File
import java.io.FileWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupExportScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.backup_export_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step indicator
            BackupStepIndicator(
                steps = listOf(
                    stringResource(R.string.backup_step_select_data),
                    stringResource(R.string.backup_step_encryption),
                    stringResource(R.string.backup_step_result)
                ),
                currentStep = uiState.wizardStep.ordinal,
                onStepClick = if (uiState.wizardStep == ExportWizardStep.RESULT) null
                    else { stepIndex -> viewModel.goToStep(ExportWizardStep.entries[stepIndex]) }
            )

            when (uiState.wizardStep) {
                ExportWizardStep.SELECT_DATA -> SelectDataStep(uiState, viewModel)
                ExportWizardStep.ENCRYPTION -> EncryptionStep(uiState, viewModel)
                ExportWizardStep.RESULT -> ResultStep(uiState, qrBitmap, context)
            }

            // Error display
            uiState.error?.let { error ->
                val message = when (error) {
                    "password_too_short" -> stringResource(R.string.backup_password_too_short)
                    "password_mismatch" -> stringResource(R.string.backup_password_mismatch)
                    "no_passphrase" -> stringResource(R.string.backup_no_passphrase)
                    "credentials_require_encryption" -> stringResource(R.string.backup_credentials_require_encryption)
                    else -> error
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelectDataStep(
    uiState: BackupExportUiState,
    viewModel: BackupExportViewModel
) {
    val counts = uiState.dataCounts

    Text(
        text = stringResource(R.string.backup_step_select_data),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    // Plans - always included
    DataOptionCard(
        title = stringResource(R.string.backup_include_plans),
        subtitle = stringResource(R.string.backup_include_plans_hint),
        count = counts.planCount,
        checked = true,
        enabled = false,
        onCheckedChange = {}
    )

    // Settings - always included
    DataOptionCard(
        title = stringResource(R.string.backup_include_settings),
        subtitle = stringResource(R.string.backup_include_plans_hint),
        count = null,
        checked = true,
        enabled = false,
        onCheckedChange = {}
    )

    // Credentials
    DataOptionCard(
        title = stringResource(R.string.backup_include_credentials),
        subtitle = stringResource(R.string.backup_include_credentials_hint),
        count = counts.credentialCount,
        checked = uiState.includeCredentials,
        enabled = counts.credentialCount > 0,
        onCheckedChange = { viewModel.setIncludeCredentials(it) }
    )

    // Transactions
    DataOptionCard(
        title = stringResource(R.string.backup_include_transactions),
        subtitle = null,
        count = counts.transactionCount,
        checked = uiState.includeTransactions,
        enabled = counts.transactionCount > 0,
        onCheckedChange = { viewModel.setIncludeTransactions(it) }
    )

    // Notifications
    DataOptionCard(
        title = stringResource(R.string.backup_include_notifications),
        subtitle = null,
        count = counts.notificationCount,
        checked = uiState.includeNotifications,
        enabled = counts.notificationCount > 0,
        onCheckedChange = { viewModel.setIncludeNotifications(it) }
    )

    // Withdrawals
    DataOptionCard(
        title = stringResource(R.string.backup_include_withdrawals),
        subtitle = null,
        count = counts.withdrawalCount,
        checked = uiState.includeWithdrawals,
        enabled = counts.withdrawalCount > 0,
        onCheckedChange = { viewModel.setIncludeWithdrawals(it) }
    )

    Button(
        onClick = { viewModel.proceedToEncryption() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.common_next))
    }
}

@Composable
private fun DataOptionCard(
    title: String,
    subtitle: String?,
    count: Int?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EncryptionStep(
    uiState: BackupExportUiState,
    viewModel: BackupExportViewModel
) {
    Text(
        text = stringResource(R.string.backup_step_encryption),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    // Mode selection
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = uiState.encryptionMode == EncryptionMode.Password,
            onClick = { viewModel.setEncryptionMode(EncryptionMode.Password) },
            label = { Text(stringResource(R.string.backup_encryption_password)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = uiState.encryptionMode == EncryptionMode.Seed,
            onClick = { viewModel.setEncryptionMode(EncryptionMode.Seed) },
            label = { Text(stringResource(R.string.backup_encryption_seed)) },
            modifier = Modifier.weight(1f)
        )
    }

    if (uiState.encryptionMode == EncryptionMode.Password) {
        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            label = { Text(stringResource(R.string.backup_enter_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.confirmPassword,
            onValueChange = { viewModel.setConfirmPassword(it) },
            label = { Text(stringResource(R.string.backup_confirm_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // Seed mode
        if (uiState.seedWords.isEmpty()) {
            Button(
                onClick = { viewModel.generateSeed() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Key, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_generate_seed))
            }
        } else {
            // Display seed words
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Warning.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backup_seed_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Warning
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Display as 4x3 grid
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0 until 4) {
                                val idx = row * 4 + col
                                Text(
                                    text = "${idx + 1}. ${uiState.seedWords[idx]}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.seedConfirmed,
                    onCheckedChange = { if (it) viewModel.confirmSeed() }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.backup_seed_confirm),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Navigation buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.goToStep(ExportWizardStep.SELECT_DATA) },
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.common_previous))
        }
        Button(
            onClick = { viewModel.createBackup() },
            modifier = Modifier.weight(1f),
            enabled = !uiState.isCreating && (
                uiState.encryptionMode == EncryptionMode.Password ||
                (uiState.seedWords.isNotEmpty() && uiState.seedConfirmed)
            )
        ) {
            if (uiState.isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (uiState.isCreating) stringResource(R.string.backup_creating) else stringResource(R.string.backup_export_title))
        }
    }
}

@Composable
private fun ResultStep(
    uiState: BackupExportUiState,
    qrBitmap: Bitmap?,
    context: android.content.Context
) {
    Text(
        text = stringResource(R.string.backup_export_success),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    // Share file button
    Button(
        onClick = {
            uiState.resultJson?.let { json ->
                try {
                    val file = File(context.cacheDir, uiState.resultFileName ?: "accbot_backup.json")
                    FileWriter(file).use { writer -> writer.write(json) }

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.backup_share_file)))
                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Share, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.backup_share_file))
    }

    // QR code display (if feasible)
    qrBitmap?.let { bitmap ->
        Text(
            text = stringResource(R.string.backup_show_qr),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.backup_show_qr),
                    modifier = Modifier.size(256.dp)
                )
            }
        }
    } ?: run {
        Text(
            text = stringResource(R.string.backup_qr_too_large),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

