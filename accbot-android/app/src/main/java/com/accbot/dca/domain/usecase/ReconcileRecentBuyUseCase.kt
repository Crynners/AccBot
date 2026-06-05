package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.exchange.ExchangeApi
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.Instant

/**
 * Outcome of trying to reconcile a possibly-placed buy against the exchange's trade history.
 *
 * Distinguishes the dangerous "I don't know" case ([Unknown]) from a confirmed absence
 * ([NotFound]) so callers never retry a market order on uncertainty.
 */
sealed interface ReconcileResult {
    data class Found(val trade: HistoricalTrade) : ReconcileResult
    object NotFound : ReconcileResult
    object Unknown : ReconcileResult
}

/**
 * After a buy times out client-side, the order may still have been placed on the exchange.
 * This use case asks the exchange "did a matching buy actually happen since [since]?" so the
 * worker can record it instead of blindly retrying (which would double-spend).
 */
class ReconcileRecentBuyUseCase(
    private val transactionDao: TransactionDao
) {
    suspend operator fun invoke(
        api: ExchangeApi,
        plan: DcaPlanEntity,
        since: Instant,
        expectedFiat: BigDecimal?
    ): ReconcileResult {
        // Dedup against the whole connection, not just this plan: two plans on the same
        // account+pair must not both claim the same order.
        val alreadyRecorded = (
            if (plan.connectionId > 0) transactionDao.getExchangeOrderIdsByConnection(plan.connectionId)
            else transactionDao.getExchangeOrderIdsByPlan(plan.id)
        ).toSet()

        // Allow a small slack before `since`: the exchange stamps fills with its own clock,
        // which can be a few seconds behind the device that captured attemptStart.
        val cutoff = since.minusSeconds(LOOKBACK_BUFFER_SECONDS)

        // The fill may not appear in trade history instantly, so look a few times with a
        // settlement pause (mirrors CoinmateApi.getTradeDetailsByOrderId). A query that
        // throws means we genuinely don't know - return Unknown so the caller stays
        // conservative and never retries on uncertainty.
        repeat(SETTLEMENT_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(SETTLEMENT_DELAY_MS)

            val page = try {
                api.getTradeHistory(
                    crypto = plan.crypto,
                    fiat = plan.fiat,
                    sinceTimestamp = cutoff,
                    limit = PAGE_LIMIT
                )
            } catch (_: Exception) {
                return ReconcileResult.Unknown
            }

            // A single market buy can fill across multiple trade rows, so aggregate by
            // orderId before matching the amount - otherwise each partial looks too small.
            val match = page.trades
                .filter { it.side == "BUY" && it.orderId.isNotEmpty() && it.orderId !in alreadyRecorded }
                .groupBy { it.orderId }
                .map { (orderId, fills) -> aggregateOrder(orderId, fills) }
                .filter { !it.timestamp.isBefore(cutoff) }
                .filter { expectedFiat == null || withinTolerance(it.fiatAmount, expectedFiat) }
                .maxByOrNull { it.timestamp }

            if (match != null) return ReconcileResult.Found(match)
        }

        return ReconcileResult.NotFound
    }

    /** Collapse all fills of one order into a single trade with summed amounts. */
    private fun aggregateOrder(orderId: String, fills: List<HistoricalTrade>): HistoricalTrade {
        val totalCrypto = fills.fold(BigDecimal.ZERO) { acc, f -> acc + f.cryptoAmount }
        val totalFiat = fills.fold(BigDecimal.ZERO) { acc, f -> acc + f.fiatAmount }
        val totalFee = fills.fold(BigDecimal.ZERO) { acc, f -> acc + f.fee }
        val price = if (totalCrypto.signum() > 0) {
            totalFiat.divide(totalCrypto, 2, java.math.RoundingMode.HALF_UP)
        } else {
            fills.first().price
        }
        val ref = fills.first()
        return HistoricalTrade(
            orderId = orderId,
            timestamp = fills.maxOf { it.timestamp },
            crypto = ref.crypto,
            fiat = ref.fiat,
            cryptoAmount = totalCrypto,
            fiatAmount = totalFiat,
            price = price,
            fee = totalFee,
            feeAsset = ref.feeAsset,
            side = "BUY"
        )
    }

    private fun withinTolerance(actual: BigDecimal, expected: BigDecimal): Boolean {
        if (expected.signum() == 0) return true
        val ratio = actual.toDouble() / expected.toDouble()
        return ratio in (1.0 - AMOUNT_TOLERANCE)..(1.0 + AMOUNT_TOLERANCE)
    }

    private companion object {
        const val SETTLEMENT_ATTEMPTS = 3
        const val SETTLEMENT_DELAY_MS = 2_000L
        const val LOOKBACK_BUFFER_SECONDS = 5L
        const val PAGE_LIMIT = 50
        const val AMOUNT_TOLERANCE = 0.30
    }
}
