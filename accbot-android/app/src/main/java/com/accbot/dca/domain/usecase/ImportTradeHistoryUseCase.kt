package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.exchange.ExchangeApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

sealed class ApiImportResultState {
    data class Success(val imported: Int, val skipped: Int) : ApiImportResultState()
    data class Error(val message: String) : ApiImportResultState()
}

sealed class ApiImportProgress {
    data class Fetching(val page: Int, val totalFetched: Int) : ApiImportProgress()
    data class Deduplicating(val count: Int) : ApiImportProgress()
    data class Importing(val newCount: Int) : ApiImportProgress()
    data class Complete(val imported: Int, val skipped: Int) : ApiImportProgress()
    data class Error(val message: String) : ApiImportProgress()
}

class ImportTradeHistoryUseCase @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun importFromApi(
        api: ExchangeApi,
        planId: Long,
        crypto: String,
        fiat: String,
        exchange: Exchange
    ): Flow<ApiImportProgress> = flow {
        try {
            // Get the latest transaction timestamp for incremental import
            val latestTimestamp = transactionDao.getLatestTransactionTimestamp(planId)
            val sinceInstant = latestTimestamp?.let { Instant.ofEpochMilli(it) }

            // Fetch all pages
            val allTrades = mutableListOf<com.accbot.dca.domain.model.HistoricalTrade>()
            var cursor = sinceInstant
            var page = 0
            val maxPages = 10_000  // Safety cap only; pagination stops naturally via hasMore

            do {
                page++
                emit(ApiImportProgress.Fetching(page, allTrades.size))

                val result = api.getTradeHistory(
                    crypto = crypto,
                    fiat = fiat,
                    sinceTimestamp = cursor,
                    limit = 100
                )

                // Filter to BUY trades only
                val buyTrades = result.trades.filter { it.side == "BUY" }
                allTrades.addAll(buyTrades)

                // Update cursor to the latest trade timestamp for next page
                if (result.trades.isNotEmpty()) {
                    cursor = result.trades.maxOf { it.timestamp }
                }

                if (!result.hasMore || page >= maxPages) break
            } while (true)

            if (allTrades.isEmpty()) {
                emit(ApiImportProgress.Complete(imported = 0, skipped = 0))
                return@flow
            }

            // Dedup via existing exchangeOrderIds
            emit(ApiImportProgress.Deduplicating(allTrades.size))
            val existingOrderIds = transactionDao.getExchangeOrderIdsByPlan(planId).toSet()
            val newTrades = allTrades.filter { it.orderId !in existingOrderIds }

            if (newTrades.isEmpty()) {
                emit(ApiImportProgress.Complete(imported = 0, skipped = allTrades.size))
                return@flow
            }

            emit(ApiImportProgress.Importing(newTrades.size))

            // Map to TransactionEntity and batch insert
            val entities = newTrades.map { trade ->
                TransactionEntity(
                    planId = planId,
                    exchange = exchange,
                    crypto = trade.crypto,
                    fiat = trade.fiat,
                    fiatAmount = trade.fiatAmount,
                    cryptoAmount = trade.cryptoAmount,
                    price = trade.price,
                    fee = trade.fee,
                    feeAsset = trade.feeAsset,
                    status = TransactionStatus.COMPLETED,
                    exchangeOrderId = trade.orderId,
                    executedAt = trade.timestamp
                )
            }

            transactionDao.insertTransactions(entities)

            emit(ApiImportProgress.Complete(
                imported = newTrades.size,
                skipped = allTrades.size - newTrades.size
            ))
        } catch (e: Exception) {
            emit(ApiImportProgress.Error(e.message ?: "Import failed"))
        }
    }
}
