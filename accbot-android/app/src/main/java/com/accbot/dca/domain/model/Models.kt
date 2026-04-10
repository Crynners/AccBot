package com.accbot.dca.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.accbot.dca.R
import java.math.BigDecimal
import java.time.Instant

/**
 * Sandbox support levels for exchanges
 */
enum class SandboxSupport {
    FULL,           // Full sandbox/testnet available (Binance, KuCoin, Coinbase)
    PAPER_TRADING,  // Paper trading mode (Bitfinex)
    FUTURES_ONLY,   // Only futures demo available (Kraken)
    NONE            // No sandbox available (Coinmate, Huobi)
}

/**
 * Supported cryptocurrency exchanges
 */
enum class Exchange(
    val displayName: String,
    @DrawableRes val logoRes: Int,
    val supportedFiats: List<String>,
    val supportedCryptos: List<String>,
    val minOrderSize: Map<String, BigDecimal>,
    val sandboxSupport: SandboxSupport
) {
    COINMATE(
        displayName = "Coinmate",
        logoRes = R.drawable.ic_exchange_coinmate,
        supportedFiats = listOf("EUR", "CZK"),
        supportedCryptos = listOf("BTC", "ETH", "LTC"),
        minOrderSize = mapOf("EUR" to BigDecimal("2"), "CZK" to BigDecimal("50")),
        sandboxSupport = SandboxSupport.NONE
    ),
    BINANCE(
        displayName = "Binance",
        logoRes = R.drawable.ic_exchange_binance,
        supportedFiats = listOf("EUR", "USDC"),
        supportedCryptos = listOf("BTC", "ETH", "BNB", "SOL", "ADA", "DOT"),
        minOrderSize = mapOf("EUR" to BigDecimal("5"), "USDC" to BigDecimal("5")),
        sandboxSupport = SandboxSupport.FULL
    ),
    KRAKEN(
        displayName = "Kraken",
        logoRes = R.drawable.ic_exchange_kraken,
        supportedFiats = listOf("EUR", "USD", "GBP"),
        supportedCryptos = listOf("BTC", "ETH", "SOL", "DOT"),
        minOrderSize = mapOf("EUR" to BigDecimal("10"), "USD" to BigDecimal("10")),
        sandboxSupport = SandboxSupport.FUTURES_ONLY
    ),
    KUCOIN(
        displayName = "KuCoin",
        logoRes = R.drawable.ic_exchange_kucoin,
        supportedFiats = listOf("USDT"),
        supportedCryptos = listOf("BTC", "ETH", "SOL", "ADA"),
        minOrderSize = mapOf("USDT" to BigDecimal("10")),
        sandboxSupport = SandboxSupport.FULL
    ),
    BITFINEX(
        displayName = "Bitfinex",
        logoRes = R.drawable.ic_exchange_bitfinex,
        supportedFiats = listOf("USD", "EUR"),
        supportedCryptos = listOf("BTC", "ETH"),
        minOrderSize = mapOf("USD" to BigDecimal("25"), "EUR" to BigDecimal("25")),
        sandboxSupport = SandboxSupport.PAPER_TRADING
    ),
    HUOBI(
        displayName = "Huobi",
        logoRes = R.drawable.ic_exchange_huobi,
        supportedFiats = listOf("USDT"),
        supportedCryptos = listOf("BTC", "ETH", "SOL"),
        minOrderSize = mapOf("USDT" to BigDecimal("10")),
        sandboxSupport = SandboxSupport.NONE
    ),
    COINBASE(
        displayName = "Coinbase",
        logoRes = R.drawable.ic_exchange_coinbase,
        supportedFiats = listOf("EUR", "USD", "GBP"),
        supportedCryptos = listOf("BTC", "ETH", "SOL", "ADA"),
        minOrderSize = mapOf("EUR" to BigDecimal("1"), "USD" to BigDecimal("1")),
        sandboxSupport = SandboxSupport.FULL
    );

    companion object {
        /** Binance LOT_SIZE step sizes per crypto (from /api/v3/exchangeInfo). */
        val binanceLotStepSize = mapOf(
            "BTC" to "0.00001",
            "ETH" to "0.0001",
            "BNB" to "0.001",
            "SOL" to "0.001",
            "ADA" to "0.1",
            "DOT" to "0.01"
        )
    }
}

/**
 * Check if exchange supports full sandbox mode
 */
fun Exchange.supportsSandbox(): Boolean = sandboxSupport == SandboxSupport.FULL

/**
 * Check if exchange supports API-based transaction history import
 */
val Exchange.supportsApiImport: Boolean get() = this in setOf(Exchange.COINMATE, Exchange.BINANCE, Exchange.KRAKEN, Exchange.COINBASE)

/**
 * Whether this exchange has been fully tested and is considered stable.
 */
val Exchange.isStable: Boolean get() = this in setOf(Exchange.COINMATE, Exchange.BINANCE)

/**
 * Get list of available exchanges based on sandbox mode.
 * In sandbox mode, only exchanges with full sandbox support are returned.
 * This is a cached list to avoid recreating on each call.
 */
object ExchangeFilter {
    private val sandboxExchanges: List<Exchange> by lazy {
        Exchange.entries.filter { it.supportsSandbox() }
    }

    private val allExchanges: List<Exchange> by lazy {
        Exchange.entries.toList()
    }

    fun getAvailableExchanges(isSandboxMode: Boolean): List<Exchange> =
        if (isSandboxMode) sandboxExchanges else allExchanges
}

/**
 * DCA purchase frequency options
 */
enum class DcaFrequency(
    @StringRes val displayNameRes: Int,
    val intervalMinutes: Long
) {
    EVERY_15_MIN(R.string.frequency_every_15_min, 15),
    HOURLY(R.string.frequency_hourly, 60),
    EVERY_4_HOURS(R.string.frequency_every_4_hours, 240),
    EVERY_8_HOURS(R.string.frequency_every_8_hours, 480),
    DAILY(R.string.frequency_daily, 1440),
    WEEKLY(R.string.frequency_weekly, 10080),
    CUSTOM(R.string.frequency_custom, 1440)
}

/**
 * DCA Plan configuration - stored locally only
 */
data class DcaPlan(
    val id: Long = 0,
    val exchange: Exchange,
    /** FK to ExchangeConnectionEntity.id - every plan belongs to one connection. */
    val connectionId: Long,
    /** Optional custom label. Empty = UI shows "BTC/EUR" as default title. */
    val name: String = "",
    val crypto: String,
    val fiat: String,
    val amount: BigDecimal,           // Base amount (strategy may modify)
    val frequency: DcaFrequency,
    val cronExpression: String? = null,
    val strategy: DcaStrategy = DcaStrategy.Classic,
    val isEnabled: Boolean = true,
    val withdrawalEnabled: Boolean = false,
    val withdrawalAddress: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastExecutedAt: Instant? = null,
    val nextExecutionAt: Instant? = null,
    val targetAmount: BigDecimal? = null,
    /** Order for Dashboard display. Lower values shown first. */
    val displayOrder: Int = 0
)

/**
 * API credentials - encrypted and stored locally only
 */
data class ExchangeCredentials(
    val exchange: Exchange,
    val apiKey: String,
    val apiSecret: String,
    val passphrase: String? = null, // Some exchanges require this (KuCoin, Coinbase)
    val clientId: String? = null    // Coinmate requires separate Client ID
)

/**
 * Purchase transaction record
 */
data class Transaction(
    val id: Long = 0,
    val planId: Long,
    val exchange: Exchange,
    /** Optional connection reference; null for legacy or post-deletion. */
    val connectionId: Long? = null,
    val crypto: String,
    val fiat: String,
    val fiatAmount: BigDecimal,
    val cryptoAmount: BigDecimal,
    val price: BigDecimal,
    val fee: BigDecimal,
    val feeAsset: String = "",
    val status: TransactionStatus,
    val exchangeOrderId: String? = null,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val executedAt: Instant = Instant.now()
)

enum class TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    PARTIAL
}

/**
 * Withdrawal record
 */
data class Withdrawal(
    val id: Long = 0,
    val planId: Long,
    val exchange: Exchange,
    val crypto: String,
    val amount: BigDecimal,
    val address: String,
    val txHash: String? = null,
    val fee: BigDecimal,
    val status: WithdrawalStatus,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now()
)

enum class WithdrawalStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

/**
 * Portfolio statistics - calculated locally
 */
data class PortfolioStats(
    val totalInvestedFiat: Map<String, BigDecimal>,
    val totalCryptoHoldings: Map<String, BigDecimal>,
    val totalTransactions: Int,
    val averageBuyPrice: Map<String, BigDecimal>,
    val lastUpdated: Instant
)

/**
 * DCA execution result
 */
sealed class DcaResult {
    data class Success(
        val transaction: Transaction
    ) : DcaResult()

    data class Error(
        val message: String,
        val retryable: Boolean = true
    ) : DcaResult()
}

/**
 * In-app notification for display in the Notifications tab
 */
data class AppNotification(
    val id: Long,
    val type: com.accbot.dca.data.local.NotificationType,
    val title: String,
    val message: String,
    val planId: Long? = null,
    val crypto: String? = null,
    val exchange: Exchange? = null,
    val connectionId: Long? = null,
    val isRead: Boolean,
    val isArchived: Boolean = false,
    val createdAt: Instant
)

/**
 * Withdrawal threshold configuration - per (crypto, connection) pair.
 *
 * `exchange` is denormalized from the parent connection so UI can group/display by exchange
 * without joining; it is filled in at the ViewModel layer when loading thresholds.
 * `connectionName` may be empty if the connection has no custom name.
 */
data class WithdrawalThreshold(
    val crypto: String,
    val connectionId: Long,
    val exchange: Exchange,
    val connectionName: String,
    val thresholdAmount: BigDecimal
)

/**
 * A single historical trade from an exchange API
 */
data class HistoricalTrade(
    val orderId: String,
    val timestamp: Instant,
    val crypto: String,
    val fiat: String,
    val cryptoAmount: BigDecimal,
    val fiatAmount: BigDecimal,
    val price: BigDecimal,
    val fee: BigDecimal,
    val feeAsset: String,
    val side: String  // "BUY" or "SELL"
)

/**
 * A page of trade history results
 */
data class TradeHistoryPage(
    val trades: List<HistoricalTrade>,
    val hasMore: Boolean
)
