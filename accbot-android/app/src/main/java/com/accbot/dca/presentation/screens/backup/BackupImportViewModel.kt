package com.accbot.dca.presentation.screens.backup

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.domain.model.BackupPayload
import com.accbot.dca.domain.model.BackupPreview
import com.accbot.dca.domain.usecase.BackupCryptoUseCase
import com.accbot.dca.domain.usecase.RestoreBackupResult
import com.accbot.dca.domain.usecase.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ImportWizardStep { SELECT_SOURCE, ENTER_PASSWORD, PREVIEW, RESULT }

@Immutable
data class BackupImportUiState(
    val wizardStep: ImportWizardStep = ImportWizardStep.SELECT_SOURCE,
    val envelopeJson: String? = null,
    val isEncrypted: Boolean = false,
    val passphrase: String = "",
    val preview: BackupPreview? = null,
    val payload: BackupPayload? = null,
    val isRestoring: Boolean = false,
    val restoreSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BackupImportViewModel @Inject constructor(
    private val application: Application,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val cryptoUseCase: BackupCryptoUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BackupImportUiState())
    val uiState: StateFlow<BackupImportUiState> = _uiState.asStateFlow()

    fun onFileSelected(content: String) {
        _uiState.update { it.copy(envelopeJson = content, error = null) }
        tryParsePreview(content, "")
    }

    fun onQrScanned(content: String) {
        _uiState.update { it.copy(envelopeJson = content, error = null) }
        tryParsePreview(content, "")
    }

    fun setPassphrase(passphrase: String) {
        _uiState.update { it.copy(passphrase = passphrase, error = null) }
    }

    fun submitPassphrase() {
        val state = _uiState.value
        val json = state.envelopeJson ?: return
        tryParsePreview(json, state.passphrase)
    }

    private fun tryParsePreview(json: String, passphrase: String) {
        viewModelScope.launch(Dispatchers.Default) {
            when (val result = restoreBackupUseCase.parseAndPreview(json, passphrase)) {
                is RestoreBackupResult.PreviewReady -> {
                    _uiState.update {
                        it.copy(
                            wizardStep = ImportWizardStep.PREVIEW,
                            preview = result.preview,
                            payload = result.payload,
                            isEncrypted = false,
                            error = null
                        )
                    }
                }
                is RestoreBackupResult.Error -> {
                    if (result.message == "Password required" || result.message == "Wrong password or seed") {
                        _uiState.update {
                            it.copy(
                                wizardStep = ImportWizardStep.ENTER_PASSWORD,
                                isEncrypted = true,
                                error = if (result.message == "Wrong password or seed") result.message else null
                            )
                        }
                    } else {
                        _uiState.update { it.copy(error = result.message) }
                    }
                }
                is RestoreBackupResult.RestoreComplete -> {
                    // Not expected here
                }
            }
        }
    }

    fun confirmRestore() {
        val payload = _uiState.value.payload ?: return

        _uiState.update { it.copy(isRestoring = true, error = null) }

        viewModelScope.launch {
            when (val result = restoreBackupUseCase.restore(payload)) {
                is RestoreBackupResult.RestoreComplete -> {
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            restoreSuccess = true,
                            wizardStep = ImportWizardStep.RESULT
                        )
                    }
                    // Delay briefly then restart
                    kotlinx.coroutines.delay(1500)
                    restartApp()
                }
                is RestoreBackupResult.Error -> {
                    _uiState.update { it.copy(isRestoring = false, error = result.message) }
                }
                is RestoreBackupResult.PreviewReady -> {
                    // Not expected here
                }
            }
        }
    }

    fun goToStep(step: ImportWizardStep) {
        _uiState.update { it.copy(wizardStep = step, error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun restartApp() {
        val packageManager = application.packageManager
        val intent = packageManager.getLaunchIntentForPackage(application.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
