package com.accbot.dca.domain.usecase

import android.util.Log
import androidx.room.withTransaction
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.exchange.ExchangeApi
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.exchange.OrderStatusResult
import com.accbot.dca.service.NotificationService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import javax.inject.Inject

/**
 * Normalized fill snapshot derived from an [OrderStatusResult].
 * Ensures any terminal cancelled/failed order that had partial fills is classified
 * as [TransactionStatus.PARTIAL_CANCELLED] and that proceeds (`fiatAmount`) are
 * consistently computed from filled crypto × average fill price when the exchange
 * omits quote totals.
 */
internal data class NormalizedOrderFill(
    val status: TransactionStatus,
    val filledCrypto: BigDecimal,
    val filledFiat: BigDecimal,
    val price: BigDecimal,
    val fee: BigDecimal
)

internal fun normalizeOrderStatus(
    result: OrderStatusResult,
    tx: TransactionEntity
): NormalizedOrderFill {
    val filledCrypto = result.filledCryptoAmount.max(BigDecimal.ZERO)
    val resolvedStatus = when (result.status) {
        TransactionStatus.CANCELLED, TransactionStatus.FAILED ->
            if (filledCrypto > BigDecimal.ZERO) TransactionStatus.PARTIAL_CANCELLED
            else result.status
        else -> result.status
    }
    val verifiedAverage = result.avgFillPrice?.takeIf { it > BigDecimal.ZERO }
    val previousAmountStillApplies = filledCrypto.compareTo(tx.cryptoAmount) == 0 && tx.fiatAmount > BigDecimal.ZERO
    val resolvedFiat = when {
        filledCrypto <= BigDecimal.ZERO -> BigDecimal.ZERO
        result.filledFiatAmount > BigDecimal.ZERO -> result.filledFiatAmount
        verifiedAverage != null -> filledCrypto.multiply(verifiedAverage).setScale(2, RoundingMode.HALF_UP)
        previousAmountStillApplies -> tx.fiatAmount
        else -> BigDecimal.ZERO
    }
    val resolvedPrice = verifiedAverage ?: if (filledCrypto > BigDecimal.ZERO && resolvedFiat > BigDecimal.ZERO) {
        resolvedFiat.divide(filledCrypto, 12, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO
    val resolvedFee = result.fee ?: tx.fee
    return NormalizedOrderFill(
        status = resolvedStatus,
        filledCrypto = filledCrypto,
        filledFiat = resolvedFiat,
        price = resolvedPrice,
        fee = resolvedFee
    )
}

/**
 * Resolves PENDING/PARTIAL transactions by querying exchange APIs for fill details.
 *
 * Two scenarios produce resolvable rows:
 *  - BUY: Kraken/Coinbase sometimes return PENDING at place-order time because fill
 *    details weren't available within the initial 3-second polling window.
 *  - SELL: limit sell orders are always PENDING until (partially) filled or cancelled.
 *
 * Uses the guarded [TransactionDao.updateResolvedTransaction] UPDATE so a concurrent
 * user cancel (which sets a terminal status) is never clobbered.
 *
 * Fires a system notification for each SELL that transitions to COMPLETED in this run.
 */
class ResolvePendingTransactionsUseCase internal constructor(
    private val database: DcaDatabase,
    private val credentialsProvider: suspend (connectionId: Long?, exchange: Exchange, isSandbox: Boolean) -> ExchangeCredentials?,
    private val apiProvider: (ExchangeCredentials) -> ExchangeApi,
    private val isSandboxProvider: () -> Boolean,
    private val onSellCompleted: suspend (TransactionEntity, NormalizedOrderFill) -> Unit
) {
    @Inject constructor(
        database: DcaDatabase,
        credentialsStore: CredentialsStore,
        exchangeApiFactory: ExchangeApiFactory,
        userPreferences: UserPreferences,
        notificationService: NotificationService
    ) : this(
        database = database,
        credentialsProvider = { connectionId, exchange, isSandbox ->
            @Suppress("DEPRECATION")
            if (connectionId != null) {
                credentialsStore.getCredentials(connectionId, isSandbox)
            } else {
                credentialsStore.getCredentials(exchange, isSandbox)
            }
        },
        apiProvider = { exchangeApiFactory.create(it) },
        isSandboxProvider = { userPreferences.isSandboxMode() },
        onSellCompleted = { tx, normalized ->
            notificationService.showSellFilledNotification(
                crypto = tx.crypto,
                cryptoAmount = normalized.filledCrypto,
                fiatAmount = normalized.filledFiat,
                fiat = tx.fiat,
                price = normalized.price,
                transactionId = tx.id,
                planId = tx.planId,
                exchange = tx.exchange,
                connectionId = tx.connectionId
            )
        }
    )

    internal suspend fun verifyUnidentifiedSell(transactionId: Long, orderId: String): Boolean {
        if (orderId.toLongOrNull()?.let { it > 0 } != true) return false
        val dao = database.transactionDao()
        val pending = dao.getTransactionById(transactionId)?.takeIf {
            it.status == TransactionStatus.PENDING && it.side == TransactionSide.SELL && it.exchangeOrderId.isNullOrBlank()
        } ?: return false
        val isSandbox = isSandboxProvider()
        val credentials = credentialsProvider(pending.connectionId, pending.exchange, isSandbox) ?: return false
        if (credentials.exchange != pending.exchange) return false
        val result = apiProvider(credentials).getOrderStatus(orderId, pending.crypto, pending.fiat) ?: return false
        val identity = result.identity ?: return false
        val pair = when (pending.exchange) {
            Exchange.BINANCE -> pending.crypto + pending.fiat
            Exchange.COINMATE -> "${pending.crypto}_${pending.fiat}"
            else -> return false
        }
        val amount = pending.requestedCryptoAmount ?: return false
        val price = pending.limitPrice ?: return false
        val orderAge = Duration.between(pending.executedAt, identity.createdAt).seconds
        if (identity.id != orderId || identity.side != TransactionSide.SELL || identity.pair != pair ||
            identity.amount.compareTo(amount.setScale(8, RoundingMode.DOWN)) != 0 ||
            identity.limitPrice.compareTo(price.setScale(2, RoundingMode.HALF_UP)) != 0 ||
            orderAge !in -60L..300L
        ) return false
        val fill = normalizeOrderStatus(result, pending)
        return database.withTransaction {
            if (dao.getTransactionById(transactionId) != pending ||
                dao.findMatchingBackupOrder(orderId, pending.connectionId, pending.exchange, pending.crypto, pending.fiat) != null
            ) return@withTransaction false

            dao.updateTransaction(pending.copy(
                exchangeOrderId = orderId,
                status = fill.status,
                cryptoAmount = fill.filledCrypto,
                fiatAmount = fill.filledFiat,
                price = fill.price,
                fee = fill.fee,
                feeAsset = result.feeAsset ?: pending.feeAsset
            ))
            true
        }
    }

    suspend operator fun invoke(): Int {
        val pendingTransactions = database.transactionDao().getResolvablePendingTransactions()
        if (pendingTransactions.isEmpty()) return 0

        val isSandbox = isSandboxProvider()
        var resolvedCount = 0

        for (tx in pendingTransactions) {
            try {
                val credentials = credentialsProvider(tx.connectionId, tx.exchange, isSandbox) ?: continue
                val api = apiProvider(credentials)
                val orderId = tx.exchangeOrderId ?: continue

                val result = api.getOrderStatus(orderId, tx.crypto, tx.fiat) ?: continue
                val normalized = normalizeOrderStatus(result, tx)

                val rows = database.transactionDao().updateResolvedTransaction(
                    id = tx.id,
                    newStatus = normalized.status,
                    cryptoAmount = normalized.filledCrypto,
                    fiatAmount = normalized.filledFiat,
                    price = normalized.price,
                    fee = normalized.fee
                )
                if (rows > 0) {
                    resolvedCount++
                    if (tx.side == TransactionSide.SELL && normalized.status == TransactionStatus.COMPLETED) {
                        try {
                            onSellCompleted(tx, normalized)
                        } catch (e: Exception) {
                            Log.w(TAG, "Sell-filled notification failed for tx ${tx.id}", e)
                        }
                    }
                }
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
