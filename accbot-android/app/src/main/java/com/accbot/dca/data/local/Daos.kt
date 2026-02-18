package com.accbot.dca.data.local

import androidx.room.*
import com.accbot.dca.domain.model.Exchange
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

@Dao
interface DcaPlanDao {
    @Query("SELECT * FROM dca_plans ORDER BY createdAt DESC")
    fun getAllPlans(): Flow<List<DcaPlanEntity>>

    @Query("SELECT * FROM dca_plans WHERE isEnabled = 1")
    suspend fun getEnabledPlans(): List<DcaPlanEntity>

    @Query("SELECT MIN(nextExecutionAt) FROM dca_plans WHERE isEnabled = 1 AND nextExecutionAt IS NOT NULL")
    suspend fun getEarliestNextExecution(): Long?

    @Query("SELECT * FROM dca_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): DcaPlanEntity?

    @Query("SELECT * FROM dca_plans WHERE exchange = :exchange")
    fun getPlansByExchange(exchange: Exchange): Flow<List<DcaPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: DcaPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: DcaPlanEntity)

    @Query("UPDATE dca_plans SET lastExecutedAt = :lastExecutedAt, nextExecutionAt = :nextExecutionAt WHERE id = :planId")
    suspend fun updateExecutionTime(planId: Long, lastExecutedAt: Instant, nextExecutionAt: Instant)

    @Query("UPDATE dca_plans SET isEnabled = :enabled WHERE id = :planId")
    suspend fun setEnabled(planId: Long, enabled: Boolean)

    @Delete
    suspend fun deletePlan(plan: DcaPlanEntity)

    @Query("DELETE FROM dca_plans WHERE id = :planId")
    suspend fun deletePlanById(planId: Long)
}

@Dao
interface TransactionDao {
    /**
     * Get all transactions as a Flow.
     * Warning: For large datasets, prefer getTransactionsPaged() to avoid OOM.
     */
    @Query("SELECT * FROM transactions ORDER BY executedAt DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    /**
     * Get all transactions as a one-time snapshot (suspend function).
     * Used for CSV export and other one-time operations.
     */
    @Query("SELECT * FROM transactions ORDER BY executedAt DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    /**
     * Get paginated transactions for efficient memory usage with large datasets.
     * @param limit Maximum number of items to return
     * @param offset Number of items to skip
     */
    @Query("SELECT * FROM transactions ORDER BY executedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPaged(limit: Int, offset: Int): List<TransactionEntity>

    /**
     * Get total count of transactions for pagination calculations.
     */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    /**
     * Get paginated filtered transactions for efficient memory usage.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE (:crypto IS NULL OR crypto = :crypto)
        AND (:exchange IS NULL OR exchange = :exchange)
        AND (:status IS NULL OR status = :status)
        ORDER BY executedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFilteredTransactionsPaged(
        crypto: String?,
        exchange: String?,
        status: String?,
        limit: Int,
        offset: Int
    ): List<TransactionEntity>

    /**
     * Get count of filtered transactions for pagination.
     */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE (:crypto IS NULL OR crypto = :crypto)
        AND (:exchange IS NULL OR exchange = :exchange)
        AND (:status IS NULL OR status = :status)
    """)
    suspend fun getFilteredTransactionCount(
        crypto: String?,
        exchange: String?,
        status: String?
    ): Int

    @Query("SELECT * FROM transactions WHERE planId = :planId ORDER BY executedAt DESC")
    fun getTransactionsByPlan(planId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE crypto = :crypto ORDER BY executedAt DESC")
    fun getTransactionsByCrypto(crypto: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE exchange = :exchange ORDER BY executedAt DESC")
    fun getTransactionsByExchange(exchange: Exchange): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY executedAt DESC")
    fun getTransactionsByStatus(status: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE executedAt BETWEEN :startTime AND :endTime ORDER BY executedAt DESC")
    fun getTransactionsByDateRange(startTime: Instant, endTime: Instant): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE (:crypto IS NULL OR crypto = :crypto)
        AND (:exchange IS NULL OR exchange = :exchange)
        AND (:status IS NULL OR status = :status)
        ORDER BY executedAt DESC
    """)
    fun getFilteredTransactions(
        crypto: String?,
        exchange: String?,
        status: String?
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY executedAt DESC LIMIT :limit")
    suspend fun getRecentTransactions(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    // Returns sum as String to avoid Double precision loss for monetary values
    @Query("SELECT CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT) FROM transactions WHERE fiat = :fiat AND status = 'COMPLETED'")
    suspend fun getTotalInvestedByFiat(fiat: String): String

    // Returns sum as String to avoid Double precision loss for crypto amounts
    @Query("SELECT CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) FROM transactions WHERE crypto = :crypto AND status = 'COMPLETED'")
    suspend fun getTotalCryptoBySymbol(crypto: String): String

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'COMPLETED'")
    suspend fun getCompletedTransactionCount(): Int

    @Query("SELECT DISTINCT crypto FROM transactions ORDER BY crypto ASC")
    suspend fun getDistinctCryptos(): List<String>

    @Query("SELECT DISTINCT exchange FROM transactions ORDER BY exchange ASC")
    suspend fun getDistinctExchanges(): List<String>

    // Monthly aggregation queries - returns strings to preserve BigDecimal precision
    @Query("""
        SELECT
            strftime('%Y-%m', datetime(executedAt/1000, 'unixepoch')) as month,
            CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT) as totalFiat,
            CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) as totalCrypto,
            COUNT(*) as txCount,
            CAST(COALESCE(AVG(CAST(price AS REAL)), 0) AS TEXT) as avgPrice
        FROM transactions
        WHERE status = 'COMPLETED' AND crypto = :crypto
        GROUP BY strftime('%Y-%m', datetime(executedAt/1000, 'unixepoch'))
        ORDER BY month DESC
    """)
    suspend fun getMonthlyStats(crypto: String): List<MonthlyStatsResult>

    @Query("SELECT exchangeOrderId FROM transactions WHERE planId = :planId AND exchangeOrderId IS NOT NULL")
    suspend fun getExchangeOrderIdsByPlan(planId: Long): List<String>

    @Query("SELECT MAX(executedAt) FROM transactions WHERE planId = :planId AND status = 'COMPLETED'")
    suspend fun getLatestTransactionTimestamp(planId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE planId = :planId")
    suspend fun deleteTransactionsByPlanId(planId: Long)

    @Query("""
        SELECT crypto || '/' || fiat as pair, crypto, fiat,
               CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) as totalCrypto,
               CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT) as totalFiat,
               COUNT(*) as transactionCount
        FROM transactions WHERE status = 'COMPLETED'
        GROUP BY crypto, fiat
        ORDER BY SUM(CAST(fiatAmount AS REAL)) DESC
    """)
    suspend fun getHoldingsByPair(): List<CryptoFiatHolding>

    @Query("""
        SELECT crypto || '/' || fiat as pair, crypto, fiat,
               CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) as totalCrypto,
               CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT) as totalFiat,
               COUNT(*) as transactionCount
        FROM transactions WHERE status = 'COMPLETED'
        GROUP BY crypto, fiat
        ORDER BY SUM(CAST(fiatAmount AS REAL)) DESC
    """)
    fun getHoldingsByPairFlow(): Flow<List<CryptoFiatHolding>>

    @Query("SELECT MIN(executedAt) FROM transactions WHERE status = 'COMPLETED' AND crypto = :crypto AND fiat = :fiat")
    suspend fun getEarliestTransactionDate(crypto: String, fiat: String): Long?

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("""
        SELECT * FROM transactions
        WHERE status = 'COMPLETED'
        AND (:exchange IS NULL OR exchange = :exchange)
        ORDER BY executedAt ASC
    """)
    suspend fun getCompletedTransactionsOrdered(exchange: String? = null): List<TransactionEntity>
}

data class CryptoFiatHolding(
    val pair: String,
    val crypto: String,
    val fiat: String,
    val totalCrypto: String,
    val totalFiat: String,
    val transactionCount: Int
)

/**
 * Monthly statistics result from aggregation query.
 * Uses String for monetary values to preserve precision when converting to BigDecimal.
 */
data class MonthlyStatsResult(
    val month: String,
    val totalFiat: String,   // Use BigDecimal(totalFiat) in calling code
    val totalCrypto: String, // Use BigDecimal(totalCrypto) in calling code
    val txCount: Int,
    val avgPrice: String     // Use BigDecimal(avgPrice) in calling code
)

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals ORDER BY createdAt DESC")
    fun getAllWithdrawals(): Flow<List<WithdrawalEntity>>

    @Query("SELECT * FROM withdrawals WHERE planId = :planId ORDER BY createdAt DESC")
    fun getWithdrawalsByPlan(planId: Long): Flow<List<WithdrawalEntity>>

    @Query("SELECT * FROM withdrawals WHERE status = 'PENDING' OR status = 'PROCESSING'")
    suspend fun getPendingWithdrawals(): List<WithdrawalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: WithdrawalEntity): Long

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity)

    @Delete
    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity)
}

@Dao
interface ExchangeBalanceDao {
    @Query("SELECT * FROM exchange_balances ORDER BY exchange, currency")
    fun getAllBalances(): Flow<List<ExchangeBalanceEntity>>

    @Query("SELECT * FROM exchange_balances WHERE exchange = :exchange")
    fun getBalancesByExchange(exchange: Exchange): Flow<List<ExchangeBalanceEntity>>

    @Query("SELECT * FROM exchange_balances WHERE id = :id")
    suspend fun getBalance(id: String): ExchangeBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalance(balance: ExchangeBalanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalances(balances: List<ExchangeBalanceEntity>)

    @Query("DELETE FROM exchange_balances WHERE exchange = :exchange")
    suspend fun deleteBalancesByExchange(exchange: Exchange)

    @Query("DELETE FROM exchange_balances")
    suspend fun deleteAllBalances()
}

@Dao
interface MonthlySummaryDao {
    @Query("SELECT * FROM monthly_summaries ORDER BY year DESC, month DESC")
    fun getAllSummaries(): Flow<List<MonthlySummaryEntity>>

    @Query("SELECT * FROM monthly_summaries ORDER BY year DESC, month DESC LIMIT :limit")
    suspend fun getRecentSummaries(limit: Int): List<MonthlySummaryEntity>

    @Query("SELECT * FROM monthly_summaries WHERE id = :id")
    suspend fun getSummary(id: String): MonthlySummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: MonthlySummaryEntity)

    @Query("DELETE FROM monthly_summaries")
    suspend fun deleteAllSummaries()
}

data class DailyPriceRow(val dateEpochDay: Long, val price: BigDecimal)

@Dao
interface DailyPriceDao {
    @Query("SELECT * FROM daily_prices WHERE crypto = :crypto AND fiat = :fiat AND dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay ASC")
    suspend fun getPricesInRange(crypto: String, fiat: String, from: Long, to: Long): List<DailyPriceEntity>

    @Query("SELECT dateEpochDay, price FROM daily_prices WHERE crypto = :crypto AND fiat = :fiat AND dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay ASC")
    suspend fun getPriceMapInRange(crypto: String, fiat: String, from: Long, to: Long): List<DailyPriceRow>

    @Query("SELECT MAX(dateEpochDay) FROM daily_prices WHERE crypto = :crypto AND fiat = :fiat")
    suspend fun getLatestDay(crypto: String, fiat: String): Long?

    @Query("SELECT MIN(dateEpochDay) FROM daily_prices WHERE crypto = :crypto AND fiat = :fiat")
    suspend fun getEarliestDay(crypto: String, fiat: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<DailyPriceEntity>)
}
