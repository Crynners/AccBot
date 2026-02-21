package com.accbot.dca.recording

import androidx.test.platform.app.InstrumentationRegistry
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * One-time setup test that configures the emulator for the foreground service demo.
 *
 * Run ONCE before ForegroundServiceDemoTest:
 * ```
 * ./gradlew connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.accbot.dca.recording.EmulatorSetupTest
 * ```
 *
 * After running, force-stop the app so it restarts with sandbox mode active:
 * ```
 * adb shell am force-stop com.accbot.dca
 * ```
 */
class EmulatorSetupTest {

    @Test
    fun setupEmulatorForDemo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Cleanup: delete all existing DCA plans and transactions from sandbox DB
        val db = DcaDatabase.getInstance(context, isSandbox = true)
        runBlocking {
            db.dcaPlanDao().deleteAllPlans()
            db.transactionDao().deleteAllTransactions()
        }

        // 2. Mark onboarding as completed
        val onboarding = OnboardingPreferences(context)
        onboarding.setOnboardingCompleted(true)

        // 3. Enable sandbox mode
        val userPrefs = UserPreferences(context)
        userPrefs.setSandboxMode(true)
        userPrefs.setBiometricLockEnabled(false)

        // 4. Save Binance sandbox credentials
        val credentialsStore = CredentialsStore(context)
        val credentials = ExchangeCredentials(
            exchange = Exchange.BINANCE,
            apiKey = "EHF3PoIyxgXkJa1iUy7OsGPqtu7eSi6dis9O9QOBZL9SUXp16ThTyPHcIGc5ZidW",
            apiSecret = "pg6Xj5bBJUer1OnFsy6kanNK9YW6A5Xk6hsjp5AEMxEgum0Yqf7vbkpDg0MbZNHo"
        )
        val saved = credentialsStore.saveCredentials(credentials, isSandbox = true)
        assert(saved) { "Failed to save Binance sandbox credentials" }

        // DCA plan is NOT created here — it will be created via UI in ForegroundServiceDemoTest

        // Verify setup
        val hasCredentials = credentialsStore.hasCredentials(Exchange.BINANCE, isSandbox = true)
        assert(hasCredentials) { "Binance sandbox credentials not found after save" }
        assert(userPrefs.isSandboxMode()) { "Sandbox mode not enabled" }
        assert(onboarding.isOnboardingCompleted()) { "Onboarding not marked as completed" }
    }
}
