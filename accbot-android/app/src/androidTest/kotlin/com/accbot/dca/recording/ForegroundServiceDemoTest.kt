package com.accbot.dca.recording

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.accbot.dca.MainActivity
import com.accbot.dca.data.local.DcaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Instrumented UI test that records the complete DCA foreground service flow
 * as video evidence for Google Play's FOREGROUND_SERVICE_DATA_SYNC requirement.
 *
 * The video demonstrates:
 * 1. Creating a DCA plan with "Every 15 minutes" scheduling (regular data sync)
 * 2. Running the plan immediately to show foreground service in action
 * 3. Viewing the completed transaction in History
 *
 * **Pre-conditions (set up via EmulatorSetupTest):**
 * - App installed with onboarding completed
 * - Sandbox mode enabled with Binance credentials configured
 * - No existing DCA plans (clean state)
 * - Biometric lock disabled
 *
 * **Run:**
 * ```
 * cd accbot-android
 * ./gradlew connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.accbot.dca.recording.ForegroundServiceDemoTest
 * ```
 *
 * **Pull video:**
 * ```
 * adb pull /sdcard/Movies/foreground_service_data_sync_demo.mp4
 * ```
 */
class ForegroundServiceDemoTest {

    @get:Rule(order = 0)
    val screenRecordRule = ScreenRecordRule()

    /**
     * Grants runtime permissions and battery optimization exemption BEFORE the Activity launches.
     * This prevents system dialogs from covering the compose hierarchy during the test.
     */
    @get:Rule(order = 1)
    val permissionRule = object : TestWatcher() {
        override fun starting(description: Description) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.executeShellCommand("pm grant com.accbot.dca android.permission.POST_NOTIFICATIONS")
            device.executeShellCommand("dumpsys deviceidle whitelist +com.accbot.dca")
            Thread.sleep(500)
        }
    }

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun foreground_service_data_sync_demo() {
        // 1. Dashboard loads — empty, no DCA plans
        composeTestRule.waitForIdle()
        Thread.sleep(5_000)

        // 2. Open notification shade — show "AccBot DCA Active" foreground service notification
        device.openNotification()
        Thread.sleep(4_000)

        // 3. Close notification shade — back to Dashboard
        device.pressBack()
        Thread.sleep(2_000)

        // 4. Tap "+" next to "My DCA Plans" → navigate to AddPlanScreen
        composeTestRule.onNodeWithContentDescription("+")
            .assertIsDisplayed()
            .performClick()
        Thread.sleep(2_000)

        // 5. Select Binance exchange
        composeTestRule.onNodeWithText("Binance")
            .performClick()
        Thread.sleep(2_000)

        // 6. Select BTC cryptocurrency
        composeTestRule.onNodeWithText("BTC")
            .performScrollTo()
            .performClick()
        Thread.sleep(2_000)

        // 7. Select USDT fiat currency (switch from default EUR)
        composeTestRule.onNodeWithText("USDT")
            .performScrollTo()
            .performClick()
        Thread.sleep(2_000)

        // 8. Enter amount "10" (replace default "100")
        composeTestRule.onNode(hasSetTextAction() and hasText("100"))
            .performScrollTo()
            .performTextReplacement("10")
        Thread.sleep(2_000)

        // 9. Open frequency dropdown (currently shows "Daily")
        composeTestRule.onNodeWithText("Daily")
            .performScrollTo()
            .performClick()
        Thread.sleep(2_000)

        // 10. Select "Every 15 minutes" — key moment: demonstrates regular scheduling
        composeTestRule.onNodeWithText("Every 15 minutes")
            .performClick()
        Thread.sleep(2_000)

        // 11. Tap "Create DCA Plan" button → navigates back to Dashboard
        composeTestRule.onNode(hasText("Create DCA Plan") and hasClickAction())
            .performScrollTo()
            .performClick()
        Thread.sleep(3_000)

        // 12. Dashboard with the new plan visible
        composeTestRule.waitForIdle()
        Thread.sleep(3_000)

        // 13. Tap "Run Now" button on Dashboard
        composeTestRule.onNodeWithText("Run Now")
            .assertIsDisplayed()
            .performClick()
        Thread.sleep(2_000)

        // 14. Confirm in the Run Now bottom sheet
        composeTestRule.onNodeWithText("plan(s)", substring = true)
            .performClick()
        Thread.sleep(3_000)

        // 15. Open notification shade DURING DCA execution — show foreground service
        //     actively performing data sync with exchange API in the background
        device.openNotification()
        Thread.sleep(5_000)

        // 16. Close shade — let the worker continue in background
        device.pressBack()
        Thread.sleep(15_000)

        // 17. Open notification shade — show "DCA Purchase Completed" result
        device.openNotification()
        Thread.sleep(5_000)

        // 18. Close notification shade
        device.pressBack()
        Thread.sleep(2_000)

        // 19. Navigate to History — show the completed transaction
        composeTestRule.onNodeWithText("History")
            .assertIsDisplayed()
            .performClick()
        Thread.sleep(5_000)

        // Cleanup: delete all DCA plans and transactions so repeated runs start clean
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = DcaDatabase.getInstance(context, isSandbox = true)
        runBlocking {
            db.dcaPlanDao().deleteAllPlans()
            db.transactionDao().deleteAllTransactions()
        }

        // Recording stops automatically via ScreenRecordRule when the test finishes
    }
}
