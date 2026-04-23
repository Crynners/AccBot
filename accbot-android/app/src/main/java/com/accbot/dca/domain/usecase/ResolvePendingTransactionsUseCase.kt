package com.accbot.dca.domain.usecase

import android.util.Log
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.exchange.ExchangeApiFactory
import javax.inject.Inject

/**
 * Resolves PENDING/PARTIAL transactions by querying exchange APIs for fill details.
 *
 * Two scenarios produce resolvable rows:
 *  - BUY: Kraken/Coinbase sometimes return PENDING at place-order time because fill
 *    details weren't available within the initial 3-second polling window.
 *  - SELL: limit sell orders are always PENDING until (partially) filled or cancelled.
 *
 * Uses the guarded [TransactionDao.updateResolvedTransaction] UPDATE so a concurrent
 * user cancel (which sets status=FAILED) is never clobbered.
 */
class ResolvePendingTransactionsUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences
) {
    suspend operator fun invoke(): Int {
        val pendingTransactions = database.transactionDao().getResolvablePendingTransactions()
        if (pendingTransactions.isEmpty()) return 0

        val isSandbox = userPreferences.isSandboxMode()
        var resolvedCount = 0

        for (tx in pendingTransactions) {
            try {
                @Suppress("DEPRECATION")
                val credentials = if (tx.connectionId != null) {
                    credentialsStore.getCredentials(tx.connectionId, isSandbox)
                } else {
                    credentialsStore.getCredentials(tx.exchange, isSandbox)
                } ?: continue
                val api = exchangeApiFactory.create(credentials)
                val orderId = tx.exchangeOrderId ?: continue

                val result = api.getOrderStatus(orderId, tx.crypto, tx.fiat) ?: continue

                val rows = database.transactionDao().updateResolvedTransaction(
                    id = tx.id,
                    newStatus = result.status,
                    cryptoAmount = result.filledCryptoAmount,
                    fiatAmount = result.filledFiatAmount,
                    price = result.avgFillPrice ?: tx.price,
                    fee = result.fee ?: tx.fee
                )
                if (rows > 0) resolvedCount++
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
