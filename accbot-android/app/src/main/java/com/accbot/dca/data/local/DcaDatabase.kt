package com.accbot.dca.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DcaPlanEntity::class,
        TransactionEntity::class,
        WithdrawalEntity::class,
        ExchangeBalanceEntity::class,
        MonthlySummaryEntity::class,
        DailyPriceEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DcaDatabase : RoomDatabase() {
    abstract fun dcaPlanDao(): DcaPlanDao
    abstract fun transactionDao(): TransactionDao
    abstract fun withdrawalDao(): WithdrawalDao
    abstract fun exchangeBalanceDao(): ExchangeBalanceDao
    abstract fun monthlySummaryDao(): MonthlySummaryDao
    abstract fun dailyPriceDao(): DailyPriceDao

    companion object {
        private const val LEGACY_DATABASE_NAME = "accbot_dca.db"
        private const val PROD_DATABASE_NAME = "accbot_prod.db"
        private const val SANDBOX_DATABASE_NAME = "accbot_sandbox.db"

        @Volatile
        private var prodInstance: DcaDatabase? = null

        @Volatile
        private var sandboxInstance: DcaDatabase? = null

        // Migration from version 1 to 2: Add exchange_balances and monthly_summaries tables
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create exchange_balances table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS exchange_balances (
                        id TEXT PRIMARY KEY NOT NULL,
                        exchange TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        balance TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                """)

                // Create monthly_summaries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS monthly_summaries (
                        id TEXT PRIMARY KEY NOT NULL,
                        year INTEGER NOT NULL,
                        month INTEGER NOT NULL,
                        totalInvestedEur TEXT NOT NULL,
                        totalBtcAccumulated TEXT NOT NULL,
                        transactionCount INTEGER NOT NULL,
                        averageBtcPrice TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                """)
            }
        }

        // Migration from version 2 to 3: Add indexes for query optimization
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Indexes for dca_plans table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_isEnabled ON dca_plans (isEnabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_exchange ON dca_plans (exchange)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_nextExecutionAt ON dca_plans (nextExecutionAt)")

                // Indexes for transactions table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_planId ON transactions (planId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_exchange ON transactions (exchange)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_crypto ON transactions (crypto)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_status ON transactions (status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_executedAt ON transactions (executedAt)")

                // Indexes for withdrawals table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_withdrawals_planId ON withdrawals (planId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_withdrawals_status ON withdrawals (status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_withdrawals_createdAt ON withdrawals (createdAt)")

                // Index for exchange_balances table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_balances_exchange ON exchange_balances (exchange)")
            }
        }

        // Migration from version 3 to 4: Add strategy column to dca_plans
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add strategy column with default value 'CLASSIC'
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN strategy TEXT NOT NULL DEFAULT 'CLASSIC'")
            }
        }

        // Migration from version 4 to 5: Add feeAsset column to transactions
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN feeAsset TEXT NOT NULL DEFAULT ''")
            }
        }

        // Migration from version 5 to 6: Add daily_prices table for performance charts
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_prices (
                        crypto TEXT NOT NULL,
                        fiat TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        price TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY (crypto, fiat, dateEpochDay)
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_daily_prices_crypto_fiat ON daily_prices (crypto, fiat)")
            }
        }

        // Migration from version 6 to 7: Add compound indexes for query optimization
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_isEnabled_nextExecutionAt ON dca_plans (isEnabled, nextExecutionAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_planId_status ON transactions (planId, status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_crypto_fiat_status ON transactions (crypto, fiat, status)")
            }
        }

        // Migration from version 7 to 8: Add cronExpression column to dca_plans
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN cronExpression TEXT DEFAULT NULL")
            }
        }

        /**
         * Get the database instance for the specified mode.
         * Production and sandbox use separate database files to prevent data mixing.
         *
         * @param context Application context
         * @param isSandbox Whether to use sandbox database
         * @return Database instance for the specified mode
         */
        fun getInstance(context: Context, isSandbox: Boolean = false): DcaDatabase {
            // Migrate legacy database to prod on first access
            migrateLegacyDatabase(context)

            return if (isSandbox) {
                sandboxInstance ?: synchronized(this) {
                    sandboxInstance ?: buildDatabase(context, SANDBOX_DATABASE_NAME).also {
                        sandboxInstance = it
                    }
                }
            } else {
                prodInstance ?: synchronized(this) {
                    prodInstance ?: buildDatabase(context, PROD_DATABASE_NAME).also {
                        prodInstance = it
                    }
                }
            }
        }

        /**
         * Migrate legacy database (accbot_dca.db) to production database name.
         * This is a one-time migration for existing users.
         */
        private fun migrateLegacyDatabase(context: Context) {
            val legacyDbFile = context.getDatabasePath(LEGACY_DATABASE_NAME)
            val prodDbFile = context.getDatabasePath(PROD_DATABASE_NAME)

            if (legacyDbFile.exists() && !prodDbFile.exists()) {
                // Rename legacy database to production database
                legacyDbFile.renameTo(prodDbFile)

                // Also migrate WAL and SHM files if they exist
                val legacyWal = context.getDatabasePath("$LEGACY_DATABASE_NAME-wal")
                val legacyShm = context.getDatabasePath("$LEGACY_DATABASE_NAME-shm")
                val prodWal = context.getDatabasePath("$PROD_DATABASE_NAME-wal")
                val prodShm = context.getDatabasePath("$PROD_DATABASE_NAME-shm")

                if (legacyWal.exists()) legacyWal.renameTo(prodWal)
                if (legacyShm.exists()) legacyShm.renameTo(prodShm)
            }
        }

        private fun buildDatabase(context: Context, databaseName: String): DcaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DcaDatabase::class.java,
                databaseName
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                // Only allow destructive migration on app downgrade, never on failed upgrade
                // This protects user's transaction history from accidental deletion
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
