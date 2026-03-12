package com.accbot.dca.presentation.screens.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accbot.dca.R
import com.accbot.dca.domain.model.EncryptionMode
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.QrScannerDialog
import com.accbot.dca.presentation.components.SeedPhraseGrid
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.DateFormatters
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showQrScanner by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = readTextFromUri(context, uri)
            if (content != null) {
                viewModel.onFileSelected(content)
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onScanResult = { result ->
                viewModel.onQrScanned(result)
                showQrScanner = false
            }
        )
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.backup_import_title),
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
                    stringResource(R.string.backup_step_select_source),
                    stringResource(R.string.backup_step_enter_password),
                    stringResource(R.string.backup_step_preview),
                    stringResource(R.string.backup_step_result)
                ),
                currentStep = uiState.wizardStep.ordinal,
                onStepClick = if (uiState.wizardStep == ImportWizardStep.RESULT) null
                    else { stepIndex -> viewModel.goToStep(ImportWizardStep.entries[stepIndex]) }
            )

            when (uiState.wizardStep) {
                ImportWizardStep.SELECT_SOURCE -> SelectSourceStep(
                    onChooseFile = { filePickerLauncher.launch(arrayOf("application/json", "*/*")) },
                    onScanQr = { showQrScanner = true }
                )
                ImportWizardStep.ENTER_PASSWORD -> EnterPasswordStep(uiState, viewModel)
                ImportWizardStep.PREVIEW -> PreviewStep(uiState, viewModel)
                ImportWizardStep.RESULT -> ImportResultStep()
            }

            // Error display
            uiState.error?.let { error ->
                val message = when (error) {
                    "Wrong password or seed" -> stringResource(R.string.backup_wrong_password)
                    "Password required" -> stringResource(R.string.backup_step_enter_password)
                    "Invalid backup file" -> stringResource(R.string.backup_invalid_file)
                    "Invalid backup format" -> stringResource(R.string.backup_invalid_file)
                    "backup_seed_incomplete" -> stringResource(R.string.backup_seed_incomplete)
                    "backup_invalid_seed" -> stringResource(R.string.backup_invalid_seed)
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
private fun SelectSourceStep(
    onChooseFile: () -> Unit,
    onScanQr: () -> Unit
) {
    Text(
        text = stringResource(R.string.backup_step_select_source),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Card(
        onClick = onChooseFile,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FileOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.backup_choose_file),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.backup_choose_file_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Card(
        onClick = onScanQr,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.backup_scan_qr),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.backup_scan_qr_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterPasswordStep(
    uiState: BackupImportUiState,
    viewModel: BackupImportViewModel
) {
    Text(
        text = stringResource(R.string.backup_step_enter_password),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    // Mode selection: Password / Recovery Seed
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = uiState.inputMode == EncryptionMode.Password,
            onClick = { viewModel.setInputMode(EncryptionMode.Password) },
            label = { Text(stringResource(R.string.backup_encryption_password)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = uiState.inputMode == EncryptionMode.Seed,
            onClick = { viewModel.setInputMode(EncryptionMode.Seed) },
            label = { Text(stringResource(R.string.backup_encryption_seed)) },
            modifier = Modifier.weight(1f)
        )
    }

    if (uiState.inputMode == EncryptionMode.Password) {
        OutlinedTextField(
            value = uiState.passphrase,
            onValueChange = { viewModel.setPassphrase(it) },
            label = { Text(stringResource(R.string.backup_enter_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Text(
            text = stringResource(R.string.backup_enter_seed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SeedPhraseGrid(
            seedWords = uiState.seedWords,
            onWordChange = { index, word -> viewModel.setSeedWord(index, word) },
            onAllWordsChange = { words -> viewModel.setAllSeedWords(words) },
            getSuggestions = { prefix -> viewModel.getSuggestions(prefix) },
            isValidWord = { word -> viewModel.isValidWord(word) }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.goToStep(ImportWizardStep.SELECT_SOURCE) },
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.common_previous))
        }
        Button(
            onClick = { viewModel.submitPassphrase() },
            modifier = Modifier.weight(1f),
            enabled = !uiState.isParsing && if (uiState.inputMode == EncryptionMode.Password) {
                uiState.passphrase.isNotBlank()
            } else {
                uiState.seedWords.all { it.isNotBlank() }
            }
        ) {
            if (uiState.isParsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_decrypting))
            } else {
                Text(stringResource(R.string.common_next))
            }
        }
    }
}

@Composable
private fun PreviewStep(
    uiState: BackupImportUiState,
    viewModel: BackupImportViewModel
) {
    val preview = uiState.preview ?: return

    Text(
        text = stringResource(R.string.backup_step_preview),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PreviewRow(stringResource(R.string.backup_preview_created), DateFormatters.transactionDateTime.format(Instant.ofEpochMilli(preview.createdAt)))
            PreviewRow(stringResource(R.string.backup_preview_version), preview.appVersion)
            HorizontalDivider()
            PreviewRow(stringResource(R.string.backup_preview_plans, preview.planCount), "")
            if (preview.hasSettings) {
                PreviewRow(stringResource(R.string.backup_preview_settings), "")
            }
            if (preview.thresholdCount > 0) {
                PreviewRow(stringResource(R.string.backup_preview_thresholds, preview.thresholdCount), "")
            }
            if (preview.credentialCount > 0) {
                PreviewRow(stringResource(R.string.backup_preview_credentials, preview.credentialCount), "")
            }
            if (preview.transactionCount > 0) {
                PreviewRow(stringResource(R.string.backup_preview_transactions, preview.transactionCount), "")
            }
            if (preview.notificationCount > 0) {
                PreviewRow(stringResource(R.string.backup_preview_notifications, preview.notificationCount), "")
            }
            if (preview.withdrawalCount > 0) {
                PreviewRow(stringResource(R.string.backup_preview_withdrawals, preview.withdrawalCount), "")
            }
        }
    }

    // Info card
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.backup_restore_warning_replace),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    // Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.goToStep(ImportWizardStep.SELECT_SOURCE) },
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.common_previous))
        }
        Button(
            onClick = { viewModel.confirmRestore() },
            modifier = Modifier.weight(1f),
            enabled = !uiState.isRestoring
        ) {
            if (uiState.isRestoring) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_restoring))
            } else {
                Text(stringResource(R.string.backup_restore_button))
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    if (value.isEmpty()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ImportResultStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = successColor(),
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = stringResource(R.string.backup_restore_success),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (_: Exception) {
        null
    }
}
