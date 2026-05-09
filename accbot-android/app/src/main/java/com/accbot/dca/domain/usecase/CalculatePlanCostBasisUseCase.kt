package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.RemainingBuy
import com.accbot.dca.domain.model.RemainingInventory
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Compute remaining inventory and weighted-average buy price for a plan using the
 * timestamp-aware cheapest-first algorithm.
 *
 * Each historical sell (chronologically ordered) consumes from buys that preceded it,
 * cheapest first; pending/partial sells reserve the unfilled portion. The result is
 * stable against new cheap buys after a sell, because such buys are not in scope for
 * past sells (timestamp filter).
 *
 * Pure logic in [computeCostBasis] for unit testing without DB / Hilt.
 */
class CalculatePlanCostBasisUseCase @Inject constructor(
    private val database: DcaDatabase
) {
    suspend operator fun invoke(planId: Long): RemainingInventory {
        val transactions = database.transactionDao().getTransactionsByPlanSync(planId)
        return computeCostBasis(transactions)
    }

    companion object {
        fun computeCostBasis(transactions: List<TransactionEntity>): RemainingInventory {
            val buys = transactions.filter {
                it.side == TransactionSide.BUY &&
                    (it.status == TransactionStatus.COMPLETED || it.status == TransactionStatus.PARTIAL)
            }

            val sells = transactions.filter {
                it.side == TransactionSide.SELL &&
                    (it.status == TransactionStatus.COMPLETED ||
                        it.status == TransactionStatus.PARTIAL ||
                        it.status == TransactionStatus.PENDING)
            }.sortedBy { it.executedAt }

            val consumed = HashMap<Long, BigDecimal>(buys.size)
            for (b in buys) consumed[b.id] = BigDecimal.ZERO

            var totalDeficit = BigDecimal.ZERO

            for (sell in sells) {
                val toConsume = effectiveConsumption(sell)
                if (toConsume <= BigDecimal.ZERO) continue
                var remaining = toConsume

                val eligible = buys
                    .filter { it.executedAt.isBefore(sell.executedAt) }
                    .filter {
                        (it.cryptoAmount - (consumed[it.id] ?: BigDecimal.ZERO)) > BigDecimal.ZERO
                    }
                    .sortedWith(compareBy({ it.price }, { it.executedAt }))

                for (b in eligible) {
                    if (remaining <= BigDecimal.ZERO) break
                    val available = b.cryptoAmount - (consumed[b.id] ?: BigDecimal.ZERO)
                    val take = remaining.min(available)
                    consumed[b.id] = (consumed[b.id] ?: BigDecimal.ZERO) + take
                    remaining -= take
                }

                if (remaining > BigDecimal.ZERO) totalDeficit = totalDeficit + remaining
            }

            val perBuyDetail = buys.mapNotNull { b ->
                val left = b.cryptoAmount - (consumed[b.id] ?: BigDecimal.ZERO)
                if (left > BigDecimal.ZERO) RemainingBuy(b.id, b.price, left) else null
            }

            val available = perBuyDetail.fold(BigDecimal.ZERO) { acc, rb -> acc + rb.remaining }
            val weightedAvg = if (available > BigDecimal.ZERO) {
                val sumCost = perBuyDetail.fold(BigDecimal.ZERO) { acc, rb ->
                    acc + rb.remaining * rb.price
                }
                sumCost.divide(available, 8, RoundingMode.HALF_UP)
            } else null

            return RemainingInventory(
                available = available,
                weightedAvgPrice = weightedAvg,
                perBuyDetail = perBuyDetail,
                deficit = totalDeficit
            )
        }

        /**
         * Crypto reserved/consumed by a sell. PENDING/PARTIAL: full requested amount (filled
         * + still-reserved unfilled portion). COMPLETED: cryptoAmount (= requested when fully
         * filled). max() guards against rare overflow if filled > requested.
         */
        private fun effectiveConsumption(sell: TransactionEntity): BigDecimal {
            val requested = sell.requestedCryptoAmount ?: BigDecimal.ZERO
            return requested.max(sell.cryptoAmount)
        }
    }
}
