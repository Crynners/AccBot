package com.accbot.dca.presentation.screens.shared

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accbot.dca.R
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.PasswordTextField
import com.accbot.dca.presentation.components.CryptoIcon
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.components.LoadingState
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.utils.DateFormatters

@Composable
fun SharedPlansScreen(
    onNavigateBack: () -> Unit,
    onOpenPlan: (shareId: String) -> Unit,
    viewModel: SharedPlansViewModel = hiltViewModel()
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }
    val openPicker = { filePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.opened.collect { onOpenPlan(it) }
    }

    if (uiState.passwordRequired) {
        SharePasswordDialog(
            wrongPassword = uiState.wrongPassword,
            onSubmit = viewModel::submitPassword,
            onDismiss = viewModel::cancelPassword
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.shared_plans_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    if (uiState.importing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = openPicker) {
                            Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.shared_plans_import))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val list = plans
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Visibility,
                    title = stringResource(R.string.shared_plans_empty_title),
                    description = stringResource(R.string.shared_plans_empty_description),
                    actionLabel = stringResource(R.string.shared_plans_import),
                    onAction = openPicker
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.shared_plans_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(list, key = { it.shareId }) { plan ->
                    SharedPlanRow(plan = plan, onClick = { onOpenPlan(plan.shareId) })
                }
            }
        }
    }
}

@Composable
private fun SharePasswordDialog(wrongPassword: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shared_plans_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.shared_plans_password_text))
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.share_plan_live_password),
                    isError = wrongPassword,
                    modifier = Modifier.fillMaxWidth()
                )
                if (wrongPassword) {
                    Text(
                        stringResource(R.string.shared_plans_wrong_password),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.shared_plans_password_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun SharedPlanRow(plan: SharedPlan, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CryptoIcon(crypto = plan.crypto, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = plan.displayName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${plan.crypto}/${plan.fiat} · ${plan.exchange?.displayName ?: plan.exchangeName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.shared_plans_snapshot_from, DateFormatters.shortDateTime.format(plan.sharedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plan.liveUpdates) {
                    Text(
                        text = stringResource(R.string.shared_plans_live_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (plan.isSandbox) {
                    Text(
                        text = stringResource(R.string.shared_plans_sandbox_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = Warning
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
