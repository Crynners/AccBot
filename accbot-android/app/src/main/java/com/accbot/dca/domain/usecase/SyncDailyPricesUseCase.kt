package com.accbot.dca.domain.usecase

import android.util.Log
import com.accbot.dca.data.local.DailyPriceDao
import com.accbot.dca.data.local.DailyPriceEntity
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.remote.MarketDataService
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Orchestrates two-phase daily price sync:
 * 1. Forward sync (every sync): CoinGecko — fills the small gap between latest cached date and today
 * 2. Historical backfill (one-time per pair): CryptoCompare — fetches older data in ≤2000-day chunks backwards
 *
 * Historical prices are immutable — once fetched, they never need re-fetching.
 * After the one-time backfill, subsequent syncs only run phase 1 (0-2 API calls total).
 */
class SyncDailyPricesUseCase @Inject constructor(
    private val transactionDao: TransactionDao,
    private val dailyPriceDao: DailyPriceDao,
    private val marketDataService: MarketDataService
) {
    companion object {
        private const val TAG = "SyncDailyPrices"
        private const val RATE_LIMIT_DELAY_MS = 1500L
        private const val BACKFILL_CHUNK_DAYS = 2000 // CryptoCompare max per call
    }

    /**
     * Sync daily prices for all crypto/fiat pairs that have completed transactions.
     * @return number of pairs synced successfully
     */
    suspend fun sync(): Int {
        val holdings = transactionDao.getHoldingsByPair()
        if (holdings.isEmpty()) return 0

        val today = LocalDate.now()
        var syncedCount = 0

        for (holding in holdings) {
            try {
                val crypto = holding.crypto
                val fiat = holding.fiat

                // Compute desired start date from earliest transaction
                val earliestTx = transactionDao.getEarliestTransactionDate(crypto, fiat)
                val desiredStartDate = if (earliestTx != null) {
                    val earliestTxDate = java.time.Instant.ofEpochMilli(earliestTx)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val idealDays = ChronoUnit.DAYS.between(earliestTxDate, today).toInt()
                        .coerceIn(30, 3650)
                    today.minusDays(idealDays.toLong())
                } else {
                    today.minusDays(365)
                }

                // ── Phase 1: Forward sync (CoinGecko, always, fast) ──
                val latestDay = dailyPriceDao.getLatestDay(crypto, fiat)
                val latestDate = latestDay?.let { LocalDate.ofEpochDay(it) }

                if (latestDate == null) {
                    // Brand new pair — bootstrap with last 365 days via CoinGecko
                    Log.d(TAG, "[$crypto/$fiat] Bootstrap: fetching last 365 days")
                    val prices = marketDataService.getDailyPriceHistory(crypto, fiat, 365)
                    if (prices != null && prices.isNotEmpty()) {
                        insertPrices(crypto, fiat, prices)
                        Log.d(TAG, "[$crypto/$fiat] Bootstrap: stored ${prices.size} days")
                    } else {
                        Log.w(TAG, "[$crypto/$fiat] Bootstrap: no data returned")
                    }
                    delay(RATE_LIMIT_DELAY_MS)
                } else if (latestDate.isBefore(today.minusDays(1))) {
                    // Fill forward gap
                    val gapDays = ChronoUnit.DAYS.between(latestDate, today).toInt() + 1
                    Log.d(TAG, "[$crypto/$fiat] Forward sync: fetching $gapDays days gap")
                    val prices = marketDataService.getDailyPriceHistory(crypto, fiat, gapDays)
                    if (prices != null && prices.isNotEmpty()) {
                        insertPrices(crypto, fiat, prices)
                        Log.d(TAG, "[$crypto/$fiat] Forward sync: stored ${prices.size} days")
                    }
                    delay(RATE_LIMIT_DELAY_MS)
                }

                // ── Phase 2: Historical backfill (CryptoCompare, one-time, backwards in chunks) ──
                val earliestCachedDay = dailyPriceDao.getEarliestDay(crypto, fiat)
                if (earliestCachedDay != null && earliestCachedDay > desiredStartDate.toEpochDay() + 1) {
                    Log.d(TAG, "[$crypto/$fiat] Backfill: earliest cached=${LocalDate.ofEpochDay(earliestCachedDay)}, desired=$desiredStartDate")
                    var chunkEndDate = LocalDate.ofEpochDay(earliestCachedDay).minusDays(1)

                    while (!chunkEndDate.isBefore(desiredStartDate)) {
                        val daysNeeded = ChronoUnit.DAYS.between(desiredStartDate, chunkEndDate).toInt() + 1
                        val limit = daysNeeded.coerceAtMost(BACKFILL_CHUNK_DAYS)

                        Log.d(TAG, "[$crypto/$fiat] Backfill chunk: $limit days ending at $chunkEndDate")
                        val prices = marketDataService.getDailyPriceHistoryRange(crypto, fiat, chunkEndDate, limit)
                        if (prices != null && prices.isNotEmpty()) {
                            insertPrices(crypto, fiat, prices)
                            val earliest = prices.minOf { it.first }
                            Log.d(TAG, "[$crypto/$fiat] Backfill chunk: stored ${prices.size} days (earliest=$earliest)")
                            chunkEndDate = earliest.minusDays(1)
                        } else {
                            Log.w(TAG, "[$crypto/$fiat] Backfill chunk: no data returned, stopping")
                            break
                        }

                        delay(RATE_LIMIT_DELAY_MS)
                    }
                }

                syncedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing prices for ${holding.crypto}/${holding.fiat}", e)
            }
        }

        return syncedCount
    }

    private suspend fun insertPrices(crypto: String, fiat: String, prices: List<Pair<LocalDate, java.math.BigDecimal>>) {
        val entities = prices.map { (date, price) ->
            DailyPriceEntity(
                crypto = crypto,
                fiat = fiat,
                dateEpochDay = date.toEpochDay(),
                price = price
            )
        }
        dailyPriceDao.insertPrices(entities)
    }
}
