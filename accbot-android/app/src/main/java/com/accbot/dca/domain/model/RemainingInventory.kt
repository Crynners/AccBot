package com.accbot.dca.domain.model

import java.math.BigDecimal

/**
 * Result of [com.accbot.dca.domain.usecase.CalculatePlanCostBasisUseCase] - timestamp-aware
 * cheapest-first remaining inventory of buys after applying past sells (including PENDING
 * reservations).
 */
data class RemainingInventory(
    /** Sum of remaining crypto across all buys with non-consumed portion. */
    val available: BigDecimal,

    /** Volume-weighted avg buy price of [perBuyDetail]. Null when [available] == 0. */
    val weightedAvgPrice: BigDecimal?,

    /** Per-buy remaining state (only buys with > 0 remaining). For debug / future features. */
    val perBuyDetail: List<RemainingBuy>,

    /** > 0 when historical sells exceed buys (data inconsistency, e.g. CSV import edge case). */
    val deficit: BigDecimal
)

data class RemainingBuy(
    val transactionId: Long,
    val price: BigDecimal,
    val remaining: BigDecimal
)
