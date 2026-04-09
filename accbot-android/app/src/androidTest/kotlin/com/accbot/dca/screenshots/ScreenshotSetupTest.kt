package com.accbot.dca.screenshots

import androidx.test.platform.app.InstrumentationRegistry
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.local.DailyPriceEntity
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.ExchangeBalanceEntity
import com.accbot.dca.data.local.ExchangeConnectionEntity
import com.accbot.dca.data.local.NotificationEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.data.local.NotificationType
import com.accbot.dca.domain.model.TransactionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Standalone data setup test for screenshot generation.
 *
 * Populates the PROD database with realistic DCA data (plans, transactions,
 * daily prices, balances, notifications) so the app can be explored manually
 * on the emulator with realistic-looking screens.
 *
 * Run:
 * ```
 * cd accbot-android
 * JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="$JAVA_HOME/bin:$PATH" \
 *   ./gradlew connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.accbot.dca.screenshots.ScreenshotSetupTest
 * ```
 */
class ScreenshotSetupTest {

    @Test
    fun setupScreenshotData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = Instant.now()

        // 1. Preferences
        val onboarding = OnboardingPreferences(context)
        onboarding.setOnboardingCompleted(true)

        val prefs = UserPreferences(context)
        prefs.setSandboxMode(false)
        prefs.setBiometricLockEnabled(false)
        prefs.setLastSeenVersionCode(99999)
        prefs.setMarketPulseEnabled(true)
        prefs.setMarketPulseExpanded(true)

        // 2. Room DB — prod database (constructed first because CredentialsStore needs the DAO)
        val db = DcaDatabase.getInstance(context, isSandbox = false)
        val creds = CredentialsStore(context, db.exchangeConnectionDao())

        runBlocking {
            // Clean slate (also clears any prior connections so unique index doesn't trip)
            db.dcaPlanDao().deleteAllPlans()
            db.transactionDao().deleteAllTransactions()
            db.dailyPriceDao().deleteAllPrices()
            db.exchangeBalanceDao().deleteAllBalances()
            db.notificationDao().deleteAllNotifications()

            // Insert default connections first (one per exchange used by the screenshots)
            val coinmateConnectionId = db.exchangeConnectionDao().insert(
                ExchangeConnectionEntity(exchange = Exchange.COINMATE, name = "")
            )
            val binanceConnectionId = db.exchangeConnectionDao().insert(
                ExchangeConnectionEntity(exchange = Exchange.BINANCE, name = "")
            )

            // Credentials (dummy — app won't call APIs during screenshots).
            // Use the connection-keyed API directly to avoid the legacy shim's auto-create.
            creds.saveCredentials(
                connectionId = coinmateConnectionId,
                credentials = ExchangeCredentials(Exchange.COINMATE, "demo_key", "demo_secret", clientId = "12345"),
                isSandbox = false
            )
            creds.saveCredentials(
                connectionId = binanceConnectionId,
                credentials = ExchangeCredentials(Exchange.BINANCE, "demo_key", "demo_secret"),
                isSandbox = false
            )

            // Insert plans
            val btcPlanId = db.dcaPlanDao().insertPlan(
                DcaPlanEntity(
                    exchange = Exchange.COINMATE, connectionId = coinmateConnectionId,
                    crypto = "BTC", fiat = "EUR",
                    amount = BigDecimal("50"), frequency = DcaFrequency.DAILY,
                    strategy = DcaStrategy.Classic, isEnabled = true,
                    withdrawalEnabled = true,
                    withdrawalAddress = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                    createdAt = now.minus(Duration.ofDays(180)),
                    lastExecutedAt = now.minus(Duration.ofHours(2)),
                    nextExecutionAt = now.plus(Duration.ofDays(1))
                )
            )
            val ethPlanId = db.dcaPlanDao().insertPlan(
                DcaPlanEntity(
                    exchange = Exchange.BINANCE, connectionId = binanceConnectionId,
                    crypto = "ETH", fiat = "EUR",
                    amount = BigDecimal("30"), frequency = DcaFrequency.WEEKLY,
                    strategy = DcaStrategy.FearAndGreed(), isEnabled = true,
                    createdAt = now.minus(Duration.ofDays(120)),
                    lastExecutedAt = now.minus(Duration.ofHours(6)),
                    nextExecutionAt = now.plus(Duration.ofDays(5))
                )
            )

            // Daily prices — real historical data from CryptoCompare
            val totalDays = HistoricalPrices.BTC_EUR.size // 201

            val btcPrices = HistoricalPrices.BTC_EUR.mapIndexed { i, price ->
                val date = LocalDate.now().minusDays((totalDays - 1 - i).toLong())
                DailyPriceEntity(
                    crypto = "BTC", fiat = "EUR",
                    dateEpochDay = date.toEpochDay(),
                    price = BigDecimal(price).setScale(2, RoundingMode.HALF_UP)
                )
            }
            db.dailyPriceDao().insertPrices(btcPrices)

            val ethPrices = HistoricalPrices.ETH_EUR.mapIndexed { i, price ->
                val date = LocalDate.now().minusDays((totalDays - 1 - i).toLong())
                DailyPriceEntity(
                    crypto = "ETH", fiat = "EUR",
                    dateEpochDay = date.toEpochDay(),
                    price = BigDecimal(price).setScale(2, RoundingMode.HALF_UP)
                )
            }
            db.dailyPriceDao().insertPrices(ethPrices)

            // BTC transactions — daily over 180 days
            val btcTxCount = 180
            val btcTransactions = (0 until btcTxCount).map { i ->
                val daysAgo = btcTxCount.toLong() - i
                val priceIndex = (totalDays.toLong() - 1 - daysAgo).toInt().coerceIn(0, totalDays - 1)
                val price = HistoricalPrices.BTC_EUR[priceIndex]
                val cryptoAmount = 50.0 / price
                TransactionEntity(
                    planId = btcPlanId, exchange = Exchange.COINMATE,
                    crypto = "BTC", fiat = "EUR",
                    fiatAmount = BigDecimal("50.00"),
                    cryptoAmount = BigDecimal(cryptoAmount).setScale(8, RoundingMode.HALF_UP),
                    price = BigDecimal(price).setScale(2, RoundingMode.HALF_UP),
                    fee = BigDecimal("0.13"), feeAsset = "EUR",
                    status = TransactionStatus.COMPLETED,
                    executedAt = now.minus(Duration.ofDays(daysAgo))
                )
            }
            db.transactionDao().insertTransactions(btcTransactions)

            // ETH transactions — weekly over 180 days = ~26 transactions
            val ethTxCount = 26
            val ethTransactions = (0 until ethTxCount).map { i ->
                val daysAgo = btcTxCount.toLong() - (i * 7).toLong()
                val priceIndex = (totalDays.toLong() - 1 - daysAgo).toInt().coerceIn(0, totalDays - 1)
                val price = HistoricalPrices.ETH_EUR[priceIndex]
                val cryptoAmount = 30.0 / price
                TransactionEntity(
                    planId = ethPlanId, exchange = Exchange.BINANCE,
                    crypto = "ETH", fiat = "EUR",
                    fiatAmount = BigDecimal("30.00"),
                    cryptoAmount = BigDecimal(cryptoAmount).setScale(8, RoundingMode.HALF_UP),
                    price = BigDecimal(price).setScale(2, RoundingMode.HALF_UP),
                    fee = BigDecimal("0.08"), feeAsset = "EUR",
                    status = TransactionStatus.COMPLETED,
                    executedAt = now.minus(Duration.ofDays(daysAgo))
                )
            }
            db.transactionDao().insertTransactions(ethTransactions)

            // Exchange balances — calculated from accumulated crypto
            val totalBtcAccumulated = btcTransactions.sumOf { it.cryptoAmount }
            val totalEthAccumulated = ethTransactions.sumOf { it.cryptoAmount }

            db.exchangeBalanceDao().insertBalances(
                listOf(
                    ExchangeBalanceEntity(coinmateConnectionId, "BTC", Exchange.COINMATE, totalBtcAccumulated, now),
                    ExchangeBalanceEntity(coinmateConnectionId, "EUR", Exchange.COINMATE, BigDecimal("142.50"), now),
                    ExchangeBalanceEntity(binanceConnectionId, "ETH", Exchange.BINANCE, totalEthAccumulated, now),
                    ExchangeBalanceEntity(binanceConnectionId, "EUR", Exchange.BINANCE, BigDecimal("85.00"), now),
                )
            )

            // Notifications (mix of read/unread)
            db.notificationDao().insert(
                NotificationEntity(
                    type = NotificationType.PURCHASE, title = "BTC Purchase",
                    message = "Bought 0.00082 BTC for 50.00 EUR on Coinmate",
                    planId = btcPlanId, crypto = "BTC", exchange = Exchange.COINMATE,
                    isRead = false, createdAt = now.minus(Duration.ofHours(2))
                )
            )
            db.notificationDao().insert(
                NotificationEntity(
                    type = NotificationType.PURCHASE, title = "ETH Purchase",
                    message = "Bought 0.0098 ETH for 30.00 EUR on Binance",
                    planId = ethPlanId, crypto = "ETH", exchange = Exchange.BINANCE,
                    isRead = false, createdAt = now.minus(Duration.ofHours(6))
                )
            )
            db.notificationDao().insert(
                NotificationEntity(
                    type = NotificationType.PURCHASE, title = "BTC Purchase",
                    message = "Bought 0.00079 BTC for 50.00 EUR on Coinmate",
                    planId = btcPlanId, crypto = "BTC", exchange = Exchange.COINMATE,
                    isRead = true, createdAt = now.minus(Duration.ofDays(1))
                )
            )
            db.notificationDao().insert(
                NotificationEntity(
                    type = NotificationType.WITHDRAWAL_THRESHOLD, title = "Withdrawal Ready",
                    message = "BTC balance on Coinmate reached withdrawal threshold",
                    planId = btcPlanId, crypto = "BTC", exchange = Exchange.COINMATE,
                    isRead = true, createdAt = now.minus(Duration.ofDays(3))
                )
            )
            db.notificationDao().insert(
                NotificationEntity(
                    type = NotificationType.LOW_BALANCE, title = "Low EUR Balance",
                    message = "EUR balance on Binance is below 50.00 EUR",
                    planId = ethPlanId, crypto = "ETH", exchange = Exchange.BINANCE,
                    isRead = true, createdAt = now.minus(Duration.ofDays(5))
                )
            )

            // Verify (inside runBlocking so we can use connectionIds + suspend hasCredentials)
            assert(OnboardingPreferences(context).isOnboardingCompleted()) { "Onboarding not completed" }
            assert(!UserPreferences(context).isSandboxMode()) { "Sandbox mode should be off" }
            assert(creds.hasCredentials(coinmateConnectionId, isSandbox = false)) { "Coinmate credentials missing" }
            assert(creds.hasCredentials(binanceConnectionId, isSandbox = false)) { "Binance credentials missing" }
        }
    }
}
