package com.accbot.dca.data.local

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.accbot.dca.domain.model.BackupEnvelope
import com.accbot.dca.domain.model.BackupExportOptions
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.domain.model.EncryptionMode
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.RestoreMode
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.BackupCryptoUseCase
import com.accbot.dca.domain.usecase.CreateBackupResult
import com.accbot.dca.domain.usecase.CreateBackupUseCase
import com.accbot.dca.domain.usecase.RestoreBackupResult
import com.accbot.dca.domain.usecase.RestoreBackupUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.testing.buildInMemoryDb
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BackupPlanRoundTripTest {
    private lateinit var db: DcaDatabase
    private lateinit var preferences: UserPreferences
    private lateinit var collector: BackupDataCollector
    private lateinit var exporter: CreateBackupUseCase
    private lateinit var importer: RestoreBackupUseCase
    private val gson = Gson()

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = UserPreferences(context)
        preferences.setSandboxMode(false)
        val credentials = CredentialsStore(context, db.exchangeConnectionDao())
        collector = BackupDataCollector(
            db.dcaPlanDao(), db.transactionDao(), db.notificationDao(), db.withdrawalDao(),
            db.withdrawalThresholdDao(), db.exchangeConnectionDao(), credentials, preferences
        )
        val restorer = BackupDataRestorer(
            db, db.dcaPlanDao(), db.transactionDao(), db.notificationDao(), db.withdrawalDao(),
            db.withdrawalThresholdDao(), db.exchangeConnectionDao(), credentials, preferences
        )
        val crypto = BackupCryptoUseCase(Bip39WordList(context))
        exporter = CreateBackupUseCase(collector, crypto, gson)
        importer = RestoreBackupUseCase(restorer, crypto, gson, preferences)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `backup preserves names and order for plans with identical trade settings`() = runTest {
        val connectionId = db.exchangeConnectionDao().insert(ExchangeConnectionEntity(exchange = Exchange.COINMATE))
        val first = plan(connectionId, "Dlouhodobé spoření", 4)
        val second = first.copy(name = "Železná rezerva", displayOrder = 1, createdAt = first.createdAt.plusSeconds(1))
        val firstId = db.dcaPlanDao().insertPlan(first)
        val secondId = db.dcaPlanDao().insertPlan(second)
        val purchase = TransactionEntity(
            planId = firstId, exchange = Exchange.COINMATE, connectionId = connectionId,
            crypto = "BTC", fiat = "CZK", fiatAmount = BigDecimal("150"),
            cryptoAmount = BigDecimal("0.0001"), price = BigDecimal("1500000"),
            fee = BigDecimal.ZERO, status = TransactionStatus.COMPLETED, exchangeOrderId = "first-order"
        )
        db.transactionDao().insertTransaction(purchase)
        db.transactionDao().insertTransaction(purchase.copy(planId = secondId, exchangeOrderId = "second-order"))
        preferences.setLanguageTag("")
        val password = "test passphrase"
        val backup = exporter.execute(BackupExportOptions(
            includeTransactions = true, encryptionMode = EncryptionMode.Password, password = password
        )) as CreateBackupResult.Success

        // Merge into a database that already contains one of the plans.
        db.transactionDao().deleteAllTransactions()
        db.dcaPlanDao().deletePlanById(secondId)
        preferences.setLanguageTag("en")
        val preview = importer.parseAndPreview(backup.envelopeJson, password) as RestoreBackupResult.PreviewReady
        val expected = listOf(second, first)
        repeat(2) {
            importer.restore(preview.payload, preview.preview.environment) as RestoreBackupResult.RestoreComplete
            val restored = db.dcaPlanDao().getAllPlansOnceOrdered()
            assertEquals(expected, restored.map { it.copy(id = 0) })
            assertEquals(firstId, restored.last().id)
            assertEquals(listOf("second-order"), db.transactionDao().getTransactionsByPlanSync(restored.first().id)
                .map { it.exchangeOrderId })
            assertEquals(listOf("first-order"), db.transactionDao().getTransactionsByPlanSync(firstId)
                .map { it.exchangeOrderId })
        }
        assertEquals("", preferences.getLanguageTag())
    }

    @Test
    fun `merge updates name order and sell settings for the same plan`() = runTest {
        val connectionId = db.exchangeConnectionDao().insert(ExchangeConnectionEntity(exchange = Exchange.COINMATE))
        val saved = plan(connectionId, "", 0).copy(allowSells = false, targetProfitAmount = null)
        val id = db.dcaPlanDao().insertPlan(saved)
        val backup = collector.collect(BackupExportOptions())
        db.dcaPlanDao().updatePlan(saved.copy(
            id = id, name = "Přejmenovaný plán", displayOrder = 5,
            allowSells = true, targetProfitAmount = BigDecimal("150")
        ))

        importer.restore(backup, "prod") as RestoreBackupResult.RestoreComplete

        assertEquals(listOf(saved.copy(id = id)), db.dcaPlanDao().getAllPlansOnce())
    }

    @Test
    fun `legacy backup without metadata preserves fields on a matching plan`() = runTest {
        val connectionId = db.exchangeConnectionDao().insert(ExchangeConnectionEntity(exchange = Exchange.COINMATE))
        val existing = plan(connectionId, "Rezerva", 2)
        val id = db.dcaPlanDao().insertPlan(existing)
        val backup = collector.collect(BackupExportOptions())
        val legacyPlan = backup.plans.single().copy(
            name = null, displayOrder = null, allowSells = null, targetProfitAmount = null,
            connectionId = null, createdAt = 0
        )
        val legacy = backup.copy(
            plans = listOf(legacyPlan), connections = emptyList(), settings = backup.settings?.copy(languageTag = null)
        )
        val envelope = BackupEnvelope(
            version = 1, encrypted = false, compressed = false,
            data = Base64.encodeToString(gson.toJson(legacy).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        )
        preferences.setLanguageTag("cs")
        val preview = importer.parseAndPreview(gson.toJson(envelope)) as RestoreBackupResult.PreviewReady

        importer.restore(preview.payload, preview.preview.environment) as RestoreBackupResult.RestoreComplete

        assertEquals(listOf(existing.copy(id = id)), db.dcaPlanDao().getAllPlansOnce())
        assertEquals("cs", preferences.getLanguageTag())
    }

    @Test
    fun `different environment is rejected both at preview and apply`() = runTest {
        val connectionId = db.exchangeConnectionDao().insert(ExchangeConnectionEntity(exchange = Exchange.COINMATE))
        val saved = plan(connectionId, "Rezerva", 2)
        db.dcaPlanDao().insertPlan(saved)
        val backup = exporter.execute(BackupExportOptions()) as CreateBackupResult.Success
        val preview = importer.parseAndPreview(backup.envelopeJson) as RestoreBackupResult.PreviewReady

        preferences.setSandboxMode(true)
        assertEquals(RestoreBackupResult.Error(RestoreBackupUseCase.ENVIRONMENT_ERROR),
            importer.parseAndPreview(backup.envelopeJson))
        assertEquals(RestoreBackupResult.Error(RestoreBackupUseCase.ENVIRONMENT_ERROR),
            importer.restore(preview.payload, preview.preview.environment, RestoreMode.Replace))
        assertEquals(saved, db.dcaPlanDao().getAllPlansOnce().single().copy(id = 0))
    }

    @Test
    fun `restore pauses active plans and replace preserves unresolved purchases`() = runTest {
        val connectionId = db.exchangeConnectionDao().insert(ExchangeConnectionEntity(exchange = Exchange.COINMATE))
        val saved = plan(connectionId, "Rezerva", 2).copy(isEnabled = true)
        val planId = db.dcaPlanDao().insertPlan(saved)
        val backup = collector.collect(BackupExportOptions())
        val purchases = db.transactionDao()
        val purchaseId = purchases.insertTransaction(TransactionEntity(
            planId = planId, exchange = Exchange.COINMATE, connectionId = connectionId,
            crypto = "BTC", fiat = "CZK", fiatAmount = saved.amount,
            cryptoAmount = BigDecimal.ZERO, price = BigDecimal.ZERO, fee = BigDecimal.ZERO,
            status = TransactionStatus.PENDING
        ))

        assertEquals(RestoreBackupResult.Error("backup_open_orders"),
            importer.restore(backup, "prod", RestoreMode.Replace))
        purchases.deleteClosedTransaction(purchaseId)
        assertTrue(purchases.hasUnidentifiedBuy(planId))
        purchases.acknowledgeUnidentifiedBuy(purchaseId)
        assertFalse(purchases.hasUnidentifiedBuy(planId))
        assertEquals(TransactionStatus.CANCELLED, purchases.getTransactionById(purchaseId)?.status)

        val result = importer.restore(backup, "prod", RestoreMode.Replace)
        assertTrue(result.toString(), result is RestoreBackupResult.RestoreComplete)
        assertFalse(db.dcaPlanDao().getAllPlansOnce().single().isEnabled)
    }

    @Test
    fun `private trading waits for the correct environment and refuses unsupported sandbox exchanges`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences.setSandboxMode(true)
        val sandboxPreferences = UserPreferences(context)
        val client = OkHttpClient.Builder().addInterceptor { error("Unexpected exchange request") }.build()
        val factory = ExchangeApiFactory(sandboxPreferences, client)
        val credentials = ExchangeCredentials(Exchange.COINMATE, "local-test-key", "local-test-secret")

        val sandboxResult = factory.create(credentials).marketBuy("BTC", "EUR", BigDecimal.TEN)
        assertTrue(sandboxResult is DcaResult.Error && sandboxResult.confirmedRejection)

        sandboxPreferences.setSandboxMode(false)
        val switchingResult = factory.create(credentials).marketBuy("BTC", "EUR", BigDecimal.TEN)
        assertTrue(switchingResult is DcaResult.Error && switchingResult.confirmedRejection)
    }

    private fun plan(connectionId: Long, name: String, displayOrder: Int) = DcaPlanEntity(
        exchange = Exchange.COINMATE,
        connectionId = connectionId,
        name = name,
        crypto = "BTC",
        fiat = "CZK",
        amount = BigDecimal("150"),
        frequency = DcaFrequency.WEEKLY,
        isEnabled = false,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        nextExecutionAt = Instant.parse("2099-01-01T00:00:00Z"),
        targetAmount = BigDecimal("0.01"),
        displayOrder = displayOrder,
        allowSells = true,
        targetProfitAmount = BigDecimal("125")
    )
}
