package com.accbot.dca.data.local

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.model.WithdrawalStatus
import java.math.BigDecimal
import java.time.Instant

/**
 * Notification type for in-app notification history
 */
enum class NotificationType { PURCHASE, ERROR, LOW_BALANCE, WITHDRAWAL_THRESHOLD, NETWORK_RETRY, MISSED_PURCHASES }

/**
 * Room type converters
 */
class Converters {
    private companion object {
        const val TAG = "Converters"
    }

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromExchange(value: Exchange): String = value.name

    @TypeConverter
    fun toExchange(value: String): Exchange = try {
        Exchange.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unknown Exchange '$value', falling back to COINMATE")
        Exchange.COINMATE
    }

    @TypeConverter
    fun fromDcaFrequency(value: DcaFrequency): String = value.name

    @TypeConverter
    fun toDcaFrequency(value: String): DcaFrequency = try {
        DcaFrequency.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unknown DcaFrequency '$value', falling back to DAILY")
        DcaFrequency.DAILY
    }

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus = try {
        TransactionStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unknown TransactionStatus '$value', falling back to FAILED")
        TransactionStatus.FAILED
    }

    @TypeConverter
    fun fromWithdrawalStatus(value: WithdrawalStatus): String = value.name

    @TypeConverter
    fun toWithdrawalStatus(value: String): WithdrawalStatus = try {
        WithdrawalStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unknown WithdrawalStatus '$value', falling back to FAILED")
        WithdrawalStatus.FAILED
    }

    @TypeConverter
    fun fromDcaStrategy(value: DcaStrategy): String = DcaStrategy.toDbString(value)

    @TypeConverter
    fun toDcaStrategy(value: String): DcaStrategy = DcaStrategy.fromString(value)

    @TypeConverter
    fun fromNotificationType(value: NotificationType): String = value.name

    @TypeConverter
    fun toNotificationType(value: String): NotificationType = try {
        NotificationType.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unknown NotificationType '$value', falling back to ERROR")
        NotificationType.ERROR
    }

    @TypeConverter
    fun fromTransactionSide(value: TransactionSide): String = value.name

    @TypeConverter
    fun toTransactionSide(value: String): TransactionSide = try {
        TransactionSide.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unknown TransactionSide '$value', falling back to BUY")
        TransactionSide.BUY
    }
}

/**
 * Exchange connection entity - represents one set of API credentials for one exchange.
 * Multiple connections can target the same exchange enum (e.g. two Coinmate sub-accounts
 * as "Hlavní" and "Spoření" envelopes). The actual API key/secret is stored separately
 * in [CredentialsStore], keyed by this entity's [id].
 *
 * Note: there is no `isSandbox` column because production and sandbox are stored in
 * separate Room database files (see [DcaDatabase]); a connection is implicitly tied to
 * the environment of the database it lives in.
 *
 * The unique index on `(exchange, name)` enforces:
 *  - At most ONE connection with the empty default name "" per exchange (the auto-created
 *    "Default" envelope after migration or first credentials save).
 *  - At most ONE connection with any given non-empty name per exchange (no duplicate
 *    "Spoření" connections on Coinmate).
 *
 * UI rule (enforced in [CredentialFormDelegate]): when adding a 2nd connection on the same
 * exchange, a non-empty name is required so both envelopes are distinguishable.
 */
@Entity(
    tableName = "exchange_connections",
    indices = [
        Index(value = ["exchange"]),
        Index(value = ["exchange", "name"], unique = true)
    ]
)
@TypeConverters(Converters::class)
data class ExchangeConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exchange: Exchange,
    /** Empty string means "no custom name" - UI displays the exchange display name only. */
    val name: String = "",
    val createdAt: Instant = Instant.now(),
    val displayOrder: Int = 0
)

/**
 * DCA Plan entity - stored in Room database
 */
@Entity(
    tableName = "dca_plans",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["exchange"]),
        Index(value = ["connectionId"]),
        Index(value = ["nextExecutionAt"]),
        Index(value = ["isEnabled", "nextExecutionAt"])
    ]
)
@TypeConverters(Converters::class)
data class DcaPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exchange: Exchange,
    /**
     * FK to [ExchangeConnectionEntity.id]. Set by migration for legacy plans, required
     * for new plans. Resolved via [ExchangeConnectionDao] at plan creation time.
     */
    val connectionId: Long = 0,
    /** Optional custom label. Empty string = no label (UI shows "BTC/EUR" as default). */
    val name: String = "",
    val crypto: String,
    val fiat: String,
    val amount: BigDecimal,
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
    val networkRetryCount: Int = 0,
    val nextNetworkRetryAt: Instant? = null,
    val originalScheduledAt: Instant? = null,
    val missedPurchaseCount: Int = 0,
    /** Order for Dashboard display. Lower values shown first. */
    val displayOrder: Int = 0,
    /**
     * Opt-in per-plan toggle for sell extension. When true (and global trading is enabled),
     * plan-detail shows P&L card, open sell orders list, and sell wizard button.
     */
    @ColumnInfo(defaultValue = "0")
    val allowSells: Boolean = false,
    /**
     * Optional profit goal (in [fiat]). When set, plan-detail shows progress bar toward this.
     * Null when user didn't specify a target.
     */
    val targetProfitAmount: BigDecimal? = null
)

/**
 * Transaction entity
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["planId"]),
        Index(value = ["exchange"]),
        Index(value = ["connectionId"]),
        Index(value = ["crypto"]),
        Index(value = ["status"]),
        Index(value = ["executedAt"]),
        Index(value = ["planId", "status"]),
        Index(value = ["crypto", "fiat", "status"]),
        Index(value = ["fiat", "status"]),
        Index(value = ["planId", "side", "status"])
    ]
)
@TypeConverters(Converters::class)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,
    val exchange: Exchange,
    /**
     * FK-like reference to [ExchangeConnectionEntity.id]. Nullable so historical
     * transactions survive deletion of their parent connection (no FK constraint).
     * UI falls back to [exchange] display name when this is null or the connection
     * was deleted.
     */
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
    val executedAt: Instant = Instant.now(),
    /**
     * BUY for DCA purchases (default), SELL for limit sell orders placed via sell extension.
     */
    @ColumnInfo(defaultValue = "BUY")
    val side: TransactionSide = TransactionSide.BUY,
    /**
     * Requested limit price for SELL orders; null for market BUYs.
     */
    val limitPrice: BigDecimal? = null,
    /**
     * Original requested crypto amount for SELL orders (fixed across lifecycle).
     * [cryptoAmount] tracks filled amount (progresses 0 -> requested). Null for BUYs.
     */
    val requestedCryptoAmount: BigDecimal? = null
)

/**
 * Withdrawal entity
 */
@Entity(
    tableName = "withdrawals",
    indices = [
        Index(value = ["planId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
@TypeConverters(Converters::class)
data class WithdrawalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,
    val exchange: Exchange,
    /** Nullable reference to [ExchangeConnectionEntity.id], no FK constraint. */
    val connectionId: Long? = null,
    val crypto: String,
    val amount: BigDecimal,
    val address: String,
    val txHash: String? = null,
    val fee: BigDecimal,
    val status: WithdrawalStatus,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now()
)

/**
 * Exchange balance cache entity.
 * Stores cached balances from exchanges for quick display.
 *
 * PK is composite `(connectionId, currency)` so that two connections targeting the same
 * exchange (e.g. two Coinmate sub-accounts) keep separate balance caches. The [exchange]
 * field is retained as a redundant fallback for display when the parent connection is
 * deleted.
 */
@Entity(
    tableName = "exchange_balances",
    primaryKeys = ["connectionId", "currency"],
    indices = [
        Index(value = ["connectionId"]),
        Index(value = ["exchange"])
    ]
)
@TypeConverters(Converters::class)
data class ExchangeBalanceEntity(
    val connectionId: Long,
    val currency: String,
    val exchange: Exchange,
    val balance: BigDecimal,
    val lastUpdated: Instant = Instant.now()
)

/**
 * Monthly summary for performance tracking
 */
@Entity(tableName = "monthly_summaries")
@TypeConverters(Converters::class)
data class MonthlySummaryEntity(
    @PrimaryKey
    val id: String,  // "YYYY-MM"
    val year: Int,
    val month: Int,
    val totalInvestedEur: BigDecimal,
    val totalBtcAccumulated: BigDecimal,
    val transactionCount: Int,
    val averageBtcPrice: BigDecimal,
    val lastUpdated: Instant = Instant.now()
)

/**
 * Daily price cache for portfolio performance charts.
 * Stores historical daily prices from CoinGecko to avoid re-fetching.
 */
@Entity(
    tableName = "daily_prices",
    primaryKeys = ["crypto", "fiat", "dateEpochDay"],
    indices = [Index(value = ["crypto", "fiat"])]
)
@TypeConverters(Converters::class)
data class DailyPriceEntity(
    val crypto: String,
    val fiat: String,
    val dateEpochDay: Long,       // LocalDate.toEpochDay()
    val price: BigDecimal,
    val fetchedAt: Instant = Instant.now()
)

/**
 * In-app notification history entity
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["isRead"]),
        Index(value = ["isArchived"]),
        Index(value = ["createdAt"])
    ]
)
@TypeConverters(Converters::class)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: NotificationType,
    val title: String,
    val message: String,
    val planId: Long? = null,
    val crypto: String? = null,
    val exchange: Exchange? = null,
    /** Optional reference to [ExchangeConnectionEntity.id], no FK constraint. */
    val connectionId: Long? = null,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val systemNotificationId: Int? = null,
    val templateArgs: String? = null,
    val createdAt: Instant = Instant.now()
)

/**
 * Withdrawal threshold configuration per crypto+connection pair.
 *
 * After v18→v19 migration the PK changed from `(crypto, exchange)` to
 * `(crypto, connectionId)` so each connection (envelope) has its own withdrawal target.
 *
 * No DB-level FOREIGN KEY: cascade-on-connection-delete is handled explicitly by
 * [ExchangeConnectionRepository.delete] via [WithdrawalThresholdDao.deleteByConnection],
 * because FK enforcement (`PRAGMA foreign_keys`) is currently disabled in the Room
 * database builder.
 */
@Entity(
    tableName = "withdrawal_thresholds",
    primaryKeys = ["crypto", "connectionId"],
    indices = [
        Index(value = ["connectionId"])
    ]
)
@TypeConverters(Converters::class)
data class WithdrawalThresholdEntity(
    val crypto: String,
    val connectionId: Long,
    val thresholdAmount: BigDecimal
)
