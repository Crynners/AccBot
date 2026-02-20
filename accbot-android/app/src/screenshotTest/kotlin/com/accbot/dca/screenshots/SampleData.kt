package com.accbot.dca.screenshots

import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaPlan
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.ChartDataPoint
import com.accbot.dca.domain.usecase.ChartZoomLevel
import com.accbot.dca.presentation.model.MonthlyCostEstimate
import com.accbot.dca.presentation.screens.CryptoHoldingWithPrice
import com.accbot.dca.presentation.screens.DashboardUiState
import com.accbot.dca.presentation.screens.DcaPlanWithBalance
import com.accbot.dca.presentation.screens.exchanges.ExchangeManagementUiState
import com.accbot.dca.presentation.screens.plans.EditPlanUiState
import com.accbot.dca.presentation.screens.portfolio.DenominationMode
import com.accbot.dca.presentation.screens.portfolio.PairPage
import com.accbot.dca.presentation.screens.portfolio.PortfolioUiState
import com.accbot.dca.presentation.screens.plans.PlanDetailsUiState
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object SampleData {

    // Fixed point in time for deterministic screenshots (no diffs on re-runs)
    private val fixedNow = Instant.parse("2026-01-15T12:00:00Z")

    // ── DCA Plans ──

    private val btcPlan = DcaPlan(
        id = 1,
        exchange = Exchange.COINMATE,
        crypto = "BTC",
        fiat = "EUR",
        amount = BigDecimal("50"),
        frequency = DcaFrequency.DAILY,
        isEnabled = true,
        nextExecutionAt = fixedNow.plus(6, ChronoUnit.HOURS)
    )

    private val ethPlan = DcaPlan(
        id = 2,
        exchange = Exchange.BINANCE,
        crypto = "ETH",
        fiat = "EUR",
        amount = BigDecimal("100"),
        frequency = DcaFrequency.WEEKLY,
        strategy = DcaStrategy.AthBased(),
        isEnabled = true,
        nextExecutionAt = fixedNow.plus(3, ChronoUnit.DAYS)
    )

    // ── Holdings ──

    private val btcHolding = CryptoHoldingWithPrice(
        crypto = "BTC",
        fiat = "EUR",
        totalCryptoAmount = BigDecimal("0.15200000"),
        totalInvested = BigDecimal("7850.00"),
        averageBuyPrice = BigDecimal("51644.74"),
        currentPrice = BigDecimal("82644.74"),
        currentValue = BigDecimal("12562.00"),
        roiAbsolute = BigDecimal("4712.00"),
        roiPercent = BigDecimal("60.03"),
        transactionCount = 157
    )

    private val ethHolding = CryptoHoldingWithPrice(
        crypto = "ETH",
        fiat = "EUR",
        totalCryptoAmount = BigDecimal("2.45700000"),
        totalInvested = BigDecimal("4200.00"),
        averageBuyPrice = BigDecimal("1709.40"),
        currentPrice = BigDecimal("2850.85"),
        currentValue = BigDecimal("7002.24"),
        roiAbsolute = BigDecimal("2802.24"),
        roiPercent = BigDecimal("66.72"),
        transactionCount = 42
    )

    // ── Plans with balance ──

    private val btcPlanWithBalance = DcaPlanWithBalance(
        plan = btcPlan,
        fiatBalance = BigDecimal("1500.00"),
        remainingExecutions = 30,
        remainingDays = 30.0,
        isLowBalance = false
    )

    private val ethPlanWithBalance = DcaPlanWithBalance(
        plan = ethPlan,
        fiatBalance = BigDecimal("800.00"),
        remainingExecutions = 8,
        remainingDays = 56.0,
        isLowBalance = false
    )

    // ── Dashboard State ──

    val dashboardUiState = DashboardUiState(
        holdings = listOf(btcHolding, ethHolding),
        activePlans = listOf(btcPlanWithBalance, ethPlanWithBalance),
        isLoading = false,
        isPriceLoading = false,
        isSandboxMode = false
    )

    // ── Transactions (TransactionEntity for HistoryScreen) ──

    private fun daysAgo(days: Long): Instant = fixedNow.minus(days, ChronoUnit.DAYS)

    val transactions = listOf(
        TransactionEntity(
            id = 1, planId = 1, exchange = Exchange.COINMATE,
            crypto = "BTC", fiat = "EUR",
            fiatAmount = BigDecimal("50.00"), cryptoAmount = BigDecimal("0.00060512"),
            price = BigDecimal("82630.50"), fee = BigDecimal("0.25"), feeAsset = "EUR",
            status = TransactionStatus.COMPLETED, executedAt = daysAgo(0)
        ),
        TransactionEntity(
            id = 2, planId = 1, exchange = Exchange.COINMATE,
            crypto = "BTC", fiat = "EUR",
            fiatAmount = BigDecimal("50.00"), cryptoAmount = BigDecimal("0.00061234"),
            price = BigDecimal("81645.20"), fee = BigDecimal("0.25"), feeAsset = "EUR",
            status = TransactionStatus.COMPLETED, executedAt = daysAgo(1)
        ),
        TransactionEntity(
            id = 3, planId = 2, exchange = Exchange.BINANCE,
            crypto = "ETH", fiat = "EUR",
            fiatAmount = BigDecimal("100.00"), cryptoAmount = BigDecimal("0.03508772"),
            price = BigDecimal("2850.85"), fee = BigDecimal("0.10"), feeAsset = "EUR",
            status = TransactionStatus.COMPLETED, executedAt = daysAgo(2)
        ),
        TransactionEntity(
            id = 4, planId = 1, exchange = Exchange.COINMATE,
            crypto = "BTC", fiat = "EUR",
            fiatAmount = BigDecimal("50.00"), cryptoAmount = BigDecimal("0.00059876"),
            price = BigDecimal("83507.12"), fee = BigDecimal("0.25"), feeAsset = "EUR",
            status = TransactionStatus.COMPLETED, executedAt = daysAgo(3)
        ),
        TransactionEntity(
            id = 5, planId = 1, exchange = Exchange.COINMATE,
            crypto = "BTC", fiat = "EUR",
            fiatAmount = BigDecimal("50.00"), cryptoAmount = BigDecimal("0.00062105"),
            price = BigDecimal("80500.00"), fee = BigDecimal("0.25"), feeAsset = "EUR",
            status = TransactionStatus.COMPLETED, executedAt = daysAgo(4)
        ),
        TransactionEntity(
            id = 6, planId = 2, exchange = Exchange.BINANCE,
            crypto = "ETH", fiat = "EUR",
            fiatAmount = BigDecimal("100.00"), cryptoAmount = BigDecimal("0.03636364"),
            price = BigDecimal("2750.00"), fee = BigDecimal("0.10"), feeAsset = "EUR",
            status = TransactionStatus.COMPLETED, executedAt = daysAgo(9)
        )
    )

    // ── Transactions (domain model for PlanDetailsScreen) ──

    val domainTransactions = transactions.map { tx ->
        Transaction(
            id = tx.id, planId = tx.planId, exchange = tx.exchange,
            crypto = tx.crypto, fiat = tx.fiat,
            fiatAmount = tx.fiatAmount, cryptoAmount = tx.cryptoAmount,
            price = tx.price, fee = tx.fee, feeAsset = tx.feeAsset,
            status = tx.status, executedAt = tx.executedAt
        )
    }

    // ── Chart Data (24 points, growing trend) ──

    private val baseEpochDay = LocalDate.of(2025, 12, 23).toEpochDay()

    val chartData = (0..23).map { i ->
        val day = baseEpochDay + i
        val invested = BigDecimal(3000 + i * 250)
        val value = BigDecimal(3000 + i * 250 + (i * i * 15) + (i * 80))
        val roi = value.subtract(invested)
        val roiPct = if (invested > BigDecimal.ZERO) {
            roi.multiply(BigDecimal(100)).divide(invested, 2, java.math.RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        ChartDataPoint(
            epochDay = day,
            portfolioValue = value,
            totalInvested = invested,
            roiAbsolute = roi,
            roiPercent = roiPct,
            cumulativeCrypto = BigDecimal("0.152").add(BigDecimal(i).multiply(BigDecimal("0.0006"))),
            investedEquivCrypto = BigDecimal("0.095").add(BigDecimal(i).multiply(BigDecimal("0.0004"))),
            avgBuyPrice = BigDecimal("51644.74")
        )
    }

    // ── Portfolio State ──

    val portfolioUiState = PortfolioUiState(
        chartData = chartData,
        zoomLevel = ChartZoomLevel.Overview,
        availableYears = listOf(2024, 2025, 2026),
        pages = listOf(
            PairPage.Aggregate("EUR"),
            PairPage.SinglePair("BTC", "EUR"),
            PairPage.SinglePair("ETH", "EUR")
        ),
        selectedPageIndex = 0,
        denominationMode = DenominationMode.FIAT,
        currentPairCrypto = null,
        currentPairFiat = "EUR",
        totalTransactions = 199,
        availableExchanges = listOf("Coinmate", "Binance"),
        isLoading = false,
        isChartLoading = false
    )

    // ── Plan Details State ──

    val planDetailsUiState = PlanDetailsUiState(
        plan = btcPlan,
        transactions = domainTransactions.filter { it.planId == 1L },
        totalInvested = BigDecimal("7850.00"),
        totalCrypto = BigDecimal("0.15200000"),
        averagePrice = BigDecimal("51644.74"),
        transactionCount = 157,
        timeUntilNextExecution = "6h 23m",
        isLoading = false,
        currentPrice = BigDecimal("82644.74"),
        currentValue = BigDecimal("12562.00"),
        roiAbsolute = BigDecimal("4712.00"),
        roiPercent = BigDecimal("60.03"),
        fiatBalance = BigDecimal("1500.00"),
        remainingExecutions = 30,
        remainingDays = 30
    )

    // ── Exchange Management State ──

    val exchangeManagementUiState = ExchangeManagementUiState(
        connectedExchanges = listOf(Exchange.COINMATE, Exchange.BINANCE),
        isLoading = false,
        isSandboxMode = false
    )

    // ── Edit Plan State ──

    val editPlanUiState = EditPlanUiState(
        planId = 1,
        crypto = "BTC",
        fiat = "EUR",
        exchangeName = "Coinmate",
        amount = "50",
        selectedFrequency = DcaFrequency.DAILY,
        selectedStrategy = DcaStrategy.Classic,
        withdrawalEnabled = true,
        withdrawalAddress = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
        isLoading = false,
        isSaving = false,
        monthlyCostEstimate = MonthlyCostEstimate(
            minMonthly = BigDecimal("1500"),
            maxMonthly = BigDecimal("1500")
        ),
        minOrderSize = BigDecimal("10")
    )
}
