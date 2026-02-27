package com.accbot.dca.presentation.screens.backup

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.Bip39WordList
import com.accbot.dca.domain.model.BackupPayload
import com.accbot.dca.domain.model.BackupPreview
import com.accbot.dca.domain.model.EncryptionMode
import com.accbot.dca.domain.model.RestoreMode
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
    val inputMode: EncryptionMode = EncryptionMode.Password,
    val passphrase: String = "",
    val seedWords: List<String> = List(12) { "" },
    val preview: BackupPreview? = null,
    val payload: BackupPayload? = null,
    val restoreMode: RestoreMode = RestoreMode.Replace,
    val isParsing: Boolean = false,
    val isRestoring: Boolean = false,
    val restoreSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BackupImportViewModel @Inject constructor(
    private val application: Application,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val cryptoUseCase: BackupCryptoUseCase,
    private val bip39WordList: Bip39WordList
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

    fun setInputMode(mode: EncryptionMode) {
        _uiState.update { it.copy(inputMode = mode, error = null) }
    }

    fun setSeedWord(index: Int, word: String) {
        _uiState.update { state ->
            val updated = state.seedWords.toMutableList().also { it[index] = word }
            state.copy(seedWords = updated, error = null)
        }
    }

    fun setAllSeedWords(words: List<String>) {
        _uiState.update { state ->
            val padded = (words + List(12) { "" }).take(12)
            state.copy(seedWords = padded, error = null)
        }
    }

    fun getSuggestions(prefix: String): List<String> =
        bip39WordList.getSuggestions(prefix)

    fun isValidWord(word: String): Boolean =
        bip39WordList.isValidWord(word)

    fun submitPassphrase() {
        val state = _uiState.value
        val json = state.envelopeJson ?: return

        if (state.inputMode == EncryptionMode.Seed) {
            if (state.seedWords.any { it.isBlank() }) {
                _uiState.update { it.copy(error = "backup_seed_incomplete") }
                return
            }
            if (!bip39WordList.isValidSeed(state.seedWords)) {
                _uiState.update { it.copy(error = "backup_invalid_seed") }
                return
            }
            tryParsePreview(json, state.seedWords.joinToString(" "))
        } else {
            tryParsePreview(json, state.passphrase)
        }
    }

    private fun tryParsePreview(json: String, passphrase: String) {
        _uiState.update { it.copy(isParsing = true, error = null) }
        viewModelScope.launch(Dispatchers.Default) {
            when (val result = restoreBackupUseCase.parseAndPreview(json, passphrase)) {
                is RestoreBackupResult.PreviewReady -> {
                    _uiState.update {
                        it.copy(
                            wizardStep = ImportWizardStep.PREVIEW,
                            preview = result.preview,
                            payload = result.payload,
                            isEncrypted = false,
                            isParsing = false,
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
                                isParsing = false,
                                error = if (result.message == "Wrong password or seed") result.message else null
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isParsing = false, error = result.message) }
                    }
                }
                is RestoreBackupResult.RestoreComplete -> {
                    _uiState.update { it.copy(isParsing = false) }
                }
            }
        }
    }

    fun confirmRestore() {
        val state = _uiState.value
        val payload = state.payload ?: return

        _uiState.update { it.copy(isRestoring = true, error = null) }

        viewModelScope.launch {
            when (val result = restoreBackupUseCase.restore(payload, state.restoreMode)) {
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
