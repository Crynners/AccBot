package com.accbot.dca.domain.share

import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import java.math.BigDecimal
import java.time.Instant

private val fixtureT0 = Instant.parse("2026-01-01T09:00:00Z")

fun sharedPlanFixture(
    shareId: String = "s1",
    includesAmounts: Boolean = true,
    isSandbox: Boolean = false,
    fiat: String = "CZK",
    exchange: Exchange? = Exchange.KRAKEN,
    trades: List<SharedPlanTrade> = listOf(
        SharedPlanTrade(fixtureT0, TransactionSide.BUY, BigDecimal("1000000"), BigDecimal("0.0005"), BigDecimal("500"), BigDecimal("1.5"), "CZK"),
        SharedPlanTrade(fixtureT0.plusSeconds(86400), TransactionSide.SELL, BigDecimal("1100000"), BigDecimal("0.0002"), BigDecimal("220"), null, null)
    )
) = SharedPlan(
    shareId = shareId, sharedAt = fixtureT0, isSandbox = isSandbox, includesAmounts = includesAmounts,
    name = "Friend", exchange = exchange, exchangeName = "x", crypto = "BTC", fiat = fiat,
    amount = null, frequency = null, cronExpression = null, strategy = DcaStrategy.Classic,
    isEnabled = true, createdAt = fixtureT0, startedAt = fixtureT0, statedAvgBuyPrice = null, trades = trades
)
