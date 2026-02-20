package com.accbot.dca.domain.usecase

import android.util.Log
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.exchange.ExchangeApiFactory
import javax.inject.Inject

/**
 * Resolves PENDING transactions by querying exchange APIs for fill details.
 *
 * When Kraken or Coinbase returns a PENDING status (fill details not available within
 * the initial 3-second polling window), the transaction is saved with cryptoAmount=0.
 * This use case finds those transactions and queries the exchange to get the actual
 * fill details, updating them to COMPLETED with real values.
 */
class ResolvePendingTransactionsUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences
) {
    suspend operator fun invoke(): Int {
        val pendingTransactions = database.transactionDao().getPendingTransactionsWithOrderId()
        if (pendingTransactions.isEmpty()) return 0

        val isSandbox = userPreferences.isSandboxMode()
        var resolvedCount = 0

        for (tx in pendingTransactions) {
            try {
                val credentials = credentialsStore.getCredentials(tx.exchange, isSandbox) ?: continue
                val api = exchangeApiFactory.create(credentials)
                val orderId = tx.exchangeOrderId ?: continue

                val filledOrder = api.getOrderStatus(orderId) ?: continue

                // Update the transaction with real fill details
                val updatedTx = tx.copy(
                    cryptoAmount = filledOrder.cryptoAmount,
                    fiatAmount = filledOrder.fiatAmount,
                    price = filledOrder.price,
                    fee = filledOrder.fee,
                    status = filledOrder.status
                )
                database.transactionDao().updateTransaction(updatedTx)
                resolvedCount++

                Log.d(TAG, "Resolved pending transaction ${tx.id}: " +
                    "${updatedTx.cryptoAmount} ${tx.crypto} for ${updatedTx.fiatAmount} ${tx.fiat}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve pending transaction ${tx.id}", e)
            }
        }

        if (resolvedCount > 0) {
            Log.d(TAG, "Resolved $resolvedCount/${pendingTransactions.size} pending transactions")
        }

        return resolvedCount
    }

    companion object {
        private const val TAG = "ResolvePendingTx"
    }
}
