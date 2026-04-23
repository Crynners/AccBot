package com.accbot.dca.domain.model

import java.math.BigDecimal

/**
 * Profit & loss snapshot for a single DCA plan.
 *
 * Derived fields (the ones ending in `?`) are null when the inputs to compute them
 * aren't available - e.g. `currentValueFiat` is null when the caller didn't provide a
 * live market price, `avgBuyPrice` is null when the plan has no completed BUYs yet.
 *
 * @property totalBoughtFiat Sum of fiat spent on COMPLETED/PARTIAL BUYs.
 * @property totalBoughtCrypto Sum of crypto filled on COMPLETED/PARTIAL BUYs.
 * @property totalSoldFiat Sum of fiat received from COMPLETED/PARTIAL SELLs (filled).
 * @property totalSoldCrypto Sum of crypto delivered on COMPLETED/PARTIAL SELLs (filled).
 * @property currentCryptoHeld totalBoughtCrypto - totalSoldCrypto.
 * @property avgBuyPrice Volume-weighted avg buy price (fiat per crypto), or null if no buys.
 * @property currentValueFiat currentCryptoHeld * currentMarketPrice, or null if no spot given.
 * @property realizedPnL totalSoldFiat - (totalSoldCrypto * avgBuyPrice), or null.
 * @property unrealizedPnL currentValueFiat - (currentCryptoHeld * avgBuyPrice), or null.
 * @property netPnL realizedPnL + unrealizedPnL, or null.
 * @property targetProgressPct netPnL / plan.targetProfitAmount as 0..1+ ratio, or null.
 */
data class PlanPnL(
    val totalBoughtFiat: BigDecimal,
    val totalBoughtCrypto: BigDecimal,
    val totalSoldFiat: BigDecimal,
    val totalSoldCrypto: BigDecimal,
    val currentCryptoHeld: BigDecimal,
    val avgBuyPrice: BigDecimal?,
    val currentValueFiat: BigDecimal?,
    val realizedPnL: BigDecimal?,
    val unrealizedPnL: BigDecimal?,
    val netPnL: BigDecimal?,
    val targetProgressPct: Double?
)
