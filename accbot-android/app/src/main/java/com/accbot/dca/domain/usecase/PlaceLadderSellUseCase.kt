package com.accbot.dca.domain.usecase

import android.util.Log
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.exchange.ExchangeApi
import com.accbot.dca.exchange.ExchangeApiFactory
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import javax.inject.Inject

data class LadderOrder(val cryptoAmount: BigDecimal, val limitPrice: BigDecimal)

sealed class LadderResult {
    data class AllPlaced(val placedTxIds: List<Long>) : LadderResult()

    /**
     * Series stopped due to a definitive rejection (pre-flight validation or exchange
     * `confirmedRejection=true`) at [failedAtIndex]. Orders in [placedTxIds] were accepted
     * and saved in DB; order [failedAtIndex] was definitely not placed.
     */
    data class PartialFailure(
        val placedTxIds: List<Long>,
        val failedAtIndex: Int,
        val totalCount: Int,
        val reason: String,
        val validationErrors: List<SellValidation.HardError> = emptyList()
    ) : LadderResult()

    /**
     * Series stopped because order [uncertainAtIndex] timed out or returned an unconfirmed
     * error. All orders in [placedTxIds] ARE confirmed placed on the exchange and persisted
     * locally; order [uncertainAtIndex] MAY have been created on the exchange. Subsequent
     * orders were not sent. Callers must preserve [placedTxIds] and block blind resubmission
     * until resolved.
     */
    data class UncertainOutcome(
        val placedTxIds: List<Long>,
        val uncertainAtIndex: Int,
        val totalCount: Int,
        val reason: String
    ) : LadderResult()
}

/**
 * Place a sequence of limit sell orders for a single plan.
 *
 * Enforces shared ladder validation ([ValidateSellOrderUseCase.validateLadder]) against fresh
 * DB inventory before sending any order. Each leg reserves its quantity before submission,
 * so the confirmed and uncertain outcomes remain visible after leaving the wizard.
 */
class PlaceLadderSellUseCase internal constructor(
    private val database: DcaDatabase,
    private val credentialsProvider: (connectionId: Long, isSandbox: Boolean) -> ExchangeCredentials?,
    private val apiProvider: (ExchangeCredentials) -> ExchangeApi,
    private val isSandboxProvider: () -> Boolean,
    private val isTradingEnabledProvider: () -> Boolean,
    private val validateSellOrderUseCase: ValidateSellOrderUseCase,
    private val resolvePending: suspend () -> Int,
    private val perOrderTimeoutMs: Long = DEFAULT_PER_ORDER_TIMEOUT_MS,
    private val messages: SellMessages = SellMessages.English
) {
    @Inject constructor(
        database: DcaDatabase,
        credentialsStore: CredentialsStore,
        exchangeApiFactory: ExchangeApiFactory,
        userPreferences: UserPreferences,
        validateSellOrderUseCase: ValidateSellOrderUseCase,
        resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase,
        messages: AndroidSellMessages
    ) : this(
        database = database,
        credentialsProvider = { connId, sandbox -> credentialsStore.getCredentials(connId, sandbox) },
        apiProvider = { exchangeApiFactory.create(it) },
        isSandboxProvider = { userPreferences.isSandboxMode() },
        isTradingEnabledProvider = { userPreferences.isTradingEnabled() },
        validateSellOrderUseCase = validateSellOrderUseCase,
        resolvePending = { resolvePendingTransactionsUseCase() },
        messages = messages
    )

    suspend operator fun invoke(
        planId: Long,
        orders: List<LadderOrder>,
        minOrderFiat: BigDecimal? = null,
        onOrderPlaced: ((placedCount: Int, totalCount: Int) -> Unit)? = null
    ): LadderResult {
        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: return LadderResult.PartialFailure(emptyList(), 0, orders.size, messages.planNotFound())

        if (!plan.allowSells || !isTradingEnabledProvider()) {
            return LadderResult.PartialFailure(
                placedTxIds = emptyList(),
                failedAtIndex = 0,
                totalCount = orders.size,
                reason = messages.sellsDisabled(),
                validationErrors = listOf(SellValidation.HardError.SellsDisabled)
            )
        }

        val credentials = credentialsProvider(plan.connectionId, isSandboxProvider())
            ?: return LadderResult.PartialFailure(emptyList(), 0, orders.size, messages.missingCredentials())

        val api = apiProvider(credentials)
        if (!api.supportsLimitSell) {
            return LadderResult.PartialFailure(
                emptyList(), 0, orders.size,
                messages.limitSellsUnsupported(plan.exchange.displayName)
            )
        }

        val effectiveMinOrderFiat = minOrderFiat ?: plan.exchange.minOrderSize[plan.fiat] ?: BigDecimal.ZERO
        val hardErrors = validateSellOrderUseCase.validateLadder(
            planId = planId,
            orders = orders,
            minOrderFiat = effectiveMinOrderFiat,
            currentSpot = null
        ).filterIsInstance<SellValidation.HardError>()

        if (hardErrors.isNotEmpty()) {
            return LadderResult.PartialFailure(
                placedTxIds = emptyList(),
                failedAtIndex = 0,
                totalCount = orders.size,
                reason = messages.hardError(hardErrors.first()),
                validationErrors = hardErrors
            )
        }

        val placed = mutableListOf<Long>()

        orders.forEachIndexed { idx, order ->
            val result = submitReservedSellOrder(database, plan, api, order, perOrderTimeoutMs, messages) {
                validateSellOrderUseCase(
                    planId, order.cryptoAmount, order.limitPrice, effectiveMinOrderFiat,
                    currentSpot = null
                ).filterIsInstance<SellValidation.HardError>()
            }
            when (result) {
                is PlaceSellResult.Uncertain -> {
                    bestEffortResolve(placed)
                    return LadderResult.UncertainOutcome(
                        placedTxIds = placed.toList(),
                        uncertainAtIndex = idx,
                        totalCount = orders.size,
                        reason = result.reason
                    )
                }
                is PlaceSellResult.Placed -> {
                    placed += result.txId
                    onOrderPlaced?.invoke(placed.size, orders.size)
                }
                is PlaceSellResult.Rejected -> {
                    bestEffortResolve(placed)
                    return LadderResult.PartialFailure(
                        placedTxIds = placed.toList(),
                        failedAtIndex = idx,
                        totalCount = orders.size,
                        reason = result.reason,
                        validationErrors = result.validationErrors
                    )
                }
            }
        }

        bestEffortResolve(placed)
        return LadderResult.AllPlaced(placed.toList())
    }

    private suspend fun bestEffortResolve(placed: List<Long>) {
        if (placed.isEmpty()) return
        try {
            resolvePending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "resolvePending after ladder failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PlaceLadderSellUseCase"
        /** Above OkHttp's 30 s callTimeout, so the HTTP call ends before the coroutine gives up on it. */
        const val DEFAULT_PER_ORDER_TIMEOUT_MS = 35_000L
    }
}
