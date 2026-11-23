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
    private val orderTimeoutMs: Long = DEFAULT_ORDER_TIMEOUT_MS
) {
    @Inject constructor(
        database: DcaDatabase,
        credentialsStore: CredentialsStore,
        exchangeApiFactory: ExchangeApiFactory,
        userPreferences: UserPreferences,
        validateSellOrderUseCase: ValidateSellOrderUseCase,
        resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
    ) : this(
        database = database,
        credentialsProvider = { connId, sandbox -> credentialsStore.getCredentials(connId, sandbox) },
        apiProvider = { exchangeApiFactory.create(it) },
        isSandboxProvider = { userPreferences.isSandboxMode() },
        isTradingEnabledProvider = { userPreferences.isTradingEnabled() },
        validateSellOrderUseCase = validateSellOrderUseCase,
        resolvePending = { resolvePendingTransactionsUseCase() }
    )

    suspend operator fun invoke(
        planId: Long,
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal,
        minOrderFiat: BigDecimal? = null
    ): PlaceSellResult {
        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: return PlaceSellResult.Rejected("Plan $planId neexistuje")

        if (!plan.allowSells || !isTradingEnabledProvider()) {
            return PlaceSellResult.Rejected(
                reason = "Prodeje nejsou pro tento plán povoleny",
                validationErrors = listOf(SellValidation.HardError.SellsDisabled)
            )
        }

        val credentials = credentialsProvider(plan.connectionId, isSandboxProvider())
            ?: return PlaceSellResult.Rejected("Chybi credentials pro connection ${plan.connectionId}")

        val api = apiProvider(credentials)
        if (!api.supportsLimitSell) {
            return PlaceSellResult.Rejected("${plan.exchange.displayName} nepodporuje limitní prodeje")
        }

        val effectiveMinOrderFiat = minOrderFiat ?: plan.exchange.minOrderSize[plan.fiat] ?: BigDecimal.ZERO
        val result = submitReservedSellOrder(
            database, plan, api, LadderOrder(cryptoAmount, limitPrice), orderTimeoutMs
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
        const val DEFAULT_ORDER_TIMEOUT_MS = 15_000L

        internal fun describeHardError(error: SellValidation.HardError): String = when (error) {
            SellValidation.HardError.AmountMustBePositive -> "Amount must be greater than 0"
            SellValidation.HardError.PriceMustBePositive -> "Limit price must be greater than 0"
            is SellValidation.HardError.MinOrderTooLow -> "Order value is below minimum (${error.minOrderFiat})"
            is SellValidation.HardError.InsufficientInventory -> "Insufficient available inventory (${error.available})"
            is SellValidation.HardError.LadderCountOutOfRange -> "Order count must be between ${error.min} and ${error.max}"
            is SellValidation.HardError.LadderLegBelowMin -> "Smallest order (${error.smallestFiat}) is below minimum (${error.minOrderFiat})"
            SellValidation.HardError.LadderOrderInvalid -> "Invalid order parameters in series"
            SellValidation.HardError.SellsDisabled -> "Sells are disabled for this plan"
        }
    }
}
