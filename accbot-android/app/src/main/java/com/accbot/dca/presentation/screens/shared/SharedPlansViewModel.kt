package com.accbot.dca.presentation.screens.shared

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.R
import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanError
import com.accbot.dca.domain.share.SharedPlanCodec
import com.accbot.dca.domain.share.SharedPlanException
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.domain.usecase.DownloadSharedPlanHistoryUseCase
import com.accbot.dca.domain.usecase.ImportRange
import com.accbot.dca.domain.usecase.ImportSharedPlanUseCase
import com.accbot.dca.domain.usecase.RefreshSharedPlanUseCase
import com.accbot.dca.domain.usecase.ShareKeyException
import com.accbot.dca.domain.usecase.ShareKeyProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject

/** List of read-only plans other people shared with this user, plus import. */
@HiltViewModel
class SharedPlansViewModel @Inject constructor(
    private val store: SharedPlanStore,
    private val importSharedPlan: ImportSharedPlanUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val importing: Boolean = false,
        /** A live-update share was picked and needs its password. */
        val passwordRequired: Boolean = false,
        val wrongPassword: Boolean = false
    )

    val plans: StateFlow<List<SharedPlan>?> = store.plans

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Emits the share ID of a newly imported plan so the screen can open it. */
    private val _opened = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val opened: SharedFlow<String> = _opened.asSharedFlow()

    /** Content of an encrypted share waiting for its password. Kept in memory only. */
    private var pendingJson: String? = null

    init {
        viewModelScope.launch { store.refresh() }
    }

    fun importFrom(uri: Uri) {
        if (_uiState.value.importing) return
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch {
            val json = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { importSharedPlan.readBounded(it) }
                } ?: throw SharedPlanException(SharedPlanError.NOT_A_PLAN_SHARE)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: SharedPlanException) {
                fail(context.getString(errorMessage(e.reason)))
                return@launch
            } catch (_: Exception) {
                fail(context.getString(R.string.shared_plans_import_failed))
                return@launch
            }
            importJson(json, password = null)
        }
    }

    /** Content of a scanned QR code: same format as a share file, so it takes the same path. */
    fun importScanned(content: String) {
        if (_uiState.value.importing) return
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch {
            if (content.toByteArray(Charsets.UTF_8).size > SharedPlanCodec.MAX_FILE_BYTES) {
                fail(context.getString(errorMessage(SharedPlanError.TOO_LARGE)))
            } else {
                importJson(content, password = null)
            }
        }
    }

    fun submitPassword(password: String) {
        val json = pendingJson ?: return
        if (_uiState.value.importing && !_uiState.value.passwordRequired) return
        _uiState.update { it.copy(importing = true, wrongPassword = false) }
        viewModelScope.launch { importJson(json, password) }
    }

    fun cancelPassword() {
        pendingJson = null
        _uiState.value = UiState()
    }

    private suspend fun importJson(json: String, password: String?) {
        try {
            val result = importSharedPlan(json, password)
            pendingJson = null
            _uiState.value = UiState()
            when (result) {
                is SharedPlanStore.SaveResult.Saved -> {
                    _messages.emit(
                        context.getString(
                            if (result.replaced) R.string.shared_plans_import_updated
                            else R.string.shared_plans_import_added,
                            result.plan.displayName
                        )
                    )
                    _opened.emit(result.plan.shareId)
                }
                is SharedPlanStore.SaveResult.Outdated ->
                    _messages.emit(context.getString(R.string.shared_plans_import_outdated, result.stored.displayName))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SharedPlanException) {
            when (e.reason) {
                SharedPlanError.PASSWORD_REQUIRED -> {
                    pendingJson = json
                    _uiState.value = UiState(importing = true, passwordRequired = true)
                }
                SharedPlanError.WRONG_PASSWORD -> {
                    pendingJson = json
                    _uiState.value = UiState(importing = true, passwordRequired = true, wrongPassword = true)
                }
                else -> fail(context.getString(errorMessage(e.reason)))
            }
        } catch (e: ShareKeyException) {
            fail(context.getString(keyProblemMessage(e.problem)))
        } catch (_: Exception) {
            fail(context.getString(R.string.shared_plans_import_failed))
        }
    }

    private suspend fun fail(message: String) {
        pendingJson = null
        _uiState.value = UiState()
        _messages.emit(message)
    }

    companion object {
        fun errorMessage(reason: SharedPlanError): Int = when (reason) {
            SharedPlanError.NOT_A_PLAN_SHARE -> R.string.shared_plans_error_not_share
            SharedPlanError.UNSUPPORTED_VERSION -> R.string.shared_plans_error_version
            SharedPlanError.TOO_LARGE -> R.string.shared_plans_error_too_large
            SharedPlanError.INVALID -> R.string.shared_plans_error_invalid
            SharedPlanError.PASSWORD_REQUIRED, SharedPlanError.WRONG_PASSWORD -> R.string.shared_plans_wrong_password
        }

        fun keyProblemMessage(problem: ShareKeyProblem): Int = when (problem) {
            ShareKeyProblem.NOT_READ_ONLY -> R.string.shared_plans_key_not_read_only
            ShareKeyProblem.LIVE_UPDATES_UNSUPPORTED -> R.string.share_plan_key_unsupported
            else -> R.string.shared_plans_import_failed
        }
    }
}

/** Read-only detail of one shared plan, with refresh for live-update shares. */
@HiltViewModel
class SharedPlanDetailViewModel @Inject constructor(
    private val store: SharedPlanStore,
    private val refreshSharedPlan: RefreshSharedPlanUseCase,
    private val downloadSharedPlanHistory: DownloadSharedPlanHistoryUseCase,
    private val marketDataService: MarketDataService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val plan: SharedPlan? = null,
        val currentPrice: BigDecimal? = null,
        val refreshing: Boolean = false,
        val deleted: Boolean = false,
        val historyDialog: Boolean = false,
        val historyRange: ImportRange = ImportRange.SinceLastPurchase,
        val downloading: Boolean = false,
        val downloadResult: ApiImportResultState? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadedShareId: String? = null

    fun load(shareId: String) {
        if (loadedShareId == shareId) return
        loadedShareId = shareId
        viewModelScope.launch {
            val plan = store.get(shareId)
            _uiState.value = UiState(loading = false, plan = plan)
            if (plan != null) {
                loadPrice(plan)
                when {
                    // Typically right after a QR import: the whole history is what the user wants next.
                    plan.liveUpdates && plan.trades.isEmpty() -> openHistoryDialog()
                    plan.liveUpdates -> refresh()
                }
            }
        }
    }

    fun refresh() {
        val plan = _uiState.value.plan ?: return
        if (!plan.liveUpdates || _uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val result = refreshSharedPlan(plan.shareId)) {
                is RefreshSharedPlanUseCase.Result.Updated -> {
                    _uiState.update { it.copy(plan = result.plan, refreshing = false) }
                    if (result.newTrades > 0) {
                        _messages.emit(context.getString(R.string.shared_plan_refresh_new, result.newTrades))
                    }
                }
                is RefreshSharedPlanUseCase.Result.Failed ->
                    _uiState.update { it.copy(plan = result.plan, refreshing = false) }
                RefreshSharedPlanUseCase.Result.KeyNotReadOnly -> {
                    _uiState.update { it.copy(plan = store.get(plan.shareId), refreshing = false) }
                    _messages.emit(context.getString(R.string.shared_plan_key_revoked_not_read_only))
                }
                RefreshSharedPlanUseCase.Result.NotLive ->
                    _uiState.update { it.copy(plan = store.get(plan.shareId), refreshing = false) }
            }
        }
    }

    fun openHistoryDialog() {
        val plan = _uiState.value.plan ?: return
        if (!plan.liveUpdates) return
        _uiState.update {
            it.copy(
                historyDialog = true,
                historyRange = if (plan.trades.isEmpty()) ImportRange.AllHistory else ImportRange.SinceLastPurchase
            )
        }
    }

    fun setHistoryRange(range: ImportRange) {
        _uiState.update { it.copy(historyRange = range) }
    }

    fun dismissHistoryDialog() {
        _uiState.update { it.copy(historyDialog = false) }
    }

    fun consumeDownloadResult() {
        _uiState.update { it.copy(downloadResult = null) }
    }

    /** Fetches the history from the exchange with the share's read-only key; nothing is ever traded. */
    fun downloadHistory() {
        val state = _uiState.value
        val plan = state.plan ?: return
        if (!plan.liveUpdates || state.downloading) return
        val range = state.historyRange
        _uiState.update { it.copy(historyDialog = false, downloading = true) }
        viewModelScope.launch {
            try {
                when (val result = downloadSharedPlanHistory(plan.shareId, range)) {
                    is RefreshSharedPlanUseCase.Result.Updated -> _uiState.update {
                        it.copy(
                            plan = result.plan,
                            downloading = false,
                            // For a replacing download newTrades is the size of the range, not an increase.
                            downloadResult = ApiImportResultState.Success(imported = result.newTrades, skipped = 0)
                        )
                    }
                    is RefreshSharedPlanUseCase.Result.Failed -> _uiState.update {
                        it.copy(
                            plan = result.plan,
                            downloading = false,
                            downloadResult = ApiImportResultState.Error(
                                context.getString(
                                    if (result.historyTooLong) R.string.shared_plan_history_too_long
                                    else R.string.shared_plan_refresh_failed
                                )
                            )
                        )
                    }
                    RefreshSharedPlanUseCase.Result.KeyNotReadOnly -> {
                        _uiState.update { it.copy(plan = store.get(plan.shareId), downloading = false) }
                        _messages.emit(context.getString(R.string.shared_plan_key_revoked_not_read_only))
                    }
                    RefreshSharedPlanUseCase.Result.NotLive ->
                        _uiState.update { it.copy(plan = store.get(plan.shareId), downloading = false) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        downloading = false,
                        downloadResult = ApiImportResultState.Error(context.getString(R.string.shared_plan_refresh_failed))
                    )
                }
            }
        }
    }

    private suspend fun loadPrice(plan: SharedPlan) {
        val price = try {
            marketDataService.getCachedPrice(plan.crypto, plan.fiat)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        _uiState.update { it.copy(currentPrice = price) }
    }

    fun delete() {
        val shareId = _uiState.value.plan?.shareId ?: return
        viewModelScope.launch {
            store.delete(shareId)
            _uiState.update { it.copy(deleted = true) }
        }
    }
}
