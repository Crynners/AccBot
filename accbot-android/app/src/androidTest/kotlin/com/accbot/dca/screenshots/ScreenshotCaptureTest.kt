package com.accbot.dca.screenshots

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.accbot.dca.MainActivity
import com.accbot.dca.R
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DailyPriceEntity
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.ExchangeBalanceEntity
import com.accbot.dca.data.local.NotificationEntity
import com.accbot.dca.data.local.NotificationType
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TransactionStatus
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.MethodSorters
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Instrumented test that captures Google Play screenshots for the current locale.
 *
 * The locale is set externally via `adb shell cmd locale set-app-locales`
 * before each test run. The test detects the active locale at runtime and
 * includes it in screenshot filenames.
 *
 * Produces 8 screenshots per run:
 *   00_welcome_{locale}  — Welcome/onboarding screen (clean install)
 *   01–07_*_{locale}     — Main app screens (with populated data)
 *
 * Run:
 * ```
 * cd accbot-android
 * adb shell cmd locale set-app-locales com.accbot.dca --locales "cs"  # or "" for EN
 * JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="$JAVA_HOME/bin:$PATH" \
 *   ./gradlew connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.accbot.dca.screenshots.ScreenshotCaptureTest
 * ```
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenshotCaptureTest {

    @get:Rule(order = 0)
    val permissionRule = object : TestWatcher() {
        override fun starting(description: Description) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("test_config", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("inject_market_pulse", true)
                .commit()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            // Grant notification permission and whitelist for battery optimization
            // to prevent system dialogs from blocking the Compose hierarchy
            device.executeShellCommand("pm grant com.accbot.dca android.permission.POST_NOTIFICATIONS")
            device.executeShellCommand("dumpsys deviceidle whitelist +com.accbot.dca")
        }
    }

    @get:Rule(order = 1)
    val dataSetupRule = object : TestWatcher() {
        override fun starting(description: Description) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            if (description.methodName.contains("Welcome")) {
                // Welcome: ensure clean onboarding state
                OnboardingPreferences(context).setOnboardingCompleted(false)
            } else {
                // Main screenshots: populate realistic data
                runBlocking { populateScreenshotData() }
            }
        }
    }

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val screenshotDir = File("/sdcard/Pictures/accbot-screenshots")

    /** Detect locale from the app's current configuration */
    private val locale: String
        get() = composeRule.activity.resources.configuration.locales[0].language

    // ── Test methods (alphabetically ordered) ──────────────────────────

    @Test fun a_captureWelcome() = captureWelcome()
    @Test fun b_captureAll() = captureAll()

    // ── Welcome screen ─────────────────────────────────────────────────

    private fun captureWelcome() {
        screenshotDir.mkdirs()
        dismissSystemDialogs()

        // Wait for Splash animation then Welcome screen to render
        composeRule.waitForIdle()
        Thread.sleep(8000)
        dismissSystemDialogs()

        // Verify Welcome screen is visible (wait for "Get Started" / "Začít" button)
        val getStartedText = composeRule.activity.getString(R.string.welcome_get_started)
        composeRule.waitUntil(timeoutMillis = 30000) {
            composeRule.onAllNodes(hasText(getStartedText)).fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(500)

        capture("00_welcome_$locale")
    }

    // ── Main app screenshots ───────────────────────────────────────────

    private fun captureAll() {
        screenshotDir.mkdirs()
        dismissSystemDialogs()

        composeRule.waitForIdle()
        Thread.sleep(8000)
        dismissSystemDialogs()

        // Wait for main navigation to appear (splash animation is slow on swiftshader emulators,
        // especially at high resolutions like the 10-inch Pixel C at 2560×1800).
        // Use text matching because NavigationRailItem (landscape) doesn't expose testTag
        // in the merged semantics tree, unlike NavigationBarItem (portrait).
        val dashboardLabel = composeRule.activity.getString(R.string.nav_dashboard)
        composeRule.waitUntil(timeoutMillis = 60000) {
            composeRule.onAllNodes(hasText(dashboardLabel)).fetchSemanticsNodes().isNotEmpty()
        }

        clickNav(R.string.nav_dashboard)
        composeRule.waitForIdle()
        Thread.sleep(3000)

        // 1. Dashboard — holdings pager, active plans, Market Pulse
        capture("01_dashboard_$locale")

        // 2. Portfolio — navigate to BTC/EUR page, Price line only
        clickNav(R.string.nav_portfolio)
        composeRule.waitForIdle()
        Thread.sleep(2000)

        // Navigate KPI pager to BTC/EUR: landscape uses arrow buttons, portrait uses swipe
        val nextLabel = composeRule.activity.getString(R.string.common_next)
        val nextArrows = composeRule.onAllNodes(hasContentDescription(nextLabel) and hasClickAction())
        try {
            nextArrows.onFirst().performClick()
            composeRule.waitForIdle()
            Thread.sleep(1000)
        } catch (e: AssertionError) {
            composeRule.onRoot().performTouchInput {
                val cardY = height * 0.18f
                down(Offset(width * 0.8f, cardY))
                moveTo(Offset(width * 0.2f, cardY), delayMillis = 300)
                up()
            }
            composeRule.waitForIdle()
            Thread.sleep(2000)
        }
        capture("02_portfolio_$locale")

        // 3. Portfolio with Avg Buy Price + Accum. BTC enabled
        val avgBuyLabel = composeRule.activity.getString(R.string.chart_avg_buy_price)
        val accumLabel = composeRule.activity.getString(R.string.chart_accumulated_crypto, "BTC")
        clickLegendChip(avgBuyLabel)
        composeRule.waitForIdle()
        clickLegendChip(accumLabel)
        composeRule.waitForIdle()
        Thread.sleep(2000)
        capture("03_portfolio_full_$locale")

        // 4. Notifications
        clickNav(R.string.nav_notifications)
        composeRule.waitForIdle()
        Thread.sleep(500)
        capture("04_notifications_$locale")

        // 5. Settings
        clickNav(R.string.nav_settings)
        composeRule.waitForIdle()
        Thread.sleep(500)
        capture("05_settings_$locale")

        // 6. Plan Details — navigate to Dashboard, tap BTC plan card
        clickNav(R.string.nav_dashboard)
        composeRule.waitForIdle()
        Thread.sleep(500)
        // On phone (portrait), Coinmate plan is below the fold in LazyColumn.
        // Swipe up to scroll dashboard content down and reveal it.
        try {
            composeRule.onNodeWithText("Coinmate").performScrollTo().performClick()
        } catch (_: AssertionError) {
            composeRule.onRoot().performTouchInput {
                down(Offset(width / 2f, height * 0.8f))
                moveTo(Offset(width / 2f, height * 0.2f), delayMillis = 300)
                up()
            }
            composeRule.waitForIdle()
            Thread.sleep(1000)
            composeRule.onNodeWithText("Coinmate").performClick()
        }
        composeRule.waitForIdle()
        Thread.sleep(3000)
        capture("06_plan_details_$locale")

        // 7. History — back via TopAppBar arrow (device.pressBack exits app on API 36)
        val backLabel = composeRule.activity.getString(R.string.common_back)
        composeRule.onNode(hasContentDescription(backLabel) and hasClickAction()).performClick()
        composeRule.waitForIdle()
        Thread.sleep(1000)
        val historyText = composeRule.activity.getString(R.string.dashboard_history)
        composeRule.onAllNodes(
            (hasText(historyText) or hasContentDescription(historyText)) and hasClickAction()
        ).onFirst().performScrollTo().performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        capture("07_history_$locale")
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Click a navigation item by its label string resource.
     *  Works for both NavigationBarItem (portrait) and NavigationRailItem (landscape). */
    private fun clickNav(@StringRes labelRes: Int) {
        val label = composeRule.activity.getString(labelRes)
        composeRule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
    }

    private fun clickLegendChip(label: String) {
        val node = composeRule.onAllNodes(hasText(label) and hasClickAction()).onLast()
        try {
            node.performScrollTo()
        } catch (_: Throwable) {
            // Landscape: legend is not inside a scrollable parent
        }
        node.performClick()
    }

    private fun dismissSystemDialogs() {
        val waitButton = device.findObject(UiSelector().textContains("Wait"))
        if (waitButton.exists()) {
            waitButton.click()
            Thread.sleep(1000)
        }
    }

    private fun capture(name: String) {
        dismissSystemDialogs()
        device.executeShellCommand("screencap -p /sdcard/Pictures/accbot-screenshots/$name.png")
        Thread.sleep(500)
    }

    // ── Data population ────────────────────────────────────────────────

    private suspend fun populateScreenshotData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = Instant.now()

        val onboarding = OnboardingPreferences(context)
        onboarding.setOnboardingCompleted(true)

        val prefs = UserPreferences(context)
        prefs.setSandboxMode(false)
        prefs.setBiometricLockEnabled(false)
        prefs.setLastSeenVersionCode(99999)
        prefs.setMarketPulseEnabled(true)
        prefs.setMarketPulseExpanded(true)

        val creds = CredentialsStore(context)
        creds.saveCredentials(
            ExchangeCredentials(Exchange.COINMATE, "demo_key", "demo_secret", clientId = "12345"),
            isSandbox = false
        )
        creds.saveCredentials(
            ExchangeCredentials(Exchange.BINANCE, "demo_key", "demo_secret"),
            isSandbox = false
        )

        val db = DcaDatabase.getInstance(context, isSandbox = false)

        db.dcaPlanDao().deleteAllPlans()
        db.transactionDao().deleteAllTransactions()
        db.dailyPriceDao().deleteAllPrices()
        db.exchangeBalanceDao().deleteAllBalances()
        db.notificationDao().deleteAllNotifications()

        val btcPlanId = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE, crypto = "BTC", fiat = "EUR",
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
                exchange = Exchange.BINANCE, crypto = "ETH", fiat = "EUR",
                amount = BigDecimal("30"), frequency = DcaFrequency.WEEKLY,
                strategy = DcaStrategy.FearAndGreed(), isEnabled = true,
                createdAt = now.minus(Duration.ofDays(120)),
                lastExecutedAt = now.minus(Duration.ofHours(6)),
                nextExecutionAt = now.plus(Duration.ofDays(5))
            )
        )

        val totalDays = HistoricalPrices.BTC_EUR.size

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

        val totalBtcAccumulated = btcTransactions.sumOf { it.cryptoAmount }
        val totalEthAccumulated = ethTransactions.sumOf { it.cryptoAmount }

        db.exchangeBalanceDao().insertBalances(
            listOf(
                ExchangeBalanceEntity("COINMATE_BTC", Exchange.COINMATE, "BTC", totalBtcAccumulated, now),
                ExchangeBalanceEntity("COINMATE_EUR", Exchange.COINMATE, "EUR", BigDecimal("142.50"), now),
                ExchangeBalanceEntity("BINANCE_ETH", Exchange.BINANCE, "ETH", totalEthAccumulated, now),
                ExchangeBalanceEntity("BINANCE_EUR", Exchange.BINANCE, "EUR", BigDecimal("85.00"), now),
            )
        )

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
    }
}
