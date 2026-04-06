package com.accbot.dca.presentation.screens.plans

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.usecase.CalculateMonthlyCostUseCase
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.exchange.MinOrderSizeRepository
import com.accbot.dca.presentation.plan.PlanFormDelegate
import com.accbot.dca.scheduler.DcaAlarmScheduler
import com.accbot.dca.presentation.plan.PlanFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class EditPlanUiState(
    val planId: Long = 0,
    val crypto: String = "",
    val fiat: String = "",
    val exchange: Exchange? = null,
    val exchangeName: String = "",

    // Plan form (from delegate)
    val planForm: PlanFormState = PlanFormState(),

    // Action state
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = planForm.isFormValid
}

@HiltViewModel
class EditPlanViewModel @Inject constructor(
    private val application: Application,
    private val dcaPlanDao: DcaPlanDao,
    private val userPreferences: UserPreferences,
    calculateMonthlyCost: CalculateMonthlyCostUseCase,
    minOrderSizeRepository: MinOrderSizeRepository
) : AndroidViewModel(application) {

    val planForm = PlanFormDelegate(calculateMonthlyCost, minOrderSizeRepository, viewModelScope)

    private val _localState = MutableStateFlow(EditPlanUiState())

    val uiState: StateFlow<EditPlanUiState> = combine(
        _localState,
        planForm.state
    ) { local, form -> local.copy(planForm = form) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EditPlanUiState())

    private var originalPlan: DcaPlanEntity? = null

    fun loadPlan(planId: Long) {
        viewModelScope.launch {
            _localState.update { it.copy(isLoading = true, error = null) }

            try {
                val plan = dcaPlanDao.getPlanById(planId)
                if (plan == null) {
                    _localState.update { it.copy(isLoading = false, error = "Plan not found") }
                    return@launch
                }

                originalPlan = plan

                _localState.update {
                    it.copy(
                        planId = plan.id,
                        crypto = plan.crypto,
                        fiat = plan.fiat,
                        exchange = plan.exchange,
                        exchangeName = plan.exchange.displayName,
                        isLoading = false
                    )
                }

                planForm.initFromPlan(
                    exchange = plan.exchange,
                    crypto = plan.crypto,
                    fiat = plan.fiat,
                    amount = plan.amount.toPlainString(),
                    frequency = plan.frequency,
                    cronExpression = plan.cronExpression ?: "",
                    strategy = plan.strategy,
                    withdrawalEnabled = plan.withdrawalEnabled,
                    withdrawalAddress = plan.withdrawalAddress ?: "",
                    targetAmount = plan.targetAmount?.toPlainString() ?: ""
                )
            } catch (e: Exception) {
                _localState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load plan"
                    )
                }
            }
        }
    }

    fun savePlan(onSuccess: () -> Unit) {
        val state = uiState.value
        val form = state.planForm
        val plan = originalPlan ?: return

        // Guard against concurrent saves
        if (state.isSaving) return

        val amount = form.amount.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _localState.update { it.copy(error = "Please enter a valid amount") }
            return
        }

        // Validate withdrawal address if enabled
        if (form.withdrawalEnabled && !form.isAddressValid) {
            _localState.update { it.copy(error = "Please enter a valid ${form.selectedCrypto} wallet address") }
            return
        }

        viewModelScope.launch {
            _localState.update { it.copy(isSaving = true, error = null) }

            try {
                // Calculate new next execution if frequency changed
                val frequencyChanged = form.selectedFrequency != plan.frequency ||
                    (form.selectedFrequency == DcaFrequency.CUSTOM && form.cronExpression != plan.cronExpression)
                val nextExecution = if (frequencyChanged) {
                    if (form.selectedFrequency == DcaFrequency.CUSTOM) {
                        CronUtils.getNextExecution(form.cronExpression, Instant.now())
                            ?: Instant.now().plus(Duration.ofMinutes(1440))
                    } else {
                        val base = plan.lastExecutedAt ?: Instant.now()
                        val next = base.plus(Duration.ofMinutes(form.selectedFrequency.intervalMinutes))
                        if (next.isAfter(Instant.now())) {
                            next
                        } else if (plan.nextExecutionAt != null && plan.nextExecutionAt.isAfter(Instant.now())) {
                            plan.nextExecutionAt
                        } else {
                            Instant.now()
                        }
                    }
                } else {
                    plan.nextExecutionAt
                }

                val updatedPlan = plan.copy(
                    amount = amount,
                    frequency = form.selectedFrequency,
                    cronExpression = if (form.selectedFrequency == DcaFrequency.CUSTOM) form.cronExpression else null,
                    strategy = form.selectedStrategy,
                    withdrawalEnabled = form.withdrawalEnabled,
                    withdrawalAddress = if (form.withdrawalEnabled) form.withdrawalAddress.trim() else null,
                    nextExecutionAt = nextExecution,
                    targetAmount = form.targetAmount.toBigDecimalOrNull()
                )

                dcaPlanDao.updatePlan(updatedPlan)
                DcaAlarmScheduler.scheduleNextAlarm(application)

                // Auto-enable Market Pulse when saving a plan with market-aware strategy
                if (form.selectedStrategy is DcaStrategy.AthBased || form.selectedStrategy is DcaStrategy.FearAndGreed) {
                    userPreferences.setMarketPulseEnabled(true)
                }

                _localState.update { it.copy(isSaving = false, isSuccess = true) }
                onSuccess()
            } catch (e: Exception) {
                _localState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save plan"
                    )
                }
            }
        }
    }
}
