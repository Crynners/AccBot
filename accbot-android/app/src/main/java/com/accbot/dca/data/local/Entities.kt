package com.accbot.dca.data.local

import android.util.Log
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.model.WithdrawalStatus
import java.math.BigDecimal
import java.time.Instant

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
    fun toBigDecimal(value: String?): BigDecimal? = value?.let {
        try {
            BigDecimal(it)
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Invalid BigDecimal value '$it', falling back to ZERO", e)
            BigDecimal.ZERO
        }
    }

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let {
        try {
            Instant.ofEpochMilli(it)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid Instant millis '$it', falling back to now()", e)
            Instant.now()
        }
    }

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
}

/**
 * DCA Plan entity - stored in Room database
 */
@Entity(
    tableName = "dca_plans",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["exchange"]),
        Index(value = ["nextExecutionAt"]),
        Index(value = ["isEnabled", "nextExecutionAt"])
    ]
)
@TypeConverters(Converters::class)
data class DcaPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exchange: Exchange,
    val crypto: String,
    val fiat: String,
    val amount: BigDecimal,
    val frequency: DcaFrequency,
    val strategy: DcaStrategy = DcaStrategy.Classic,
    val isEnabled: Boolean = true,
    val withdrawalEnabled: Boolean = false,
    val withdrawalAddress: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastExecutedAt: Instant? = null,
    val nextExecutionAt: Instant? = null
)

/**
 * Transaction entity
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["planId"]),
        Index(value = ["exchange"]),
        Index(value = ["crypto"]),
        Index(value = ["status"]),
        Index(value = ["executedAt"]),
        Index(value = ["planId", "status"]),
        Index(value = ["crypto", "fiat", "status"])
    ]
)
@TypeConverters(Converters::class)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,
    val exchange: Exchange,
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
    val executedAt: Instant = Instant.now()
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
 * Exchange balance cache entity
 * Stores cached balances from exchanges for quick display
 */
@Entity(
    tableName = "exchange_balances",
    indices = [
        Index(value = ["exchange"])
    ]
)
@TypeConverters(Converters::class)
data class ExchangeBalanceEntity(
    @PrimaryKey
    val id: String,  // "${exchange}_${currency}"
    val exchange: Exchange,
    val currency: String,
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
