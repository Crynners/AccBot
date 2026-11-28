package com.accbot.dca.domain.share

import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal

/**
 * Maps a shared plan's trades to in-memory [TransactionEntity] objects so the portfolio chart
 * use case can draw them. The result is for display only and must never be written to Room.
 *
 * Empty when the share hides amounts. Trades missing an amount are skipped. The exchange is
 * only a placeholder for the required field; the chart does not use it.
 */
fun SharedPlan.toChartTransactions(): List<TransactionEntity> {
    if (!includesAmounts) return emptyList()
    return trades.mapNotNull { trade ->
        val cryptoAmount = trade.cryptoAmount ?: return@mapNotNull null
        val fiatAmount = trade.fiatAmount ?: return@mapNotNull null
        TransactionEntity(
            planId = 0,
            exchange = exchange ?: Exchange.COINMATE,
            connectionId = null,
            crypto = crypto,
            fiat = fiat,
            fiatAmount = fiatAmount,
            cryptoAmount = cryptoAmount,
            price = trade.price,
            fee = trade.fee ?: BigDecimal.ZERO,
            feeAsset = trade.feeAsset.orEmpty(),
            status = TransactionStatus.COMPLETED,
            executedAt = trade.executedAt,
            side = trade.side
        )
    }
}
