package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DailyPriceDao
import com.accbot.dca.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import javax.inject.Inject

/**
 * A single data point for the portfolio performance chart.
 */
data class ChartDataPoint(
    val epochDay: Long,
    val portfolioValue: BigDecimal,
    val totalInvested: BigDecimal,
    val roiAbsolute: BigDecimal,
    val roiPercent: BigDecimal,
    val cumulativeCrypto: BigDecimal = BigDecimal.ZERO,
    val investedEquivCrypto: BigDecimal = BigDecimal.ZERO,
    val avgBuyPrice: BigDecimal = BigDecimal.ZERO,
    val price: BigDecimal = BigDecimal.ZERO,
    val epochMillis: Long? = null
)

/**
 * Hierarchical zoom levels for the portfolio chart.
 */
sealed class ChartZoomLevel {
    data object Overview : ChartZoomLevel()
    data class Year(val year: Int) : ChartZoomLevel()
    data class Month(val year: Int, val month: Int) : ChartZoomLevel()
}

/**
 * Use case that computes chart data for portfolio performance.
 * Uses hierarchical zoom levels: Overview (monthly across all history),
 * Year (12 monthly points), Month (daily points).
 * This naturally bounds data points to max ~60 at any zoom level.
 *
 * Memory-optimized:
 * - Uses lightweight price projection (dateEpochDay + price only)
 * - Only builds ChartDataPoint objects at emission boundaries
 * - No temporary object creation on non-emitted days
 */
class CalculateChartDataUseCase @Inject constructor(
    private val dailyPriceDao: DailyPriceDao
) {
    companion object {
        const val MAX_POINTS = 90
    }

    private enum class Granularity { PER_TX, DAILY, WEEKLY, MONTHLY }

    private fun chooseGranularity(
        txInPeriod: Int,
        daysInPeriod: Long,
        zoomLevel: ChartZoomLevel
    ): Granularity {
        // Estimate per-tx point count: each tx day gets N points (one per tx),
        // each non-tx day gets 1 point for daily fill.
        val perTxEstimate = if (txInPeriod <= daysInPeriod) {
            daysInPeriod  // each tx on a separate day → same as daily
        } else {
            txInPeriod + (daysInPeriod - minOf(txInPeriod.toLong(), daysInPeriod))
        }

        if (perTxEstimate <= MAX_POINTS) return Granularity.PER_TX
        if (daysInPeriod <= MAX_POINTS) return Granularity.DAILY

        val weeksInPeriod = (daysInPeriod + 6) / 7
        if (weeksInPeriod <= MAX_POINTS && zoomLevel !is ChartZoomLevel.Month)
            return Granularity.WEEKLY

        return Granularity.MONTHLY
    }

    suspend fun calculate(
        transactions: List<TransactionEntity>,
        crypto: String?,
        fiat: String?,
        zoomLevel: ChartZoomLevel
    ): List<ChartDataPoint> = withContext(Dispatchers.Default) {
        if (transactions.isEmpty()) return@withContext emptyList()

        if (crypto == null && fiat != null) {
            calculateAggregateForFiat(transactions, fiat, zoomLevel)
        } else if (crypto != null && fiat != null) {
            calculateForPair(transactions, crypto, fiat, zoomLevel)
        } else {
            emptyList()
        }
    }

    private suspend fun calculateForPair(
        allTransactions: List<TransactionEntity>,
        crypto: String,
        fiat: String,
        zoomLevel: ChartZoomLevel
    ): List<ChartDataPoint> {
        val pairTxs = allTransactions.filter { it.crypto == crypto && it.fiat == fiat }
        if (pairTxs.isEmpty()) return emptyList()

        val today = LocalDate.now()
        val firstTxDate = pairTxs.first().executedAt
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val (startDate, endDate) = visiblePeriod(zoomLevel, firstTxDate, today)

        // Lightweight projection: only dateEpochDay + price
        val prices = dailyPriceDao.getPriceMapInRange(
            crypto, fiat, startDate.toEpochDay(), endDate.toEpochDay()
        )
        val priceMap = prices.associate { it.dateEpochDay to it.price }

        var cumulativeCrypto = BigDecimal.ZERO
        var cumulativeInvested = BigDecimal.ZERO
        val txIterator = pairTxs.iterator()
        var nextTx: TransactionEntity? = if (txIterator.hasNext()) txIterator.next() else null

        // Pre-accumulate transactions before visible period
        while (nextTx != null) {
            val txDate = nextTx.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            if (!txDate.isBefore(startDate)) break
            cumulativeCrypto += nextTx.cryptoAmount
            cumulativeInvested += nextTx.fiatAmount
            nextTx = if (txIterator.hasNext()) txIterator.next() else null
        }

        // Count transactions in visible period for granularity selection
        val txInPeriod = pairTxs.count { tx ->
            val d = tx.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(startDate) && !d.isAfter(endDate)
        }
        val daysInPeriod = ChronoUnit.DAYS.between(startDate, endDate) + 1
        val granularity = chooseGranularity(txInPeriod, daysInPeriod, zoomLevel)

        val emitWeekly = granularity == Granularity.WEEKLY
        val emitBucketed = granularity == Granularity.WEEKLY || granularity == Granularity.MONTHLY
        val result = mutableListOf<ChartDataPoint>()
        var lastKnownPrice: BigDecimal? = null
        var currentDate = startDate

        // For bucketed emission: track raw values, only build ChartDataPoint at boundaries
        var pendingEpochDay = 0L
        var pendingCrypto = BigDecimal.ZERO
        var pendingInvested = BigDecimal.ZERO
        var pendingPrice: BigDecimal? = null
        var pendingBucketKey: Int? = null  // month ordinal or year*100+week

        val isoWeekField = WeekFields.ISO.weekOfWeekBasedYear()

        while (!currentDate.isAfter(endDate)) {
            val epochDay = currentDate.toEpochDay()

            // Resolve price for today BEFORE processing transactions
            // so per-tx emission can use it.
            // Fallback: use the next transaction's execution price when no daily
            // price data exists yet (e.g. first transaction today, sync not run).
            var price = priceMap[epochDay] ?: lastKnownPrice
            if (price == null) {
                val tx = nextTx
                if (tx != null) {
                    val txDate = tx.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                    if (!txDate.isAfter(currentDate)) {
                        price = tx.price
                    }
                }
            }
            if (price != null) lastKnownPrice = price

            // Process transactions on this day
            var txProcessedToday = false
            while (nextTx != null) {
                val txDate = nextTx.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                if (txDate.isAfter(currentDate)) break
                val txMillis = nextTx.executedAt.toEpochMilli()
                cumulativeCrypto += nextTx.cryptoAmount
                cumulativeInvested += nextTx.fiatAmount
                nextTx = if (txIterator.hasNext()) txIterator.next() else null
                txProcessedToday = true

                // Per-tx mode: emit a point after each individual transaction
                if (granularity == Granularity.PER_TX && price != null) {
                    result.add(buildChartDataPoint(
                        epochDay, cumulativeCrypto, cumulativeInvested, price,
                        epochMillis = txMillis
                    ))
                }
            }

            if (price != null) {
                if (granularity == Granularity.PER_TX) {
                    // Only emit daily fill on days with no transactions
                    if (!txProcessedToday) {
                        result.add(buildChartDataPoint(
                            epochDay, cumulativeCrypto, cumulativeInvested, price
                        ))
                    }
                } else if (emitBucketed) {
                    val bucketKey = if (emitWeekly) {
                        currentDate.get(WeekFields.ISO.weekBasedYear()) * 100 + currentDate.get(isoWeekField)
                    } else {
                        YearMonth.from(currentDate).let { it.year * 100 + it.monthValue }
                    }
                    if (pendingBucketKey != null && bucketKey != pendingBucketKey) {
                        // Bucket boundary crossed – emit the pending snapshot
                        pendingPrice?.let { pp ->
                            result.add(buildChartDataPoint(
                                pendingEpochDay, pendingCrypto, pendingInvested, pp
                            ))
                        }
                    }
                    // Update pending snapshot
                    pendingEpochDay = epochDay
                    pendingCrypto = cumulativeCrypto
                    pendingInvested = cumulativeInvested
                    pendingPrice = price
                    pendingBucketKey = bucketKey
                } else {
                    // Daily emission – build point directly
                    result.add(buildChartDataPoint(
                        epochDay, cumulativeCrypto, cumulativeInvested, price
                    ))
                }
            }

            currentDate = currentDate.plusDays(1)
        }

        // Flush last pending bucketed point
        if (emitBucketed) {
            pendingPrice?.let { pp ->
                result.add(buildChartDataPoint(
                    pendingEpochDay, pendingCrypto, pendingInvested, pp
                ))
            }
        }

        return result
    }

    private suspend fun calculateAggregateForFiat(
        transactions: List<TransactionEntity>,
        fiat: String,
        zoomLevel: ChartZoomLevel
    ): List<ChartDataPoint> {
        val fiatTransactions = transactions.filter { it.fiat == fiat }
        if (fiatTransactions.isEmpty()) return emptyList()

        val pairs = fiatTransactions.map { it.crypto to it.fiat }.distinct()

        val today = LocalDate.now()
        val firstTxDate = fiatTransactions.first().executedAt
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val (startDate, endDate) = visiblePeriod(zoomLevel, firstTxDate, today)

        // Lightweight projection: only dateEpochDay + price per pair
        val txsByPair = fiatTransactions.groupBy { it.crypto to it.fiat }
        val pairPriceMaps = pairs.associate { pair ->
            val (crypto, pairFiat) = pair
            val prices = dailyPriceDao.getPriceMapInRange(
                crypto, pairFiat, startDate.toEpochDay(), endDate.toEpochDay()
            )
            pair to prices.associate { it.dateEpochDay to it.price }
        }

        data class PairState(
            var cumulativeCrypto: BigDecimal = BigDecimal.ZERO,
            var cumulativeInvested: BigDecimal = BigDecimal.ZERO,
            var lastKnownPrice: BigDecimal? = null,
            val txIterator: Iterator<TransactionEntity>,
            var nextTx: TransactionEntity?
        )

        val states = pairs.map { pair ->
            val txs = txsByPair[pair] ?: emptyList()
            val iter = txs.iterator()
            val first = if (iter.hasNext()) iter.next() else null
            pair to PairState(txIterator = iter, nextTx = first)
        }.toMap().toMutableMap()

        // Pre-accumulate transactions before visible period
        for ((_, state) in states) {
            while (state.nextTx != null) {
                val txDate = state.nextTx!!.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                if (!txDate.isBefore(startDate)) break
                state.cumulativeCrypto += state.nextTx!!.cryptoAmount
                state.cumulativeInvested += state.nextTx!!.fiatAmount
                state.nextTx = if (state.txIterator.hasNext()) state.txIterator.next() else null
            }
        }

        // Count transactions in visible period for granularity selection
        val txInPeriod = fiatTransactions.count { tx ->
            val d = tx.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(startDate) && !d.isAfter(endDate)
        }
        val daysInPeriod = ChronoUnit.DAYS.between(startDate, endDate) + 1
        // For aggregate, PER_TX falls through to DAILY (intra-day detail not meaningful with daily prices)
        val rawGranularity = chooseGranularity(txInPeriod, daysInPeriod, zoomLevel)
        val granularity = if (rawGranularity == Granularity.PER_TX) Granularity.DAILY else rawGranularity

        val emitWeekly = granularity == Granularity.WEEKLY
        val emitBucketed = granularity == Granularity.WEEKLY || granularity == Granularity.MONTHLY
        val result = mutableListOf<ChartDataPoint>()
        var currentDate = startDate

        // For bucketed emission: track raw aggregate values
        var pendingEpochDay = 0L
        var pendingValue = BigDecimal.ZERO
        var pendingInvested = BigDecimal.ZERO
        var hasPendingData = false
        var pendingBucketKey: Int? = null

        val isoWeekField = WeekFields.ISO.weekOfWeekBasedYear()

        while (!currentDate.isAfter(endDate)) {
            val epochDay = currentDate.toEpochDay()
            var totalValue = BigDecimal.ZERO
            var totalInvested = BigDecimal.ZERO
            var anyPriceFound = false

            for ((pair, state) in states) {
                var lastTxPriceToday: BigDecimal? = null
                while (state.nextTx != null) {
                    val txDate = state.nextTx!!.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                    if (txDate.isAfter(currentDate)) break
                    state.cumulativeCrypto += state.nextTx!!.cryptoAmount
                    state.cumulativeInvested += state.nextTx!!.fiatAmount
                    lastTxPriceToday = state.nextTx!!.price
                    state.nextTx = if (state.txIterator.hasNext()) state.txIterator.next() else null
                }

                val priceMap = pairPriceMaps[pair]!!
                // Fallback: use transaction execution price when no daily price exists
                val price = priceMap[epochDay] ?: state.lastKnownPrice ?: lastTxPriceToday
                if (price != null) {
                    state.lastKnownPrice = price
                    totalValue += state.cumulativeCrypto * price
                    anyPriceFound = true
                }
                totalInvested += state.cumulativeInvested
            }

            if (anyPriceFound) {
                if (emitBucketed) {
                    val bucketKey = if (emitWeekly) {
                        currentDate.get(WeekFields.ISO.weekBasedYear()) * 100 + currentDate.get(isoWeekField)
                    } else {
                        YearMonth.from(currentDate).let { it.year * 100 + it.monthValue }
                    }
                    if (pendingBucketKey != null && bucketKey != pendingBucketKey && hasPendingData) {
                        // Bucket boundary crossed – emit previous snapshot
                        result.add(buildAggregatePoint(pendingEpochDay, pendingValue, pendingInvested))
                    }
                    pendingEpochDay = epochDay
                    pendingValue = totalValue
                    pendingInvested = totalInvested
                    hasPendingData = true
                    pendingBucketKey = bucketKey
                } else {
                    result.add(buildAggregatePoint(epochDay, totalValue, totalInvested))
                }
            }

            currentDate = currentDate.plusDays(1)
        }

        if (emitBucketed && hasPendingData) {
            result.add(buildAggregatePoint(pendingEpochDay, pendingValue, pendingInvested))
        }

        return result
    }

    private fun buildChartDataPoint(
        epochDay: Long,
        cumulativeCrypto: BigDecimal,
        cumulativeInvested: BigDecimal,
        price: BigDecimal,
        epochMillis: Long? = null
    ): ChartDataPoint {
        val value = cumulativeCrypto * price
        val roiAbsolute = value - cumulativeInvested
        val roiPercent = if (cumulativeInvested > BigDecimal.ZERO) {
            roiAbsolute.divide(cumulativeInvested, 4, RoundingMode.HALF_UP) * BigDecimal(100)
        } else BigDecimal.ZERO

        val investedEquiv = if (price > BigDecimal.ZERO) {
            cumulativeInvested.divide(price, 8, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val avgBuy = if (cumulativeCrypto > BigDecimal.ZERO) {
            cumulativeInvested.divide(cumulativeCrypto, 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return ChartDataPoint(
            epochDay = epochDay,
            portfolioValue = value.setScale(2, RoundingMode.HALF_UP),
            totalInvested = cumulativeInvested.setScale(2, RoundingMode.HALF_UP),
            roiAbsolute = roiAbsolute.setScale(2, RoundingMode.HALF_UP),
            roiPercent = roiPercent.setScale(2, RoundingMode.HALF_UP),
            cumulativeCrypto = cumulativeCrypto,
            investedEquivCrypto = investedEquiv,
            avgBuyPrice = avgBuy,
            price = price,
            epochMillis = epochMillis
        )
    }

    private fun buildAggregatePoint(
        epochDay: Long,
        totalValue: BigDecimal,
        totalInvested: BigDecimal
    ): ChartDataPoint {
        val roiAbsolute = totalValue - totalInvested
        val roiPercent = if (totalInvested > BigDecimal.ZERO) {
            roiAbsolute.divide(totalInvested, 4, RoundingMode.HALF_UP) * BigDecimal(100)
        } else BigDecimal.ZERO

        return ChartDataPoint(
            epochDay = epochDay,
            portfolioValue = totalValue.setScale(2, RoundingMode.HALF_UP),
            totalInvested = totalInvested.setScale(2, RoundingMode.HALF_UP),
            roiAbsolute = roiAbsolute.setScale(2, RoundingMode.HALF_UP),
            roiPercent = roiPercent.setScale(2, RoundingMode.HALF_UP)
        )
    }

    fun visiblePeriod(
        zoomLevel: ChartZoomLevel,
        firstTxDate: LocalDate,
        today: LocalDate
    ): Pair<LocalDate, LocalDate> {
        return when (zoomLevel) {
            is ChartZoomLevel.Overview -> firstTxDate to today
            is ChartZoomLevel.Year -> {
                val yearStart = LocalDate.of(zoomLevel.year, 1, 1)
                val yearEnd = LocalDate.of(zoomLevel.year, 12, 31)
                val start = if (yearStart.isBefore(firstTxDate)) firstTxDate else yearStart
                val end = if (yearEnd.isAfter(today)) today else yearEnd
                start to end
            }
            is ChartZoomLevel.Month -> {
                val ym = YearMonth.of(zoomLevel.year, zoomLevel.month)
                val monthStart = ym.atDay(1)
                val monthEnd = ym.atEndOfMonth()
                val start = if (monthStart.isBefore(firstTxDate)) firstTxDate else monthStart
                val end = if (monthEnd.isAfter(today)) today else monthEnd
                start to end
            }
        }
    }

    fun getAvailableYears(transactions: List<TransactionEntity>): List<Int> {
        return transactions.map {
            it.executedAt.atZone(ZoneId.systemDefault()).toLocalDate().year
        }.distinct().sorted()
    }

    fun getAvailableMonths(transactions: List<TransactionEntity>, year: Int): List<Int> {
        return transactions.mapNotNull {
            val date = it.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            if (date.year == year) date.monthValue else null
        }.distinct().sorted()
    }
}
