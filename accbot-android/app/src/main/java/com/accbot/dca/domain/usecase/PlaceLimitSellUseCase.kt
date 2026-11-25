package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.exchange.ExchangeApi
import com.accbot.dca.exchange.ExchangeApiFactory
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Outcome of placing a single limit sell order.
 *
 * Explicitly separates:
 * - [Placed]: exchange accepted the order and it was persisted in Room.
 * - [Rejected]: pre-flight validation failed or exchange confirmed rejection (`confirmedRejection=true`);
 *   no order exists on the exchange, so editing and retrying is safe.
 * - [Uncertain]: timeout, transport drop, HTTP 5xx/429, or unconfirmed error; an order MAY exist
 *   on the exchange. Callers must block blind resubmission until the user verifies and resolves.
 */
sealed class PlaceSellResult {
    data class Placed(val txId: Long, val exchangeOrderId: String?) : PlaceSellResult()
    data class Rejected(
        val reason: String,
        val validationErrors: List<SellValidation.HardError> = emptyList()
    ) : PlaceSellResult()
    data class Uncertain(val reason: String) : PlaceSellResult()
}

/**
 * Place a limit sell order for an existing DCA plan.
 *
 * Enforces shared pre-flight validation ([ValidateSellOrderUseCase]) against fresh DB inventory
 * and reserves the quantity before sending the order.
 */
class PlaceLimitSellUseCase internal constructor(
    private val database: DcaDatabase,
    private val credentialsProvider: (connectionId: Long, isSandbox: Boolean) -> ExchangeCredentials?,
    private val apiProvider: (ExchangeCredentials) -> ExchangeApi,
    private val isSandboxProvider: () -> Boolean,
    private val isTradingEnabledProvider: () -> Boolean,
    private val validateSellOrderUseCase: ValidateSellOrderUseCase,
    private val resolvePending: suspend () -> Int,
    private val orderTimeoutMs: Long = DEFAULT_ORDER_TIMEOUT_MS,
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
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal,
        minOrderFiat: BigDecimal? = null
    ): PlaceSellResult {
        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: return PlaceSellResult.Rejected(messages.planNotFound())

        if (!plan.allowSells || !isTradingEnabledProvider()) {
            return PlaceSellResult.Rejected(
                reason = messages.sellsDisabled(),
                validationErrors = listOf(SellValidation.HardError.SellsDisabled)
            )
        }

        val credentials = credentialsProvider(plan.connectionId, isSandboxProvider())
            ?: return PlaceSellResult.Rejected(messages.missingCredentials())

        val api = apiProvider(credentials)
        if (!api.supportsLimitSell) {
            return PlaceSellResult.Rejected(messages.limitSellsUnsupported(plan.exchange.displayName))
        }

        val effectiveMinOrderFiat = minOrderFiat ?: plan.exchange.minOrderSize[plan.fiat] ?: BigDecimal.ZERO
        val result = submitReservedSellOrder(
            database, plan, api, LadderOrder(cryptoAmount, limitPrice), orderTimeoutMs, messages
        ) {
            validateSellOrderUseCase(planId, cryptoAmount, limitPrice, effectiveMinOrderFiat, currentSpot = null)
                .filterIsInstance<SellValidation.HardError>()
        }
        if (result is PlaceSellResult.Placed) {
            try {
                resolvePending()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Resolution can retry later against the persisted exchange order ID.
            }
        }
        return result
    }

    companion object {
        /** Above OkHttp's 30 s callTimeout, so the HTTP call ends before the coroutine gives up on it. */
        const val DEFAULT_ORDER_TIMEOUT_MS = 35_000L
    }
}
