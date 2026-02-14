package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.Exchange
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Data class representing crypto holdings.
 */
data class CryptoHolding(
    val crypto: String,
    val totalAmount: BigDecimal,
    val totalInvested: BigDecimal,
    val averagePrice: BigDecimal,
    val transactionCount: Int
)

/**
 * Data class representing holdings on a specific exchange.
 */
data class ExchangeHolding(
    val exchange: Exchange,
    val holdings: List<CryptoHolding>,
    val totalInvested: BigDecimal
)

/**
 * Data class representing monthly performance.
 */
data class MonthlyPerformance(
    val month: String,           // "Jan 2024"
    val yearMonth: String,       // "2024-01"
    val totalInvested: BigDecimal,
    val totalCrypto: BigDecimal,
    val transactionCount: Int,
    val averagePrice: BigDecimal
)

/**
 * Data class containing all portfolio calculations.
 */
data class PortfolioSummary(
    val cryptoHoldings: List<CryptoHolding>,
    val exchangeHoldings: List<ExchangeHolding>,
    val monthlyPerformance: List<MonthlyPerformance>,
    val totalInvested: BigDecimal,
    val totalBtc: BigDecimal,
    val totalTransactions: Int,
    val averageMonthlyInvestment: BigDecimal
)

/**
 * Use case for portfolio calculations.
 * Extracts business logic from ViewModel for better testability and separation of concerns.
 */
class CalculatePortfolioUseCase @Inject constructor() {

    companion object {
        private val monthDisplayFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
    }

    /**
     * Calculate complete portfolio summary from transactions.
     */
    fun calculatePortfolioSummary(transactions: List<TransactionEntity>): PortfolioSummary {
        val cryptoHoldings = calculateCryptoHoldings(transactions)
        val exchangeHoldings = calculateExchangeHoldings(transactions)
        val monthlyPerformance = calculateMonthlyPerformance(transactions)

        val totalInvested = transactions.sumOf { it.fiatAmount }
        val totalBtc = transactions.filter { it.crypto == "BTC" }.sumOf { it.cryptoAmount }
        val totalTransactions = transactions.size

        val averageMonthly = if (monthlyPerformance.isNotEmpty()) {
            totalInvested.divide(BigDecimal(monthlyPerformance.size), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return PortfolioSummary(
            cryptoHoldings = cryptoHoldings,
            exchangeHoldings = exchangeHoldings,
            monthlyPerformance = monthlyPerformance,
            totalInvested = totalInvested,
            totalBtc = totalBtc,
            totalTransactions = totalTransactions,
            averageMonthlyInvestment = averageMonthly
        )
    }

    /**
     * Calculate holdings grouped by cryptocurrency.
     * Time complexity: O(n) where n = number of transactions.
     */
    fun calculateCryptoHoldings(transactions: List<TransactionEntity>): List<CryptoHolding> {
        return transactions
            .groupBy { it.crypto }
            .map { (crypto, txs) ->
                val totalAmount = txs.sumOf { it.cryptoAmount }
                val totalInvested = txs.sumOf { it.fiatAmount }
                val averagePrice = calculateAveragePrice(totalInvested, totalAmount)

                CryptoHolding(
                    crypto = crypto,
                    totalAmount = totalAmount,
                    totalInvested = totalInvested,
                    averagePrice = averagePrice,
                    transactionCount = txs.size
                )
            }
            .sortedByDescending { it.totalInvested }
    }

    /**
     * Calculate holdings grouped by exchange.
     * Time complexity: O(n) where n = number of transactions.
     */
    fun calculateExchangeHoldings(transactions: List<TransactionEntity>): List<ExchangeHolding> {
        return transactions
            .groupBy { it.exchange }
            .map { (exchange, txs) ->
                val holdings = txs
                    .groupBy { it.crypto }
                    .map { (crypto, cryptoTxs) ->
                        val totalAmount = cryptoTxs.sumOf { it.cryptoAmount }
                        val totalInvested = cryptoTxs.sumOf { it.fiatAmount }
                        val averagePrice = calculateAveragePrice(totalInvested, totalAmount)

                        CryptoHolding(
                            crypto = crypto,
                            totalAmount = totalAmount,
                            totalInvested = totalInvested,
                            averagePrice = averagePrice,
                            transactionCount = cryptoTxs.size
                        )
                    }

                ExchangeHolding(
                    exchange = exchange,
                    holdings = holdings.sortedByDescending { it.totalInvested },
                    totalInvested = txs.sumOf { it.fiatAmount }
                )
            }
            .sortedByDescending { it.totalInvested }
    }

    /**
     * Calculate monthly performance metrics.
     * Time complexity: O(n) where n = number of transactions.
     */
    fun calculateMonthlyPerformance(transactions: List<TransactionEntity>): List<MonthlyPerformance> {
        return transactions
            .groupBy { tx ->
                val localDate = tx.executedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                YearMonth.from(localDate)
            }
            .map { (yearMonth, txs) ->
                val totalInvested = txs.sumOf { it.fiatAmount }
                val totalCrypto = txs.filter { it.crypto == "BTC" }.sumOf { it.cryptoAmount }
                val averagePrice = calculateAveragePrice(totalInvested, totalCrypto)

                MonthlyPerformance(
                    month = yearMonth.format(monthDisplayFormatter),
                    yearMonth = yearMonth.toString(),
                    totalInvested = totalInvested,
                    totalCrypto = totalCrypto,
                    transactionCount = txs.size,
                    averagePrice = averagePrice
                )
            }
            .sortedByDescending { it.yearMonth }
    }

    /**
     * Calculate average price with safe division.
     */
    private fun calculateAveragePrice(totalFiat: BigDecimal, totalCrypto: BigDecimal): BigDecimal {
        return if (totalCrypto > BigDecimal.ZERO) {
            totalFiat.divide(totalCrypto, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }
}
