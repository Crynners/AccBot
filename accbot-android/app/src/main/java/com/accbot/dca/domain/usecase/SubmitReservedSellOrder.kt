package com.accbot.dca.domain.usecase

import androidx.room.withTransaction
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.exchange.ExchangeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal

private val sellSubmissionLock = Mutex()

internal suspend fun tryReviewSell(review: suspend () -> Unit): Boolean {
    if (!sellSubmissionLock.tryLock()) return false
    try {
        review()
        return true
    } finally {
        sellSubmissionLock.unlock()
    }
}

internal suspend fun submitReservedSellOrder(
    database: DcaDatabase,
    plan: DcaPlanEntity,
    api: ExchangeApi,
    order: LadderOrder,
    timeoutMillis: Long,
    validate: suspend () -> List<SellValidation.HardError>
): PlaceSellResult = sellSubmissionLock.withLock {
    val dao = database.transactionDao()
    val intent = TransactionEntity(
        planId = plan.id,
        connectionId = plan.connectionId,
        exchange = plan.exchange,
        crypto = plan.crypto,
        fiat = plan.fiat,
        fiatAmount = BigDecimal.ZERO,
        cryptoAmount = BigDecimal.ZERO,
        price = order.limitPrice,
        fee = BigDecimal.ZERO,
        status = TransactionStatus.PENDING,
        side = TransactionSide.SELL,
        requestedCryptoAmount = order.cryptoAmount,
        limitPrice = order.limitPrice
    )

    var rejection: PlaceSellResult.Rejected? = null
    val txId = database.withTransaction {
        val current = database.dcaPlanDao().getPlanById(plan.id)
        if (current == null || !current.allowSells || current.connectionId != plan.connectionId ||
            current.exchange != plan.exchange || current.crypto != plan.crypto || current.fiat != plan.fiat
        ) {
            rejection = PlaceSellResult.Rejected("The sell plan changed. Review it again.")
            return@withTransaction null
        }
        if (dao.hasUnidentifiedSell(plan.id)) {
            rejection = PlaceSellResult.Rejected("Review the pending sell in History before submitting a new one.")
            return@withTransaction null
        }
        val errors = validate()
        if (errors.isNotEmpty()) {
            rejection = PlaceSellResult.Rejected(PlaceLimitSellUseCase.describeHardError(errors.first()), errors)
            return@withTransaction null
        }
        dao.insertTransaction(intent)
    } ?: return@withLock requireNotNull(rejection)

    val result = try {
        withTimeoutOrNull(timeoutMillis) { api.limitSell(plan.crypto, plan.fiat, order.cryptoAmount, order.limitPrice) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        return@withLock PlaceSellResult.Uncertain("The exchange response could not be read.")
    }

    when (result) {
        is DcaResult.Success -> {
            val receipt = result.transaction
            val orderId = receipt.exchangeOrderId?.takeIf { it.isNotBlank() }
                ?: return@withLock PlaceSellResult.Uncertain("The exchange did not return an order ID.")
            try {
                withContext(NonCancellable) {
                    dao.updateTransaction(intent.copy(
                        id = txId,
                        exchangeOrderId = orderId,
                        status = receipt.status,
                        cryptoAmount = receipt.cryptoAmount,
                        fiatAmount = receipt.fiatAmount,
                        price = receipt.price,
                        fee = receipt.fee,
                        feeAsset = receipt.feeAsset,
                        limitPrice = receipt.limitPrice ?: order.limitPrice,
                        requestedCryptoAmount = receipt.requestedCryptoAmount ?: order.cryptoAmount
                    ))
                }
            } catch (_: Exception) {
                return@withLock PlaceSellResult.Uncertain("The exchange accepted the order but AccBot could not save its ID.")
            }
            PlaceSellResult.Placed(txId, orderId)
        }
        is DcaResult.Error -> {
            if (result.confirmedRejection && !result.retryable) {
                withContext(NonCancellable) {
                    dao.updateTransaction(intent.copy(id = txId, status = TransactionStatus.FAILED, errorMessage = result.message))
                }
                PlaceSellResult.Rejected(result.message)
            } else {
                PlaceSellResult.Uncertain(result.message)
            }
        }
        null -> PlaceSellResult.Uncertain("The exchange did not confirm the sell in time.")
    }
}
