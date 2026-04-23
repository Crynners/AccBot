package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.exchange.ExchangeApiFactory
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Cancel an open limit sell order.
 *
 * On exchange-cancel success locally marks the transaction as:
 *  - PARTIAL if some fills already happened (cryptoAmount > 0)
 *  - FAILED otherwise
 *
 * On exchange-cancel failure, tries to re-resolve the order status so the UI reflects
 * the true state (the order may have filled between the user pressing cancel and the
 * exchange receiving the request).
 */
class CancelSellOrderUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
) {
    suspend operator fun invoke(txId: Long): Result<Unit> {
        val tx = database.transactionDao().getTransactionById(txId)
            ?: return Result.failure(IllegalArgumentException("Transakce $txId neexistuje"))

        val orderId = tx.exchangeOrderId
            ?: return Result.failure(IllegalStateException("Transakce nema exchangeOrderId"))

        val credentials = tx.connectionId?.let {
            credentialsStore.getCredentials(it, userPreferences.isSandboxMode())
        } ?: return Result.failure(IllegalStateException("Chybi credentials"))

        val api = exchangeApiFactory.create(credentials)
        val cancelResult = api.cancelOrder(orderId, tx.crypto, tx.fiat)

        return if (cancelResult.isSuccess) {
            val newStatus = if (tx.cryptoAmount > BigDecimal.ZERO)
                TransactionStatus.PARTIAL else TransactionStatus.FAILED
            database.transactionDao().updateResolvedTransaction(
                id = txId,
                newStatus = newStatus,
                cryptoAmount = tx.cryptoAmount,
                fiatAmount = tx.fiatAmount,
                price = tx.price,
                fee = tx.fee
            )
            Result.success(Unit)
        } else {
            try {
                resolvePendingTransactionsUseCase()
            } catch (_: Exception) {
                // Best effort.
            }
            cancelResult
        }
    }
}
