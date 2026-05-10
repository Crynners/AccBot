package com.accbot.dca.data.local

import androidx.room.*
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

@Dao
interface ExchangeConnectionDao {
    @Query("SELECT * FROM exchange_connections ORDER BY exchange, displayOrder, createdAt")
    fun getAllFlow(): Flow<List<ExchangeConnectionEntity>>

    @Query("SELECT * FROM exchange_connections ORDER BY exchange, displayOrder, createdAt")
    suspend fun getAll(): List<ExchangeConnectionEntity>

    @Query("SELECT * FROM exchange_connections WHERE exchange = :exchange ORDER BY displayOrder, createdAt")
    suspend fun getByExchange(exchange: Exchange): List<ExchangeConnectionEntity>

    @Query("SELECT * FROM exchange_connections WHERE exchange = :exchange ORDER BY displayOrder, createdAt LIMIT 1")
    suspend fun getDefaultByExchange(exchange: Exchange): ExchangeConnectionEntity?

    @Query("SELECT * FROM exchange_connections WHERE id = :id")
    suspend fun getById(id: Long): ExchangeConnectionEntity?

    @Query("SELECT COUNT(*) FROM exchange_connections WHERE exchange = :exchange")
    suspend fun countByExchange(exchange: Exchange): Int

    @Insert
    suspend fun insert(connection: ExchangeConnectionEntity): Long

    @Update
    suspend fun update(connection: ExchangeConnectionEntity)

    @Query("DELETE FROM exchange_connections WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DcaPlanDao {
    @Query("SELECT * FROM dca_plans ORDER BY displayOrder ASC, createdAt DESC")
    fun getAllPlans(): Flow<List<DcaPlanEntity>>

    @Query("SELECT * FROM dca_plans WHERE isEnabled = 1")
    suspend fun getEnabledPlans(): List<DcaPlanEntity>

    @Query("SELECT MIN(nextExecutionAt) FROM dca_plans WHERE isEnabled = 1 AND nextExecutionAt IS NOT NULL")
    suspend fun getEarliestNextExecution(): Long?

    @Query("SELECT * FROM dca_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): DcaPlanEntity?

    @Query("SELECT * FROM dca_plans WHERE exchange = :exchange")
    fun getPlansByExchange(exchange: Exchange): Flow<List<DcaPlanEntity>>

    @Query("SELECT * FROM dca_plans WHERE connectionId = :connectionId")
    fun getPlansByConnection(connectionId: Long): Flow<List<DcaPlanEntity>>

    @Query("SELECT COUNT(*) FROM dca_plans WHERE connectionId = :connectionId")
    suspend fun countPlansByConnection(connectionId: Long): Int

    @Query("DELETE FROM dca_plans WHERE connectionId = :connectionId")
    suspend fun deletePlansByConnection(connectionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: DcaPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: DcaPlanEntity)

    @Query("UPDATE dca_plans SET lastExecutedAt = :lastExecutedAt, nextExecutionAt = :nextExecutionAt WHERE id = :planId")
    suspend fun updateExecutionTime(planId: Long, lastExecutedAt: Instant, nextExecutionAt: Instant)

    @Query("UPDATE dca_plans SET lastExecutedAt = :lastExecutedAt, nextExecutionAt = :nextExecutionAt WHERE id = :planId")
    fun updateExecutionTimeSync(planId: Long, lastExecutedAt: Instant, nextExecutionAt: Instant)

    /**
     * Atomically claim a plan for execution by advancing nextExecutionAt,
     * but only if it is still in the past (or null). Returns the number of
     * rows affected: 1 = claimed, 0 = another worker already claimed it.
     */
    @Query("UPDATE dca_plans SET nextExecutionAt = :newNextExecutionAt, lastExecutedAt = :now WHERE id = :planId AND (nextExecutionAt IS NULL OR nextExecutionAt <= :now)")
    fun claimPlanForExecutionSync(planId: Long, now: Instant, newNextExecutionAt: Instant): Int

    @Query("SELECT * FROM dca_plans WHERE id = :id")
    fun getPlanByIdSync(id: Long): DcaPlanEntity?

    @Query("UPDATE dca_plans SET isEnabled = :enabled WHERE id = :planId")
    suspend fun setEnabled(planId: Long, enabled: Boolean)

    @Delete
    suspend fun deletePlan(plan: DcaPlanEntity)

    @Query("DELETE FROM dca_plans WHERE id = :planId")
    suspend fun deletePlanById(planId: Long)

    @Query("DELETE FROM dca_plans")
    suspend fun deleteAllPlans()

    @Query("DELETE FROM dca_plans WHERE exchange = :exchange")
    suspend fun deletePlansByExchange(exchange: Exchange)

    @Query("SELECT COUNT(*) FROM dca_plans")
    suspend fun getPlanCount(): Int

    @Query("SELECT * FROM dca_plans ORDER BY createdAt DESC")
    suspend fun getAllPlansOnce(): List<DcaPlanEntity>

    @Query("UPDATE dca_plans SET networkRetryCount = networkRetryCount + 1, nextNetworkRetryAt = :nextRetryAt, originalScheduledAt = CASE WHEN originalScheduledAt IS NULL THEN :originalScheduledAt ELSE originalScheduledAt END WHERE id = :planId")
    suspend fun incrementNetworkRetry(planId: Long, nextRetryAt: Instant, originalScheduledAt: Instant)

    @Query("UPDATE dca_plans SET networkRetryCount = networkRetryCount + 1, nextNetworkRetryAt = :nextRetryAt, originalScheduledAt = CASE WHEN originalScheduledAt IS NULL THEN :originalScheduledAt ELSE originalScheduledAt END WHERE id = :planId")
    fun incrementNetworkRetrySync(planId: Long, nextRetryAt: Instant, originalScheduledAt: Instant)

    @Query("UPDATE dca_plans SET networkRetryCount = 0, nextNetworkRetryAt = NULL, originalScheduledAt = NULL WHERE id = :planId")
    suspend fun resetNetworkRetry(planId: Long)

    @Query("UPDATE dca_plans SET missedPurchaseCount = :count WHERE id = :planId")
    suspend fun setMissedPurchaseCount(planId: Long, count: Int)

    @Query("UPDATE dca_plans SET missedPurchaseCount = 0 WHERE id = :planId")
    suspend fun resetMissedPurchaseCount(planId: Long)

    @Query("UPDATE dca_plans SET name = :name WHERE id = :planId")
    suspend fun renamePlan(planId: Long, name: String)

    @Query("UPDATE dca_plans SET displayOrder = :displayOrder WHERE id = :planId")
    suspend fun updateDisplayOrder(planId: Long, displayOrder: Int)

    @Query("SELECT * FROM dca_plans ORDER BY displayOrder ASC, createdAt DESC")
    suspend fun getAllPlansOnceOrdered(): List<DcaPlanEntity>

    @Query("SELECT COALESCE(MAX(displayOrder), -1) FROM dca_plans")
    suspend fun getMaxDisplayOrder(): Int

    @Transaction
    suspend fun updateAllDisplayOrders(planOrders: List<Pair<Long, Int>>) {
        for ((planId, order) in planOrders) {
            updateDisplayOrder(planId, order)
        }
    }

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

    /**
     * One-shot snapshot of all transactions for a plan. Used by PnL / validation
     * logic where a Flow isn't practical (suspend call from use case).
     */
    @Query("SELECT * FROM transactions WHERE planId = :planId ORDER BY executedAt DESC")
    suspend fun getTransactionsByPlanSync(planId: Long): List<TransactionEntity>

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

    // Returns sum as String to avoid Double precision loss for monetary values.
    // SELL excluded: this query represents money INVESTED via BUYs, not net cash flow.
    @Query("SELECT CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT) FROM transactions WHERE fiat = :fiat AND status = 'COMPLETED' AND side = 'BUY'")
    suspend fun getTotalInvestedByFiat(fiat: String): String

    // Returns sum as String to avoid Double precision loss for crypto amounts.
    // SELL excluded: represents accumulated BUY volume, mirroring the dashboard's
    // "total accumulated" KPI.
    @Query("SELECT CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) FROM transactions WHERE crypto = :crypto AND status = 'COMPLETED' AND side = 'BUY'")
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

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' AND exchangeOrderId IS NOT NULL")
    suspend fun getPendingTransactionsWithOrderId(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE status IN ('PENDING', 'PARTIAL') AND exchangeOrderId IS NOT NULL")
    suspend fun getResolvablePendingTransactions(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE side = 'SELL' AND status IN ('PENDING', 'PARTIAL')")
    suspend fun countOpenSells(): Int

    @Query("""
        SELECT * FROM transactions
        WHERE planId = :planId
          AND side = 'SELL'
          AND status IN ('PENDING', 'PARTIAL')
        ORDER BY executedAt DESC
    """)
    fun observeOpenSellsForPlan(planId: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE side = 'SELL' AND status IN ('PENDING', 'PARTIAL')
        ORDER BY executedAt DESC
    """)
    fun observeAllOpenSells(): Flow<List<TransactionEntity>>

    /**
     * Guarded update for resolving an order. Only updates rows still in PENDING/PARTIAL
     * state - prevents races with concurrent user cancel (which sets status=FAILED).
     * Returns number of rows updated (0 = race lost, order state already changed).
     */
    @Query("""
        UPDATE transactions
        SET status = :newStatus,
            cryptoAmount = :cryptoAmount,
            fiatAmount = :fiatAmount,
            price = :price,
            fee = :fee
        WHERE id = :id
          AND status IN ('PENDING', 'PARTIAL')
    """)
    suspend fun updateResolvedTransaction(
        id: Long,
        newStatus: TransactionStatus,
        cryptoAmount: BigDecimal,
        fiatAmount: BigDecimal,
        price: BigDecimal,
        fee: BigDecimal
    ): Int

    @Query("SELECT exchangeOrderId FROM transactions WHERE planId = :planId AND exchangeOrderId IS NOT NULL")
    suspend fun getExchangeOrderIdsByPlan(planId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactionSync(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE planId = :planId")
    suspend fun deleteTransactionsByPlanId(planId: Long)

    @Query("DELETE FROM transactions WHERE exchange = :exchange")
    suspend fun deleteTransactionsByExchange(exchange: Exchange)

    /**
     * Per-pair holdings used for dashboard / portfolio summaries.
     * Sell-extension: SELL rows are excluded so the displayed "total accumulated"
     * and "total invested" reflect BUY-only DCA activity. Realized P&L from sells
     * is surfaced separately in the portfolio summary, not subtracted from these
     * totals (avoids double-counting and keeps the historical chart logic stable).
     */
    @Query("""
        SELECT crypto || '/' || fiat as pair, crypto, fiat,
               CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) as totalCrypto,
               CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT) as totalFiat,
               COUNT(*) as transactionCount
        FROM transactions WHERE status = 'COMPLETED' AND side = 'BUY'
        GROUP BY crypto, fiat
        ORDER BY SUM(CAST(fiatAmount AS REAL)) DESC
    """)
    suspend fun getHoldingsByPair(): List<CryptoFiatHolding>

    @Query("""
        SELECT crypto || '/' || fiat as pair, crypto, fiat,
               CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) as totalCrypto,
               CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT) as totalFiat,
               COUNT(*) as transactionCount
        FROM transactions WHERE status = 'COMPLETED' AND side = 'BUY'
        GROUP BY crypto, fiat
        ORDER BY SUM(CAST(fiatAmount AS REAL)) DESC
    """)
    fun getHoldingsByPairFlow(): Flow<List<CryptoFiatHolding>>

    @Query("SELECT MIN(executedAt) FROM transactions WHERE status = 'COMPLETED' AND crypto = :crypto AND fiat = :fiat")
    suspend fun getEarliestTransactionDate(crypto: String, fiat: String): Long?

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    /**
     * Used by the portfolio chart pipeline. SELL excluded so the historical
     * "invested / accumulated" series stays monotonic (sells would create
     * counterintuitive dips in cumulative volume). Realized P&L is reported
     * separately in the portfolio summary, see [getRealizedFiatByFiat].
     */
    @Query("""
        SELECT * FROM transactions
        WHERE status = 'COMPLETED' AND side = 'BUY'
        AND (:exchange IS NULL OR exchange = :exchange)
        ORDER BY executedAt ASC
    """)
    suspend fun getCompletedTransactionsOrdered(exchange: String? = null): List<TransactionEntity>

    /**
     * Completed SELL transactions, ordered by execution. Used to draw chart timeline
     * markers; kept separate from [getCompletedTransactionsOrdered] so the BUY-only
     * invariant of the chart series pipeline isn't disturbed.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE status = 'COMPLETED' AND side = 'SELL'
        AND (:exchange IS NULL OR exchange = :exchange)
        ORDER BY executedAt ASC
    """)
    suspend fun getCompletedSellsOrdered(exchange: String? = null): List<TransactionEntity>

    @Query("SELECT CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) FROM transactions WHERE exchange = :exchange AND crypto = :crypto AND status = 'COMPLETED' AND side = 'BUY'")
    suspend fun getTotalCryptoByExchangeAndCrypto(exchange: String, crypto: String): String

    @Query("SELECT CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) FROM transactions WHERE connectionId = :connectionId AND crypto = :crypto AND status = 'COMPLETED' AND side = 'BUY'")
    suspend fun getTotalCryptoByConnectionAndCrypto(connectionId: Long, crypto: String): String

    @Query("SELECT * FROM transactions WHERE exchangeOrderId = :orderId LIMIT 1")
    suspend fun getByExchangeOrderId(orderId: String): TransactionEntity?

    /**
     * Connection-scoped lookup: find a transaction by exchange order id within a
     * specific connection. Used by backup restore dedup so two connections
     * (e.g. prod vs sandbox, or "main" vs "savings") that happen to share an
     * exchangeOrderId don't collapse into one row.
     */
    @Query("SELECT * FROM transactions WHERE exchangeOrderId = :orderId AND connectionId = :connectionId LIMIT 1")
    suspend fun getByExchangeOrderIdAndConnection(orderId: String, connectionId: Long): TransactionEntity?

    @Query("SELECT CAST(COALESCE(SUM(CAST(cryptoAmount AS REAL)), 0) AS TEXT) FROM transactions WHERE planId = :planId AND status = 'COMPLETED' AND side = 'BUY'")
    suspend fun getAccumulatedCryptoByPlan(planId: Long): String

    /**
     * Total realized fiat from completed/partial SELL orders for a given fiat
     * currency. Used by the portfolio summary to surface "Realized P&L" alongside
     * the existing BUY-based totals. PARTIAL is included because partial fills
     * have already booked their fiatAmount (= filled amount * avg fill price).
     */
    @Query("""
        SELECT CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT)
        FROM transactions
        WHERE fiat = :fiat AND side = 'SELL' AND status IN ('COMPLETED', 'PARTIAL')
    """)
    suspend fun getRealizedFiatByFiat(fiat: String): String

    /**
     * Per-plan variant of [getRealizedFiatByFiat]. Same semantics, scoped to a
     * single plan id so the per-plan portfolio summary can show realized P&L
     * for that plan only.
     */
    @Query("""
        SELECT CAST(COALESCE(SUM(CAST(fiatAmount AS REAL)), 0) AS TEXT)
        FROM transactions
        WHERE planId = :planId AND side = 'SELL' AND status IN ('COMPLETED', 'PARTIAL')
    """)
    suspend fun getRealizedFiatByPlan(planId: Long): String
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

    @Query("DELETE FROM withdrawals")
    suspend fun deleteAllWithdrawals()

    @Query("SELECT * FROM withdrawals ORDER BY createdAt DESC")
    suspend fun getAllWithdrawalsOnce(): List<WithdrawalEntity>

    @Query("SELECT COUNT(*) FROM withdrawals")
    suspend fun getWithdrawalCount(): Int
}

@Dao
interface ExchangeBalanceDao {
    @Query("SELECT * FROM exchange_balances ORDER BY exchange, currency")
    fun getAllBalances(): Flow<List<ExchangeBalanceEntity>>

    @Query("SELECT * FROM exchange_balances WHERE exchange = :exchange")
    fun getBalancesByExchange(exchange: Exchange): Flow<List<ExchangeBalanceEntity>>

    @Query("SELECT * FROM exchange_balances WHERE connectionId = :connectionId")
    fun getBalancesByConnection(connectionId: Long): Flow<List<ExchangeBalanceEntity>>

    @Query("SELECT * FROM exchange_balances WHERE connectionId = :connectionId AND currency = :currency")
    suspend fun getBalance(connectionId: Long, currency: String): ExchangeBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalance(balance: ExchangeBalanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalances(balances: List<ExchangeBalanceEntity>)

    @Query("DELETE FROM exchange_balances WHERE exchange = :exchange")
    suspend fun deleteBalancesByExchange(exchange: Exchange)

    @Query("DELETE FROM exchange_balances WHERE connectionId = :connectionId")
    suspend fun deleteBalancesByConnection(connectionId: Long)

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

    @Query("DELETE FROM daily_prices")
    suspend fun deleteAllPrices()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getActiveNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getNotificationCount(): Int

    @Query("SELECT systemNotificationId FROM notifications WHERE id = :id")
    suspend fun getSystemNotificationId(id: Long): Int?

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    suspend fun getAllNotificationsOnce(): List<NotificationEntity>

}

@Dao
interface WithdrawalThresholdDao {
    @Query("SELECT * FROM withdrawal_thresholds")
    fun getAll(): Flow<List<WithdrawalThresholdEntity>>

    @Query("SELECT * FROM withdrawal_thresholds WHERE crypto = :crypto AND connectionId = :connectionId")
    suspend fun get(crypto: String, connectionId: Long): WithdrawalThresholdEntity?

    @Query("SELECT * FROM withdrawal_thresholds WHERE connectionId = :connectionId")
    fun getByConnection(connectionId: Long): Flow<List<WithdrawalThresholdEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WithdrawalThresholdEntity)

    @Query("DELETE FROM withdrawal_thresholds WHERE crypto = :crypto AND connectionId = :connectionId")
    suspend fun delete(crypto: String, connectionId: Long)

    /**
     * Delete all thresholds for a connection. Used as manual cascade when an
     * [ExchangeConnectionEntity] is deleted, since FK enforcement (`PRAGMA foreign_keys`)
     * is currently disabled in Room and the schema-level `ON DELETE CASCADE` is a no-op.
     */
    @Query("DELETE FROM withdrawal_thresholds WHERE connectionId = :connectionId")
    suspend fun deleteByConnection(connectionId: Long)

    @Query("SELECT thresholdAmount FROM withdrawal_thresholds WHERE connectionId = :connectionId AND crypto = :crypto")
    suspend fun getThresholdAmount(connectionId: Long, crypto: String): BigDecimal?

    @Query("DELETE FROM withdrawal_thresholds")
    suspend fun deleteAll()

    @Query("SELECT * FROM withdrawal_thresholds")
    suspend fun getAllThresholdsOnce(): List<WithdrawalThresholdEntity>
}
