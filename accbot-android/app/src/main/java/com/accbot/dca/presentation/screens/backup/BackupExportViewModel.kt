package com.accbot.dca.presentation.screens.backup

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.BackupDataCollector
import com.accbot.dca.domain.model.BackupDataCounts
import com.accbot.dca.domain.model.BackupExportOptions
import com.accbot.dca.domain.model.EncryptionMode
import com.accbot.dca.domain.usecase.BackupCryptoUseCase
import com.accbot.dca.domain.usecase.CreateBackupResult
import com.accbot.dca.domain.usecase.CreateBackupUseCase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExportWizardStep { SELECT_DATA, ENCRYPTION, RESULT }

@Immutable
data class BackupExportUiState(
    val dataCounts: BackupDataCounts = BackupDataCounts(),
    val includeCredentials: Boolean = false,
    val includeTransactions: Boolean = false,
    val includeNotifications: Boolean = false,
    val includeWithdrawals: Boolean = false,
    val encryptionMode: EncryptionMode = EncryptionMode.Password,
    val password: String = "",
    val confirmPassword: String = "",
    val seedWords: List<String> = emptyList(),
    val seedConfirmed: Boolean = false,
    val wizardStep: ExportWizardStep = ExportWizardStep.SELECT_DATA,
    val isCreating: Boolean = false,
    val resultJson: String? = null,
    val resultFileName: String? = null,
    val resultSizeBytes: Int = 0,
    val error: String? = null
)

@HiltViewModel
class BackupExportViewModel @Inject constructor(
    private val collector: BackupDataCollector,
    private val createBackupUseCase: CreateBackupUseCase,
    private val cryptoUseCase: BackupCryptoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupExportUiState())
    val uiState: StateFlow<BackupExportUiState> = _uiState.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    init {
        loadDataCounts()
    }

    private fun loadDataCounts() {
        viewModelScope.launch {
            val counts = collector.getDataCounts()
            _uiState.update { it.copy(dataCounts = counts) }
        }
    }

    fun setIncludeCredentials(include: Boolean) {
        _uiState.update { it.copy(includeCredentials = include) }
    }

    fun setIncludeTransactions(include: Boolean) {
        _uiState.update { it.copy(includeTransactions = include) }
    }

    fun setIncludeNotifications(include: Boolean) {
        _uiState.update { it.copy(includeNotifications = include) }
    }

    fun setIncludeWithdrawals(include: Boolean) {
        _uiState.update { it.copy(includeWithdrawals = include) }
    }

    fun setEncryptionMode(mode: EncryptionMode) {
        _uiState.update { it.copy(encryptionMode = mode, error = null) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun setConfirmPassword(password: String) {
        _uiState.update { it.copy(confirmPassword = password, error = null) }
    }

    fun generateSeed() {
        val words = cryptoUseCase.generateSeed()
        _uiState.update { it.copy(seedWords = words, seedConfirmed = false) }
    }

    fun confirmSeed() {
        _uiState.update { it.copy(seedConfirmed = true) }
    }

    fun goToStep(step: ExportWizardStep) {
        _uiState.update { it.copy(wizardStep = step, error = null) }
    }

    fun proceedToEncryption() {
        _uiState.update { it.copy(wizardStep = ExportWizardStep.ENCRYPTION, error = null) }
    }

    fun createBackup() {
        val state = _uiState.value

        // Validate encryption
        if (state.encryptionMode == EncryptionMode.Password) {
            if (state.password.length < 8) {
                _uiState.update { it.copy(error = "password_too_short") }
                return
            }
            if (state.password != state.confirmPassword) {
                _uiState.update { it.copy(error = "password_mismatch") }
                return
            }
        } else {
            if (state.seedWords.isEmpty() || !state.seedConfirmed) {
                _uiState.update { it.copy(error = "no_passphrase") }
                return
            }
        }

        _uiState.update { it.copy(isCreating = true, error = null) }

        viewModelScope.launch {
            val seed = if (state.encryptionMode == EncryptionMode.Seed) {
                state.seedWords.joinToString(" ")
            } else ""

            val options = BackupExportOptions(
                includeCredentials = state.includeCredentials,
                includeTransactions = state.includeTransactions,
                includeNotifications = state.includeNotifications,
                includeWithdrawals = state.includeWithdrawals,
                encryptionMode = state.encryptionMode,
                password = state.password,
                seed = seed
            )

            when (val result = createBackupUseCase.execute(options)) {
                is CreateBackupResult.Success -> {
                    _qrBitmap.value = if (createBackupUseCase.isQrFeasible(result.payloadSizeBytes)) {
                        generateQrBitmap(result.envelopeJson)
                    } else null

                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            wizardStep = ExportWizardStep.RESULT,
                            resultJson = result.envelopeJson,
                            resultFileName = result.suggestedFileName,
                            resultSizeBytes = result.payloadSizeBytes
                        )
                    }
                }
                is CreateBackupResult.Error -> {
                    _uiState.update { it.copy(isCreating = false, error = result.message) }
                }
            }
        }
    }

    private fun generateQrBitmap(content: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val size = 512
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val pixels = IntArray(size * size) { i ->
                if (bitMatrix[i % size, i / size]) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also {
                it.setPixels(pixels, 0, size, 0, 0, size, size)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
