package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.exchange.ExchangeApi
import com.accbot.dca.exchange.ExchangeApiFactory
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Cancel an open limit sell order and verify final fills from the exchange.
 *
 * After exchange-cancel succeeds:
 *  - Queries [ExchangeApi.getOrderStatus] (with one brief retry if the exchange still reports
 *    an open/pending-cancel state) so any fills that occurred between the last poll and the
 *    cancel request are captured accurately.
 *  - Persists the verified terminal status:
 *      - [TransactionStatus.COMPLETED] if the order filled completely before cancellation landed
 *      - [TransactionStatus.PARTIAL_CANCELLED] if some fills occurred (releases unfilled
 *        reservation and counts verified proceeds)
 *      - [TransactionStatus.CANCELLED] if zero fills occurred
 *  - If the exchange cannot confirm a terminal state yet (network error or still pending cancel),
 *    keeps the row open (updating any newly observed partial fill progress) so we never drop
 *    unverified fills or prematurely release reservations, and returns failure asking the user
 *    to refresh.
 *
 * On exchange-cancel failure, queries this order's status directly (and runs best-effort pending
 * resolution) so an order that filled in the race immediately reflects its true state.
 */
class CancelSellOrderUseCase internal constructor(
    private val database: DcaDatabase,
    private val credentialsProvider: (connectionId: Long, isSandbox: Boolean) -> ExchangeCredentials?,
    private val apiProvider: (ExchangeCredentials) -> ExchangeApi,
    private val isSandboxProvider: () -> Boolean,
    private val resolvePending: suspend () -> Int,
    private val verifyRetryDelayMs: Long = DEFAULT_VERIFY_RETRY_DELAY_MS,
    private val messages: SellMessages = SellMessages.English
) {
    @Inject constructor(
        database: DcaDatabase,
        credentialsStore: CredentialsStore,
        exchangeApiFactory: ExchangeApiFactory,
        userPreferences: UserPreferences,
        resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase,
        messages: AndroidSellMessages
    ) : this(
        database = database,
        credentialsProvider = { connId, sandbox -> credentialsStore.getCredentials(connId, sandbox) },
        apiProvider = { exchangeApiFactory.create(it) },
        isSandboxProvider = { userPreferences.isSandboxMode() },
        resolvePending = { resolvePendingTransactionsUseCase() },
        messages = messages
    )

    suspend operator fun invoke(txId: Long): Result<Unit> {
        val tx = database.transactionDao().getTransactionById(txId)
            ?: return Result.failure(IllegalArgumentException(messages.transactionNotFound()))

        if (tx.status.isTerminal) {
            return when (tx.status) {
                TransactionStatus.CANCELLED, TransactionStatus.PARTIAL_CANCELLED -> Result.success(Unit)
                else -> Result.failure(IllegalStateException(messages.orderAlreadyClosed()))
            }
        }

        val orderId = tx.exchangeOrderId
            ?: return Result.failure(IllegalStateException(messages.orderHasNoExchangeId()))

        val credentials = tx.connectionId?.let {
            credentialsProvider(it, isSandboxProvider())
        } ?: return Result.failure(IllegalStateException(messages.missingCredentials()))

        val api = apiProvider(credentials)
        val cancelResult = api.cancelOrder(orderId, tx.crypto, tx.fiat)

        return if (cancelResult.isSuccess) {
            var verified = api.getOrderStatus(orderId, tx.crypto, tx.fiat)
            if (verified != null && verified.status.isOpen) {
                if (verifyRetryDelayMs > 0) delay(verifyRetryDelayMs)
                verified = api.getOrderStatus(orderId, tx.crypto, tx.fiat) ?: verified
            }

            if (verified == null) {
                return Result.failure(
                    IllegalStateException(messages.cancelSentUnverified())
                )
            }

            val normalized = normalizeOrderStatus(verified, tx)

            if (normalized.status.isOpen) {
                // Exchange still reports the order as open/pending-cancel; persist any newer
                // fill progress while keeping it open so polling/refresh finishes verification.
                database.transactionDao().updateResolvedTransaction(
                    id = txId,
                    newStatus = normalized.status,
                    cryptoAmount = normalized.filledCrypto,
                    fiatAmount = normalized.filledFiat,
                    price = normalized.price,
                    fee = normalized.fee
                )
                return Result.failure(
                    IllegalStateException(messages.cancelNotConfirmed())
                )
            }

            // Terminal state verified (COMPLETED, PARTIAL_CANCELLED, CANCELLED, or FAILED).
            val finalStatus = if (normalized.status == TransactionStatus.FAILED &&
                normalized.filledCrypto.signum() == 0
            ) {
                TransactionStatus.CANCELLED
            } else {
                normalized.status
            }

            database.transactionDao().updateResolvedTransaction(
                id = txId,
                newStatus = finalStatus,
                cryptoAmount = normalized.filledCrypto,
                fiatAmount = normalized.filledFiat,
                price = normalized.price,
                fee = normalized.fee
            )
            Result.success(Unit)
        } else {
            try {
                val statusResult = api.getOrderStatus(orderId, tx.crypto, tx.fiat)
                if (statusResult != null) {
                    val normalized = normalizeOrderStatus(statusResult, tx)
                    database.transactionDao().updateResolvedTransaction(
                        id = txId,
                        newStatus = normalized.status,
                        cryptoAmount = normalized.filledCrypto,
                        fiatAmount = normalized.filledFiat,
                        price = normalized.price,
                        fee = normalized.fee
                    )
                }
            } catch (_: Exception) {
                // Best effort.
            }
            try {
                resolvePending()
            } catch (_: Exception) {
                // Best effort.
            }
            cancelResult
        }
    }

    companion object {
        private const val DEFAULT_VERIFY_RETRY_DELAY_MS = 300L
    }
}
