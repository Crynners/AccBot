package com.accbot.dca.presentation.screens.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.supportsApiImport
import com.accbot.dca.domain.usecase.CreatePlanShareUseCase
import com.accbot.dca.domain.usecase.LiveShareAccess
import com.accbot.dca.domain.usecase.LiveShareKey
import com.accbot.dca.domain.usecase.ShareKeyException
import com.accbot.dca.domain.usecase.ShareKeyProblem
import com.accbot.dca.domain.usecase.VerifyShareKeyUseCase
import com.accbot.dca.exchange.ReadOnlyExchangeClient.KeyCheck
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.components.PasswordTextField
import com.accbot.dca.presentation.components.areCredentialsComplete
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.writeSharedExport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Which key a live-update share carries; see [LiveShareKey]. */
enum class ShareKeySource {
    /** A new key the user creates on the exchange with read-only access (the default). */
    NEW_READ_ONLY,
    /** The key this plan trades with, after an explicit warning. */
    PLAN_KEY
}

@HiltViewModel
class SharePlanViewModel @Inject constructor(
    private val createPlanShare: CreatePlanShareUseCase,
    private val verifyShareKey: VerifyShareKeyUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Secrets live only in this in-memory state, never in saved instance state. */
    data class UiState(
        val includeAmounts: Boolean = false,
        val liveUpdates: Boolean = false,
        val keySource: ShareKeySource = ShareKeySource.NEW_READ_ONLY,
        /** The user accepted that the recipient gets the plan's key with all its permissions. */
        val planKeyAcknowledged: Boolean = false,
        val apiKey: String = "",
        val apiSecret: String = "",
        val passphrase: String = "",
        val clientId: String = "",
        val password: String = "",
        val passwordConfirm: String = "",
        val verifying: Boolean = false,
        val keyCheck: KeyCheck? = null,
        val keyProblem: ShareKeyProblem? = null,
        val unverifiableConfirmed: Boolean = false,
        val creating: Boolean = false,
        val readyUri: Uri? = null,
        val failed: Boolean = false
    ) {
        val passwordValid: Boolean get() = password.length >= MIN_PASSWORD_LENGTH && password == passwordConfirm
        val keyAccepted: Boolean
            get() = when (keySource) {
                ShareKeySource.NEW_READ_ONLY ->
                    keyCheck == KeyCheck.READ_ONLY || (keyCheck == KeyCheck.UNVERIFIABLE && unverifiableConfirmed)
                ShareKeySource.PLAN_KEY -> planKeyAcknowledged
            }
        val canShare: Boolean
            get() = !creating && !verifying && (!liveUpdates || (keyAccepted && passwordValid))
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun setIncludeAmounts(value: Boolean) = _uiState.update { it.copy(includeAmounts = value) }
    fun setLiveUpdates(value: Boolean) = _uiState.update { it.copy(liveUpdates = value) }
    fun setApiKey(value: String) = updateKey { it.copy(apiKey = value) }
    fun setApiSecret(value: String) = updateKey { it.copy(apiSecret = value) }
    fun setPassphrase(value: String) = updateKey { it.copy(passphrase = value) }
    fun setClientId(value: String) = updateKey { it.copy(clientId = value) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value) }
    fun setPasswordConfirm(value: String) = _uiState.update { it.copy(passwordConfirm = value) }
    fun setUnverifiableConfirmed(value: Boolean) = _uiState.update { it.copy(unverifiableConfirmed = value) }
    fun setPlanKeyAcknowledged(value: Boolean) = _uiState.update { it.copy(planKeyAcknowledged = value) }

    /** Switching the source always asks for the warning to be accepted again. */
    fun setKeySource(value: ShareKeySource) = _uiState.update {
        if (it.keySource == value) it
        else it.copy(keySource = value, planKeyAcknowledged = false, keyProblem = null)
    }

    /** Any change to the key invalidates an earlier verification. */
    private fun updateKey(change: (UiState) -> UiState) = _uiState.update {
        change(it).copy(keyCheck = null, keyProblem = null, unverifiableConfirmed = false)
    }

    private fun credentials(exchange: Exchange, state: UiState) = ExchangeCredentials(
        exchange = exchange,
        apiKey = state.apiKey.trim(),
        apiSecret = state.apiSecret.trim(),
        passphrase = state.passphrase.trim().ifEmpty { null },
        clientId = state.clientId.trim().ifEmpty { null }
    )

    fun verifyKey(planId: Long, exchange: Exchange) {
        val state = _uiState.value
        if (state.verifying || state.apiKey.isBlank() || state.apiSecret.isBlank()) return
        _uiState.update { it.copy(verifying = true, keyCheck = null, keyProblem = null) }
        viewModelScope.launch {
            try {
                val check = verifyShareKey(planId, credentials(exchange, state))
                _uiState.update { it.copy(verifying = false, keyCheck = check) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ShareKeyException) {
                _uiState.update { it.copy(verifying = false, keyProblem = e.problem) }
            } catch (_: Exception) {
                _uiState.update { it.copy(verifying = false, keyProblem = ShareKeyProblem.CHECK_FAILED) }
            }
        }
    }

    fun createShare(planId: Long, exchange: Exchange) {
        val state = _uiState.value
        if (!state.canShare) return
        val live = if (state.liveUpdates) {
            val key = when (state.keySource) {
                ShareKeySource.PLAN_KEY -> LiveShareKey.PlanKey
                ShareKeySource.NEW_READ_ONLY -> LiveShareKey.Separate(
                    credentials(exchange, state),
                    state.keyCheck ?: return
                )
            }
            LiveShareAccess(key, state.password)
        } else null
        _uiState.update { it.copy(creating = true, failed = false, keyProblem = null) }
        viewModelScope.launch {
            var problem: ShareKeyProblem? = null
            val uri = try {
                val file = createPlanShare(planId, state.includeAmounts, live)
                withContext(Dispatchers.IO) { writeSharedExport(context, file.fileName, file.content) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ShareKeyException) {
                problem = e.problem
                null
            } catch (_: Exception) {
                null
            }
            _uiState.update {
                it.copy(
                    creating = false,
                    readyUri = uri,
                    // A key problem is shown inline in the dialog instead of a generic error.
                    failed = uri == null && problem == null,
                    keyProblem = problem ?: it.keyProblem
                )
            }
        }
    }

    fun consumeResult() = _uiState.update { it.copy(readyUri = null, failed = false) }

    /** Drop secrets once the dialog is closed. */
    fun reset() {
        _uiState.value = UiState()
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

/**
 * Lets the user choose what a read-only plan share contains, optionally adding an exchange
 * key for live updates (by default a new read-only key, or the plan's own key after a
 * warning), then opens the system share sheet.
 */
@Composable
fun SharePlanDialog(
    planId: Long,
    exchange: Exchange,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
    viewModel: SharePlanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subject = stringResource(R.string.share_plan_subject)
    val chooserTitle = stringResource(R.string.share_plan_title)
    val failedMessage = stringResource(R.string.share_plan_failed)
    val close = {
        viewModel.reset()
        onDismiss()
    }

    LaunchedEffect(state.readyUri, state.failed) {
        val uri = state.readyUri
        when {
            uri != null -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                viewModel.consumeResult()
                close()
                context.startActivity(Intent.createChooser(intent, chooserTitle))
            }
            state.failed -> {
                viewModel.consumeResult()
                onError(failedMessage)
            }
        }
    }

    AlertDialog(
        // Closes only via the Cancel / Share / Done buttons, never by outside tap or Back.
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = { Text(stringResource(R.string.share_plan_title)) },
        text = {
            Column(
                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.share_plan_description))
                SwitchRow(
                    title = stringResource(R.string.share_plan_include_amounts),
                    subtitle = stringResource(
                        if (state.includeAmounts || state.liveUpdates) R.string.share_plan_include_amounts_on
                        else R.string.share_plan_include_amounts_off
                    ),
                    checked = state.includeAmounts || state.liveUpdates,
                    enabled = !state.liveUpdates && !state.creating,
                    onCheckedChange = viewModel::setIncludeAmounts
                )
                if (exchange.supportsApiImport) {
                    SwitchRow(
                        title = stringResource(R.string.share_plan_live_updates),
                        subtitle = stringResource(R.string.share_plan_live_updates_subtitle),
                        checked = state.liveUpdates,
                        enabled = !state.creating,
                        onCheckedChange = viewModel::setLiveUpdates
                    )
                    if (state.liveUpdates) {
                        LiveKeySection(state, exchange, viewModel, onVerify = { viewModel.verifyKey(planId, exchange) })
                    }
                }
                Text(
                    text = stringResource(
                        when {
                            !state.liveUpdates -> R.string.share_plan_never_included
                            state.keySource == ShareKeySource.PLAN_KEY -> R.string.share_plan_never_included_plan_key
                            else -> R.string.share_plan_never_included_live
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.createShare(planId, exchange) }, enabled = state.canShare) {
                if (state.creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.share_plan_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = close, enabled = !state.creating) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun LiveKeySection(
    state: SharePlanViewModel.UiState,
    exchange: Exchange,
    viewModel: SharePlanViewModel,
    onVerify: () -> Unit
) {
    val busy = state.verifying || state.creating
    HorizontalDivider()
    Text(stringResource(R.string.share_plan_key_source_title), fontWeight = FontWeight.Medium)
    KeySourceOption(
        selected = state.keySource == ShareKeySource.NEW_READ_ONLY,
        title = stringResource(R.string.share_plan_key_source_new),
        subtitle = stringResource(R.string.share_plan_key_source_new_subtitle),
        enabled = !busy,
        onClick = { viewModel.setKeySource(ShareKeySource.NEW_READ_ONLY) }
    )
    KeySourceOption(
        selected = state.keySource == ShareKeySource.PLAN_KEY,
        title = stringResource(R.string.share_plan_key_source_plan),
        subtitle = stringResource(R.string.share_plan_key_source_plan_subtitle),
        enabled = !busy,
        onClick = { viewModel.setKeySource(ShareKeySource.PLAN_KEY) }
    )

    when (state.keySource) {
        ShareKeySource.NEW_READ_ONLY -> NewKeyInput(state, exchange, viewModel, busy, onVerify)
        ShareKeySource.PLAN_KEY -> PlanKeyWarning(state, exchange, viewModel, busy)
    }

    state.keyProblem?.let { problem ->
        Text(
            stringResource(
                when (problem) {
                    ShareKeyProblem.LIVE_UPDATES_UNSUPPORTED -> R.string.share_plan_key_unsupported
                    ShareKeyProblem.WRONG_EXCHANGE -> R.string.share_plan_key_wrong_exchange
                    ShareKeyProblem.SAME_AS_PLAN_KEY -> R.string.share_plan_key_same_as_plan
                    ShareKeyProblem.NOT_READ_ONLY -> R.string.share_plan_key_not_read_only
                    ShareKeyProblem.CHECK_FAILED -> R.string.share_plan_key_check_failed
                    ShareKeyProblem.PLAN_KEY_MISSING -> R.string.share_plan_key_plan_missing
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Error
        )
    }
    HorizontalDivider()
    Text(stringResource(R.string.share_plan_live_password_info), style = MaterialTheme.typography.bodySmall)
    SecretField(state.password, viewModel::setPassword, stringResource(R.string.share_plan_live_password))
    SecretField(state.passwordConfirm, viewModel::setPasswordConfirm, stringResource(R.string.share_plan_live_password_confirm))
    if (state.password.isNotEmpty() && !state.passwordValid) {
        Text(
            stringResource(R.string.share_plan_live_password_invalid, SharePlanViewModel.MIN_PASSWORD_LENGTH),
            style = MaterialTheme.typography.bodySmall,
            color = Error
        )
    }
}

/** The same credentials form (QR scan, paste) as when adding a plan for this exchange. */
@Composable
private fun NewKeyInput(
    state: SharePlanViewModel.UiState,
    exchange: Exchange,
    viewModel: SharePlanViewModel,
    busy: Boolean,
    onVerify: () -> Unit
) {
    Text(
        stringResource(R.string.share_plan_live_key_instructions, exchange.displayName),
        style = MaterialTheme.typography.bodySmall,
        color = Warning
    )
    CredentialsInputCard(
        exchange = exchange,
        clientId = state.clientId,
        apiKey = state.apiKey,
        apiSecret = state.apiSecret,
        passphrase = state.passphrase,
        onClientIdChange = viewModel::setClientId,
        onApiKeyChange = viewModel::setApiKey,
        onApiSecretChange = viewModel::setApiSecret,
        onPassphraseChange = viewModel::setPassphrase,
        isValidating = busy
    )
    val keyComplete = areCredentialsComplete(exchange, state.clientId, state.apiKey, state.apiSecret, state.passphrase)
    OutlinedButton(
        onClick = onVerify,
        enabled = keyComplete && !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.verifying) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.share_plan_live_verify))
        }
    }
    when (state.keyCheck) {
        KeyCheck.READ_ONLY -> Text(
            stringResource(R.string.share_plan_live_verified),
            style = MaterialTheme.typography.bodySmall,
            color = successColor()
        )
        KeyCheck.UNVERIFIABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.unverifiableConfirmed, onCheckedChange = viewModel::setUnverifiableConfirmed)
            Text(
                stringResource(R.string.share_plan_live_unverifiable, exchange.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = Warning
            )
        }
        else -> Unit
    }
}

@Composable
private fun PlanKeyWarning(
    state: SharePlanViewModel.UiState,
    exchange: Exchange,
    viewModel: SharePlanViewModel,
    busy: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.share_plan_plan_key_warning_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                stringResource(R.string.share_plan_plan_key_warning, exchange.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.planKeyAcknowledged,
                        enabled = !busy,
                        role = Role.Checkbox,
                        onValueChange = viewModel::setPlanKeyAcknowledged
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = state.planKeyAcknowledged, onCheckedChange = null, enabled = !busy)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.share_plan_plan_key_confirm),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun KeySourceOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    PasswordTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .onFocusChanged { if (it.isFocused) scope.launch { requester.bringIntoView() } }
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
