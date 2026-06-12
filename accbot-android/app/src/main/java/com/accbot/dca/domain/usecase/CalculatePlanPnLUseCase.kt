package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.domain.model.PlanPnL
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Compute [PlanPnL] from the plan's COMPLETED/PARTIAL transactions.
 *
 * Only counts filled amounts (`cryptoAmount` / `fiatAmount`) on SELLs - open (PENDING)
 * SELLs do not affect realized PnL; they only reduce the effective free crypto in
 * [ValidateSellOrderUseCase].
 */
class CalculatePlanPnLUseCase @Inject constructor(
    private val database: DcaDatabase
) {
    suspend operator fun invoke(
        planId: Long,
        currentMarketPrice: BigDecimal?
    ): PlanPnL {
        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: error("Plan $planId neexistuje")

        val transactions = database.transactionDao().getTransactionsByPlanSync(planId)

        val relevant = transactions.filter {
            it.status == TransactionStatus.COMPLETED || it.status == TransactionStatus.PARTIAL
        }

        val buys = relevant.filter { it.side == TransactionSide.BUY }
        val sells = relevant.filter { it.side == TransactionSide.SELL }

        val totalBoughtFiat = buys.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.fiatAmount }
        val totalBoughtCrypto = buys.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.cryptoAmount }
        val totalSoldFiat = sells.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.fiatAmount }
        val totalSoldCrypto = sells.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.cryptoAmount }
        val currentCryptoHeld = totalBoughtCrypto - totalSoldCrypto

        val avgBuyPrice = if (totalBoughtCrypto > BigDecimal.ZERO) {
            totalBoughtFiat.divide(totalBoughtCrypto, 8, RoundingMode.HALF_UP)
        } else null

        val currentValueFiat = currentMarketPrice?.let { currentCryptoHeld * it }
        val realizedPnL = avgBuyPrice?.let { totalSoldFiat - (totalSoldCrypto * it) }
        val unrealizedPnL = if (avgBuyPrice != null && currentValueFiat != null) {
            currentValueFiat - (currentCryptoHeld * avgBuyPrice)
        } else null
        val netPnL = if (realizedPnL != null && unrealizedPnL != null) {
            realizedPnL + unrealizedPnL
        } else null

        val target = plan.targetProfitAmount
        val targetProgressPct = if (netPnL != null && target != null && target > BigDecimal.ZERO) {
            netPnL.toDouble() / target.toDouble()
        } else null

        return PlanPnL(
            totalBoughtFiat = totalBoughtFiat,
            totalBoughtCrypto = totalBoughtCrypto,
            totalSoldFiat = totalSoldFiat,
            totalSoldCrypto = totalSoldCrypto,
            currentCryptoHeld = currentCryptoHeld,
            avgBuyPrice = avgBuyPrice,
            currentValueFiat = currentValueFiat,
            realizedPnL = realizedPnL,
            unrealizedPnL = unrealizedPnL,
            netPnL = netPnL,
            targetProgressPct = targetProgressPct
        )
    }
}
