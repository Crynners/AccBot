package com.accbot.dca.screenshots

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import com.accbot.dca.data.local.ExchangeConnectionEntity
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanTrade
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
 * Captures the screens shown in the stakeholder release notes (UX update, read-only
 * sharing). Not part of the regular test suite; run it on an emulator after setting the
 * app locale, without reinstalling in between:
 * ```
 * ./gradlew installDebug installDebugAndroidTest
 * adb shell cmd locale set-app-locales com.accbot.dca --locales cs
 * # blocked notifications (user-fixed, so no dialog) make the automation warning show:
 * adb shell pm revoke com.accbot.dca android.permission.POST_NOTIFICATIONS
 * adb shell pm set-permission-flags com.accbot.dca android.permission.POST_NOTIFICATIONS user-fixed
 * adb shell am instrument -w -e class com.accbot.dca.screenshots.ReleaseNotesScreenshotTest \
 *   com.accbot.dca.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull //sdcard/Pictures/accbot-release-notes
 * ```
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReleaseNotesScreenshotTest {

    @get:Rule(order = 0)
    val permissionRule = object : TestWatcher() {
        override fun starting(description: Description) {
            // Notifications are blocked from the host beforehand (revoking here kills the process).
            device.executeShellCommand("dumpsys deviceidle whitelist +com.accbot.dca")
        }
    }

    @get:Rule(order = 1)
    val dataSetupRule = object : TestWatcher() {
        override fun starting(description: Description) {
            runBlocking {
                if (description.methodName.contains("Guide")) populateFirstPlan() else populateMain()
            }
        }
    }

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val outDir = File("/sdcard/Pictures/accbot-release-notes")

    @Test
    fun a_mainScreens() {
        waitForMain()

        // Dashboard: "Needs attention" + plan cards
        capture("01_dashboard_attention")
        swipeUntil("Coinmate")
        composeRule.onRoot().performTouchInput {
            swipeUp(startY = height * 0.62f, endY = height * 0.48f, durationMillis = 400)
        }
        settle(1500)
        capture("02_dashboard_plans")

        // Review of an uncertain purchase
        scrollToTop()
        click(R.string.attention_review_action)
        settle(2000)
        capture("03_transaction_review")
        click(R.string.transaction_review_purchase)
        settle(1000)
        capture("04_review_dialog")
        device.pressBack()
        settle(800)
        back()

        // Plan details with the sticky action bar, then the simpler sell wizard
        scrollToTop()
        swipeUntil("Coinmate")
        composeRule.onAllNodes(hasText("Coinmate")).onFirst().performClick()
        settle(3500)
        capture("05_plan_details")
        click(R.string.plan_details_sell)
        settle(4000)
        waitFor(text(R.string.sell_wizard_amount))
        capture("06_sell_basic")
        val advanced = composeRule.onAllNodes(hasText(text(R.string.sell_wizard_advanced))).onFirst()
        runCatching { advanced.performScrollTo() }
        advanced.performClick()
        settle(1500)
        capture("07_sell_advanced")
        device.pressBack()
        settle(1000)
        back()

        // History with a readable error, then its detail
        val historyText = text(R.string.dashboard_history)
        swipeUntil(historyText)
        composeRule.onAllNodes(hasText(historyText) and hasClickAction()).onFirst().performClick()
        settle(1500)
        waitFor(text(R.string.error_network_title))
        capture("08_history_errors")
        composeRule.onAllNodes(hasText(text(R.string.error_network_title))).onFirst().performClick()
        settle(1500)
        capture("09_friendly_error")
        back()
        back()

        // Settings in groups with the automation status
        clickNav(R.string.nav_settings)
        settle(1500)
        capture("10_settings")

        // Read-only shared plan
        swipeUntil(text(R.string.shared_plans_title))
        composeRule.onAllNodes(hasText(text(R.string.shared_plans_title))).onFirst().performClick()
        settle(1500)
        capture("11_shared_list")
        composeRule.onAllNodes(hasText(SHARED_NAME)).onFirst().performClick()
        settle(2500)
        capture("12_shared_detail")
    }

    @Test
    fun b_firstPlanGuide() {
        waitForMain()
        capture("13_first_plan_guide")
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun text(@StringRes id: Int) = composeRule.activity.getString(id)

    private fun waitForMain() {
        outDir.mkdirs()
        composeRule.waitForIdle()
        Thread.sleep(6000)
        dismissSystemDialogs()
        val dashboard = text(R.string.nav_dashboard)
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodes(hasText(dashboard)).fetchSemanticsNodes().isNotEmpty()
        }
        clickNav(R.string.nav_dashboard)
        settle(4000)
    }

    private fun settle(ms: Long) {
        composeRule.waitForIdle()
        Thread.sleep(ms)
    }

    private fun click(@StringRes id: Int) {
        val node = composeRule.onAllNodes(hasText(text(id)) and hasClickAction()).onFirst()
        runCatching { node.performScrollTo() } // not every button sits in a scrollable container
        node.performClick()
    }

    private fun clickNav(@StringRes id: Int) {
        composeRule.onAllNodes(hasText(text(id)) and hasClickAction()).onFirst().performClick()
    }

    private fun back() {
        composeRule.onAllNodes(hasContentDescription(text(R.string.common_back)) and hasClickAction())
            .onFirst().performClick()
        settle(1200)
    }

    private fun waitFor(value: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) { exists(value) }
        settle(1500)
    }

    private fun exists(value: String) =
        composeRule.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty()

    private fun swipeUp() {
        composeRule.onRoot().performTouchInput {
            swipeUp(startY = height * 0.72f, endY = height * 0.38f, durationMillis = 400)
        }
        settle(700)
    }

    /** Lazy lists only compose visible items, so swipe until the text exists, then a bit further. */
    private fun swipeUntil(value: String) {
        var tries = 0
        while (!exists(value) && tries++ < 12) swipeUp()
        runCatching { composeRule.onAllNodes(hasText(value)).onFirst().performScrollTo() }
        settle(500)
    }

    private fun scrollToTop() {
        repeat(3) {
            composeRule.onRoot().performTouchInput {
                down(Offset(width / 2f, height * 0.25f))
                moveTo(Offset(width / 2f, height * 0.9f), delayMillis = 200)
                up()
            }
        }
        settle(800)
    }

    private fun dismissSystemDialogs() {
        val wait = device.findObject(UiSelector().textContains("Wait"))
        if (wait.exists()) wait.click()
    }

    private fun capture(name: String) {
        dismissSystemDialogs()
        device.executeShellCommand("screencap -p ${outDir.absolutePath}/$name.png")
        Thread.sleep(400)
    }

    // ── data ────────────────────────────────────────────────────────────

    private suspend fun resetCommon(): Pair<DcaDatabase, CredentialsStore> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OnboardingPreferences(context).setOnboardingCompleted(true)
        UserPreferences(context).apply {
            setSandboxMode(false)
            setBiometricLockEnabled(false)
            setLastSeenVersionCode(99999)
            setMarketPulseEnabled(false)
            setTradingEnabled(true)
            setLanguageTag("cs")
            setDismissedAutomationIssues(emptySet())
        }
        val db = DcaDatabase.getInstance(context, isSandbox = false)
        db.dcaPlanDao().deleteAllPlans()
        db.transactionDao().deleteAllTransactions()
        db.dailyPriceDao().deleteAllPrices()
        db.exchangeBalanceDao().deleteAllBalances()
        db.notificationDao().deleteAllNotifications()
        db.openHelper.writableDatabase.execSQL("DELETE FROM exchange_connections")
        return db to CredentialsStore(context, db.exchangeConnectionDao())
    }

    private suspend fun connection(db: DcaDatabase, creds: CredentialsStore, exchange: Exchange): Long {
        val id = db.exchangeConnectionDao().insert(ExchangeConnectionEntity(exchange = exchange, name = ""))
        creds.saveCredentials(
            connectionId = id,
            credentials = ExchangeCredentials(exchange, "demo_key", "demo_secret", clientId = "12345"),
            isSandbox = false
        )
        return id
    }

    private suspend fun populateMain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (db, creds) = resetCommon()
        UserPreferences(context).setFirstPlanGuideDismissed(true)
        val now = Instant.now()
        val coinmate = connection(db, creds, Exchange.COINMATE)
        val binance = connection(db, creds, Exchange.BINANCE)

        val btcPlan = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE, connectionId = coinmate, crypto = "BTC", fiat = "EUR",
                amount = BigDecimal("50"), frequency = DcaFrequency.DAILY, strategy = DcaStrategy.Classic,
                isEnabled = true, allowSells = true, createdAt = now.minus(Duration.ofDays(180)),
                lastExecutedAt = now.minus(Duration.ofHours(20)), nextExecutionAt = now.plus(Duration.ofHours(4))
            )
        )
        val ethPlan = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.BINANCE, connectionId = binance, crypto = "ETH", fiat = "EUR",
                amount = BigDecimal("30"), frequency = DcaFrequency.WEEKLY, strategy = DcaStrategy.FearAndGreed(),
                isEnabled = true, createdAt = now.minus(Duration.ofDays(120)),
                lastExecutedAt = now.minus(Duration.ofHours(3)), nextExecutionAt = now.plus(Duration.ofDays(7))
            )
        )

        val days = HistoricalPrices.BTC_EUR.size
        db.dailyPriceDao().insertPrices(HistoricalPrices.BTC_EUR.mapIndexed { i, p ->
            DailyPriceEntity("BTC", "EUR", LocalDate.now().minusDays((days - 1 - i).toLong()).toEpochDay(),
                BigDecimal(p).setScale(2, RoundingMode.HALF_UP))
        })
        db.dailyPriceDao().insertPrices(HistoricalPrices.ETH_EUR.mapIndexed { i, p ->
            DailyPriceEntity("ETH", "EUR", LocalDate.now().minusDays((days - 1 - i).toLong()).toEpochDay(),
                BigDecimal(p).setScale(2, RoundingMode.HALF_UP))
        })

        val btcTx = (1..120).map { d ->
            val price = HistoricalPrices.BTC_EUR[(days - 1 - d).coerceIn(0, days - 1)]
            TransactionEntity(
                planId = btcPlan, exchange = Exchange.COINMATE, connectionId = coinmate, crypto = "BTC", fiat = "EUR",
                fiatAmount = BigDecimal("50.00"),
                cryptoAmount = BigDecimal(50.0 / price).setScale(8, RoundingMode.HALF_UP),
                price = BigDecimal(price).setScale(2, RoundingMode.HALF_UP), fee = BigDecimal("0.13"), feeAsset = "EUR",
                status = TransactionStatus.COMPLETED, exchangeOrderId = "CM-$d",
                executedAt = now.minus(Duration.ofDays(d.toLong())).minus(Duration.ofHours(20))
            )
        }
        db.transactionDao().insertTransactions(btcTx)
        val ethTx = (1..16).map { w ->
            val d = w * 7
            val price = HistoricalPrices.ETH_EUR[(days - 1 - d).coerceIn(0, days - 1)]
            TransactionEntity(
                planId = ethPlan, exchange = Exchange.BINANCE, connectionId = binance, crypto = "ETH", fiat = "EUR",
                fiatAmount = BigDecimal("30.00"),
                cryptoAmount = BigDecimal(30.0 / price).setScale(8, RoundingMode.HALF_UP),
                price = BigDecimal(price).setScale(2, RoundingMode.HALF_UP), fee = BigDecimal("0.08"), feeAsset = "EUR",
                status = TransactionStatus.COMPLETED, exchangeOrderId = "BN-$w",
                executedAt = now.minus(Duration.ofDays(d.toLong()))
            )
        }
        db.transactionDao().insertTransactions(ethTx)

        // A purchase whose outcome the exchange never confirmed (blocks the ETH plan).
        db.transactionDao().insertTransactions(listOf(
            TransactionEntity(
                planId = ethPlan, exchange = Exchange.BINANCE, connectionId = binance, crypto = "ETH", fiat = "EUR",
                fiatAmount = BigDecimal("30.00"), cryptoAmount = BigDecimal.ZERO, price = BigDecimal("3120.00"),
                fee = BigDecimal.ZERO, status = TransactionStatus.PENDING, side = TransactionSide.BUY,
                executedAt = now.minus(Duration.ofHours(3))
            ),
            // A failed purchase with a raw transport error.
            TransactionEntity(
                planId = btcPlan, exchange = Exchange.COINMATE, connectionId = coinmate, crypto = "BTC", fiat = "EUR",
                fiatAmount = BigDecimal("50.00"), cryptoAmount = BigDecimal.ZERO, price = BigDecimal("98500.00"),
                fee = BigDecimal.ZERO, status = TransactionStatus.FAILED,
                errorMessage = "Unable to resolve host \"coinmate.io\": No address associated with hostname",
                executedAt = now.minus(Duration.ofMinutes(40))
            )
        ))

        val btcHeld = btcTx.sumOf { it.cryptoAmount }
        db.exchangeBalanceDao().insertBalances(listOf(
            ExchangeBalanceEntity(coinmate, "BTC", Exchange.COINMATE, btcHeld, now),
            ExchangeBalanceEntity(coinmate, "EUR", Exchange.COINMATE, BigDecimal("640.00"), now),
            ExchangeBalanceEntity(binance, "ETH", Exchange.BINANCE, ethTx.sumOf { it.cryptoAmount }, now),
            ExchangeBalanceEntity(binance, "EUR", Exchange.BINANCE, BigDecimal("210.00"), now),
        ))

        // Read-only plan shared by someone else.
        val store = SharedPlanStore(context)
        store.refresh().forEach { store.delete(it.shareId) }
        store.save(
            SharedPlan(
                shareId = "3f6c2a1e-8b4d-4c7a-9e21-5d0b7f9a1c34", sharedAt = now.minus(Duration.ofDays(1)), isSandbox = false,
                includesAmounts = true, name = SHARED_NAME, exchange = Exchange.COINMATE, exchangeName = "Coinmate",
                crypto = "BTC", fiat = "EUR", amount = BigDecimal("25"), frequency = DcaFrequency.WEEKLY,
                cronExpression = null, strategy = DcaStrategy.Classic, isEnabled = true,
                createdAt = now.minus(Duration.ofDays(150)), statedAvgBuyPrice = null,
                liveUpdates = true, verifiedReadOnly = true, refreshedAt = now.minus(Duration.ofMinutes(12)),
                trades = (21 downTo 1).map { w ->
                    val d = w * 7
                    val price = HistoricalPrices.BTC_EUR[(days - 1 - d).coerceIn(0, days - 1)]
                    SharedPlanTrade(
                        executedAt = now.minus(Duration.ofDays(d.toLong())), side = TransactionSide.BUY,
                        price = BigDecimal(price).setScale(2, RoundingMode.HALF_UP),
                        cryptoAmount = BigDecimal(25.0 / price).setScale(8, RoundingMode.HALF_UP),
                        fiatAmount = BigDecimal("25.00"), fee = BigDecimal("0.06"), feeAsset = "EUR"
                    )
                }
            ),
            // A key for an exchange without trade import: the detail then shows the stored live
            // state instead of trying (and failing) to refresh with demo credentials.
            credentials = ExchangeCredentials(Exchange.HUOBI, "demo_ro_key", "demo_ro_secret")
        )
    }

    private suspend fun populateFirstPlan() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (db, creds) = resetCommon()
        UserPreferences(context).setFirstPlanGuideDismissed(false)
        val now = Instant.now()
        val coinmate = connection(db, creds, Exchange.COINMATE)
        db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE, connectionId = coinmate, crypto = "BTC", fiat = "EUR",
                amount = BigDecimal("50"), frequency = DcaFrequency.DAILY, strategy = DcaStrategy.Classic,
                isEnabled = true, createdAt = now.minus(Duration.ofMinutes(5)),
                nextExecutionAt = now.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(10))
            )
        )
        db.exchangeBalanceDao().insertBalances(listOf(
            ExchangeBalanceEntity(coinmate, "EUR", Exchange.COINMATE, BigDecimal("30.00"), now)
        ))
    }

    private companion object {
        const val SHARED_NAME = "Tátův spořicí plán"
    }
}
