package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.local.toEntity
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.exchange.ExchangeApiFactory
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Place a limit sell order for an existing DCA plan.
 *
 * On success inserts a PENDING SELL transaction (filled fields=0, requestedCryptoAmount
 * preserved) and returns the new row id. The caller (UI / worker) is expected to refresh
 * the open-sells list after.
 *
 * Also kicks off the resolver best-effort after insert so if the order filled instantly
 * it's already marked COMPLETED when the UI reads back.
 */
class PlaceLimitSellUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
) {
    suspend operator fun invoke(
        planId: Long,
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal
    ): Result<Long> {
        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: return Result.failure(IllegalArgumentException("Plan $planId neexistuje"))

        val credentials = credentialsStore.getCredentials(
            plan.connectionId,
            userPreferences.isSandboxMode()
        ) ?: return Result.failure(
            IllegalStateException("Chybi credentials pro connection ${plan.connectionId}")
        )

        val api = exchangeApiFactory.create(credentials)
        val result = api.limitSell(plan.crypto, plan.fiat, cryptoAmount, limitPrice)

        return when (result) {
            is DcaResult.Success -> {
                val tx = result.transaction.copy(
                    planId = planId,
                    connectionId = plan.connectionId
                )
                val txId = database.transactionDao().insertTransaction(tx.toEntity())
                try {
                    resolvePendingTransactionsUseCase()
                } catch (_: Exception) {
                    // Best effort - resolver runs periodically anyway.
                }
                Result.success(txId)
            }
            is DcaResult.Error -> Result.failure(
                IllegalStateException(result.message)
            )
        }
    }
}
