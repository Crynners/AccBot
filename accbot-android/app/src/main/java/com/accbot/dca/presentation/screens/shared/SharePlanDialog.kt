package com.accbot.dca.presentation.screens.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import com.accbot.dca.domain.usecase.ShareKeyException
import com.accbot.dca.domain.usecase.ShareKeyProblem
import com.accbot.dca.domain.usecase.VerifyShareKeyUseCase
import com.accbot.dca.exchange.ReadOnlyExchangeClient.KeyCheck
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
            get() = keyCheck == KeyCheck.READ_ONLY || (keyCheck == KeyCheck.UNVERIFIABLE && unverifiableConfirmed)
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
        val keyCheck = state.keyCheck
        val live = if (state.liveUpdates && keyCheck != null) {
            LiveShareAccess(credentials(exchange, state), state.password, keyCheck)
        } else null
        _uiState.update { it.copy(creating = true, failed = false) }
        viewModelScope.launch {
            val uri = try {
                val file = createPlanShare(planId, state.includeAmounts, live)
                withContext(Dispatchers.IO) { writeSharedExport(context, file.fileName, file.content) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _uiState.update { it.copy(creating = false, readyUri = uri, failed = uri == null) }
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
 * Lets the user choose what a read-only plan share contains, optionally adding a separate
 * read-only exchange key for live updates, then opens the system share sheet.
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
        onDismissRequest = { if (!state.creating) close() },
        title = { Text(stringResource(R.string.share_plan_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
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
                        if (state.liveUpdates) R.string.share_plan_never_included_live else R.string.share_plan_never_included
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
    HorizontalDivider()
    Text(
        stringResource(R.string.share_plan_live_key_instructions, exchange.displayName),
        style = MaterialTheme.typography.bodySmall,
        color = Warning
    )
    SecretField(state.apiKey, viewModel::setApiKey, stringResource(R.string.share_plan_live_api_key), secret = false)
    SecretField(state.apiSecret, viewModel::setApiSecret, stringResource(R.string.share_plan_live_api_secret))
    if (exchange == Exchange.COINMATE) {
        SecretField(state.clientId, viewModel::setClientId, stringResource(R.string.share_plan_live_client_id), secret = false)
    }
    if (exchange == Exchange.KUCOIN || exchange == Exchange.COINBASE) {
        SecretField(state.passphrase, viewModel::setPassphrase, stringResource(R.string.share_plan_live_passphrase))
    }
    val keyComplete = state.apiKey.isNotBlank() && state.apiSecret.isNotBlank() &&
        (exchange != Exchange.COINMATE || state.clientId.isNotBlank()) &&
        ((exchange != Exchange.KUCOIN && exchange != Exchange.COINBASE) || state.passphrase.isNotBlank())
    OutlinedButton(
        onClick = onVerify,
        enabled = keyComplete && !state.verifying && !state.creating,
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
    state.keyProblem?.let { problem ->
        Text(
            stringResource(
                when (problem) {
                    ShareKeyProblem.LIVE_UPDATES_UNSUPPORTED -> R.string.share_plan_key_unsupported
                    ShareKeyProblem.WRONG_EXCHANGE -> R.string.share_plan_key_wrong_exchange
                    ShareKeyProblem.SAME_AS_PLAN_KEY -> R.string.share_plan_key_same_as_plan
                    ShareKeyProblem.NOT_READ_ONLY -> R.string.share_plan_key_not_read_only
                    ShareKeyProblem.CHECK_FAILED -> R.string.share_plan_key_check_failed
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

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii,
            autoCorrectEnabled = false
        ),
        modifier = Modifier.fillMaxWidth()
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
