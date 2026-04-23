package com.accbot.dca.exchange

import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal

/**
 * Result of an order status query.
 *
 * Used by the ResolvePendingTransactionsUseCase to update PENDING/PARTIAL transactions
 * with fill information from the exchange.
 *
 * @property status Current order status mapped to our TransactionStatus enum.
 * @property filledCryptoAmount Crypto amount filled so far (0 if not yet filled).
 * @property filledFiatAmount Fiat amount of fills so far (0 if not yet filled).
 * @property avgFillPrice Volume-weighted average fill price, or null if not yet filled.
 * @property fee Total fee accrued by the order so far, or null if the exchange doesn't
 *           report fees on the order object (e.g. Binance - fees are per-trade).
 * @property feeAsset Asset in which the fee is denominated, or null if [fee] is null.
 */
data class OrderStatusResult(
    val status: TransactionStatus,
    val filledCryptoAmount: BigDecimal,
    val filledFiatAmount: BigDecimal,
    val avgFillPrice: BigDecimal?,
    val fee: BigDecimal?,
    val feeAsset: String?
)
