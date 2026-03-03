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
        DailyPriceEntity::class,
        NotificationEntity::class,
        WithdrawalThresholdEntity::class
    ],
    version = 14,
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
    abstract fun notificationDao(): NotificationDao
    abstract fun withdrawalThresholdDao(): WithdrawalThresholdDao

    companion object {
        private const val LEGACY_DATABASE_NAME = "accbot_dca.db"
        private const val PROD_DATABASE_NAME = "accbot_prod.db"
        private const val SANDBOX_DATABASE_NAME = "accbot_sandbox.db"

        @Volatile
        private var prodInstance: DcaDatabase? = null

        @Volatile
        private var sandboxInstance: DcaDatabase? = null

        @Volatile
        private var legacyMigrationDone: Boolean = false

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

        // Migration from version 8 to 9: Add warningMessage column to transactions
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN warningMessage TEXT DEFAULT NULL")
            }
        }

        // Migration from version 10 to 11: Add isArchived column to notifications
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notifications ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_isArchived ON notifications (isArchived)")
            }
        }

        // Migration from version 11 to 12: Add systemNotificationId column to notifications
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notifications ADD COLUMN systemNotificationId INTEGER DEFAULT NULL")
            }
        }

        // Migration from version 12 to 13: Add targetAmount column to dca_plans
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN targetAmount TEXT DEFAULT NULL")
            }
        }

        // Migration from version 13 to 14: Add templateArgs column to notifications
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notifications ADD COLUMN templateArgs TEXT DEFAULT NULL")
            }
        }

        // Migration from version 9 to 10: Add notifications and withdrawal_thresholds tables
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        planId INTEGER,
                        crypto TEXT,
                        exchange TEXT,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_isRead ON notifications (isRead)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_createdAt ON notifications (createdAt)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS withdrawal_thresholds (
                        crypto TEXT NOT NULL,
                        exchange TEXT NOT NULL,
                        thresholdAmount TEXT NOT NULL,
                        PRIMARY KEY (crypto, exchange)
                    )
                """)
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
            // Migrate legacy database to prod on first access (skip check after first run)
            if (!legacyMigrationDone) {
                migrateLegacyDatabase(context)
                legacyMigrationDone = true
            }

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
                // Rename legacy database to production database, fall back to copy
                if (!legacyDbFile.renameTo(prodDbFile)) {
                    legacyDbFile.copyTo(prodDbFile, overwrite = false)
                    legacyDbFile.delete()
                }

                // Also migrate WAL and SHM files if they exist
                val legacyWal = context.getDatabasePath("$LEGACY_DATABASE_NAME-wal")
                val legacyShm = context.getDatabasePath("$LEGACY_DATABASE_NAME-shm")
                val prodWal = context.getDatabasePath("$PROD_DATABASE_NAME-wal")
                val prodShm = context.getDatabasePath("$PROD_DATABASE_NAME-shm")

                if (legacyWal.exists()) {
                    if (!legacyWal.renameTo(prodWal)) {
                        legacyWal.copyTo(prodWal, overwrite = false)
                        legacyWal.delete()
                    }
                }
                if (legacyShm.exists()) {
                    if (!legacyShm.renameTo(prodShm)) {
                        legacyShm.copyTo(prodShm, overwrite = false)
                        legacyShm.delete()
                    }
                }
            }
        }

        private fun buildDatabase(context: Context, databaseName: String): DcaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DcaDatabase::class.java,
                databaseName
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                // Only allow destructive migration on app downgrade, never on failed upgrade
                // This protects user's transaction history from accidental deletion
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
