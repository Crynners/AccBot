package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.local.toEntity
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.exchange.ExchangeApiFactory
import java.math.BigDecimal
import javax.inject.Inject

data class LadderOrder(val cryptoAmount: BigDecimal, val limitPrice: BigDecimal)

sealed class LadderResult {
    data class AllPlaced(val placedTxIds: List<Long>) : LadderResult()
    data class PartialFailure(
        val placedTxIds: List<Long>,
        val failedAtIndex: Int,
        val totalCount: Int,
        val reason: String
    ) : LadderResult()
}

/**
 * Place a sequence of limit sell orders for a single plan. On the first failure we stop
 * and report partial success - already placed orders stay on the exchange and as PENDING
 * transactions in the DB. The caller (UI) can ask the user whether to retry the rest or
 * cancel placed orders manually via the existing cancel flow on plan detail.
 */
class PlaceLadderSellUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
) {
    suspend operator fun invoke(planId: Long, orders: List<LadderOrder>): LadderResult {
        if (orders.size < 2) return LadderResult.PartialFailure(
            emptyList(), 0, orders.size, "Ladder requires at least 2 orders"
        )

        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: return LadderResult.PartialFailure(emptyList(), 0, orders.size, "Plan not found")
        val credentials = credentialsStore.getCredentials(
            plan.connectionId, userPreferences.isSandboxMode()
        ) ?: return LadderResult.PartialFailure(emptyList(), 0, orders.size, "Missing credentials")

        val api = exchangeApiFactory.create(credentials)
        val placed = mutableListOf<Long>()

        orders.forEachIndexed { idx, order ->
            val result = api.limitSell(plan.crypto, plan.fiat, order.cryptoAmount, order.limitPrice)
            when (result) {
                is DcaResult.Success -> {
                    val tx = result.transaction.copy(planId = planId, connectionId = plan.connectionId)
                    val id = database.transactionDao().insertTransaction(tx.toEntity())
                    placed += id
                }
                is DcaResult.Error -> {
                    return LadderResult.PartialFailure(placed, idx, orders.size, result.message)
                }
            }
        }

        try { resolvePendingTransactionsUseCase() } catch (_: Exception) {}
        return LadderResult.AllPlaced(placed)
    }
}
