package com.accbot.dca.presentation.screens

import android.content.Context
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.LoadingState
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCsvScreen(
    planId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: ImportCsvViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(planId) {
        viewModel.loadPlan(planId)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = readTextFromUri(context, uri)
            if (content != null) {
                viewModel.loadCsv(content)
            }
        }
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.import_csv_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingState(message = stringResource(R.string.common_loading))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Plan info card
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
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = accentColor(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(
                                R.string.import_csv_plan_info,
                                uiState.planCrypto,
                                uiState.planFiat,
                                uiState.planExchange
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                when {
                    // Success state
                    uiState.importSuccess -> {
                        SuccessContent(
                            importedCount = uiState.importedCount,
                            onViewHistory = onNavigateToHistory
                        )
                    }
                    // Preview state (CSV loaded)
                    uiState.csvLoaded -> {
                        PreviewContent(
                            newCount = uiState.newCount,
                            skippedCount = uiState.skippedCount,
                            isImporting = uiState.isImporting,
                            onImport = { viewModel.importTransactions() },
                            onSelectAnother = {
                                filePickerLauncher.launch(arrayOf("text/*"))
                            }
                        )
                    }
                    // Initial state
                    else -> {
                        InitialContent(
                            error = uiState.error,
                            onSelectFile = {
                                filePickerLauncher.launch(arrayOf("text/*"))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitialContent(
    error: String?,
    onSelectFile: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = null,
                tint = accentColor(),
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = stringResource(R.string.import_csv_instructions),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (error != null) {
                val errorMessage = when (error) {
                    "no_buy_transactions" -> stringResource(R.string.import_csv_no_buy_transactions)
                    "parse_error" -> stringResource(R.string.import_csv_parse_error)
                    else -> error
                }
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onSelectFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.import_csv_select_file))
            }

            Text(
                text = stringResource(R.string.import_csv_select_file_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PreviewContent(
    newCount: Int,
    skippedCount: Int,
    isImporting: Boolean,
    onImport: () -> Unit,
    onSelectAnother: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = successColor(),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.import_csv_file_loaded),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.import_csv_new_transactions, newCount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (newCount > 0) successColor() else MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(R.string.import_csv_skipped, skippedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (newCount > 0) {
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isImporting
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_csv_importing))
                    } else {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_csv_import_button))
                    }
                }
            }

            OutlinedButton(
                onClick = onSelectAnother,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting
            ) {
                Text(stringResource(R.string.import_csv_select_file))
            }
        }
    }
}

@Composable
private fun SuccessContent(
    importedCount: Int,
    onViewHistory: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = successColor(),
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = stringResource(R.string.import_csv_success, importedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.import_csv_view_history))
            }
        }
    }
}

private fun readTextFromUri(context: Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
        null
    }
}
