package com.accbot.dca.domain.share

import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.domain.usecase.RefreshSharedPlanUseCase
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.time.Instant

class SharedPlanCodecTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val shareId = "3f2b8c1e-5d4a-4f6b-9c2d-7e8f9a0b1c2d"
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")

    private val plan = DcaPlanEntity(
        id = 7,
        exchange = Exchange.COINMATE,
        connectionId = 3,
        name = "Spoření",
        crypto = "BTC",
        fiat = "CZK",
        amount = BigDecimal("500"),
        frequency = DcaFrequency.DAILY,
        strategy = DcaStrategy.FearAndGreed(),
        withdrawalEnabled = true,
        withdrawalAddress = "bc1qsecretaddress",
        createdAt = t0
    )

    private fun tx(
        id: Long,
        side: TransactionSide,
        crypto: String,
        fiat: String,
        status: TransactionStatus = TransactionStatus.COMPLETED,
        day: Long = id
    ) = TransactionEntity(
        id = id,
        planId = 7,
        connectionId = 3,
        exchange = Exchange.COINMATE,
        crypto = "BTC",
        fiat = "CZK",
        fiatAmount = BigDecimal(fiat),
        cryptoAmount = BigDecimal(crypto),
        price = if (BigDecimal(crypto).signum() == 0) BigDecimal.ZERO
            else BigDecimal(fiat).divide(BigDecimal(crypto), 2, java.math.RoundingMode.HALF_UP),
        fee = BigDecimal("2.5"),
        feeAsset = "CZK",
        status = status,
        exchangeOrderId = "secret-order-$id",
        executedAt = t0.plusSeconds(day * 86_400),
        side = side
    )

    private val transactions = listOf(
        tx(1, TransactionSide.BUY, "0.0004", "500"),
        tx(2, TransactionSide.BUY, "0.0005", "500"),
        tx(3, TransactionSide.BUY, "0.0005", "500", status = TransactionStatus.FAILED),
        tx(4, TransactionSide.BUY, "0", "500", status = TransactionStatus.PENDING),
        tx(5, TransactionSide.SELL, "0.0002", "300", status = TransactionStatus.PARTIAL_CANCELLED)
    )

    @Test
    fun `round trip with amounts keeps only confirmed fills`() {
        val json = SharedPlanCodec.encode(
            SharedPlanCodec.createSnapshot(plan, transactions, shareId, isSandbox = false, includeAmounts = true)
        )
        val decoded = SharedPlanCodec.decode(json)

        assertEquals(shareId, decoded.shareId)
        assertEquals("Spoření", decoded.name)
        assertEquals(Exchange.COINMATE, decoded.exchange)
        assertEquals(DcaFrequency.DAILY, decoded.frequency)
        assertTrue(decoded.strategy is DcaStrategy.FearAndGreed)
        assertEquals(0, BigDecimal("500").compareTo(decoded.amount))
        assertEquals(listOf(TransactionSide.BUY, TransactionSide.BUY, TransactionSide.SELL), decoded.trades.map { it.side })

        val summary = decoded.summary
        assertEquals(2, summary.buyCount)
        assertEquals(1, summary.sellCount)
        assertEquals(0, BigDecimal("1000").compareTo(summary.totalInvested))
        assertEquals(0, BigDecimal("0.0007").compareTo(summary.heldCrypto))
        assertEquals(0, BigDecimal("1111111.11111111").compareTo(summary.avgBuyPrice))
    }

    @Test
    fun `snapshot never contains credentials, order ids or withdrawal address`() {
        val json = SharedPlanCodec.encode(
            SharedPlanCodec.createSnapshot(plan, transactions, shareId, isSandbox = false, includeAmounts = true)
        )
        assertFalse(json.contains("secret-order"))
        assertFalse(json.contains("bc1qsecretaddress"))
        assertFalse(json.contains("connectionId"))
    }

    @Test
    fun `hidden amounts share only prices and stated average`() {
        val json = SharedPlanCodec.encode(
            SharedPlanCodec.createSnapshot(plan, transactions, shareId, isSandbox = true, includeAmounts = false)
        )
        assertFalse(json.contains("\"cryptoAmount\""))
        assertFalse(json.contains("\"amount\""))

        val decoded = SharedPlanCodec.decode(json)
        assertTrue(decoded.isSandbox)
        assertNull(decoded.amount)
        assertTrue(decoded.trades.all { it.cryptoAmount == null && it.fiatAmount == null })
        val summary = decoded.summary
        assertNull(summary.totalInvested)
        assertNull(summary.heldCrypto)
        assertEquals(0, BigDecimal("1111111.11111111").compareTo(summary.avgBuyPrice))
        assertEquals(0, BigDecimal("80.00").compareTo(summary.performancePercent(BigDecimal("2000000"))))
    }

    @Test
    fun `rejects other files and future versions`() {
        assertReason(SharedPlanError.NOT_A_PLAN_SHARE, """{"format":"accbot-backup","version":1}""")
        assertReason(SharedPlanError.NOT_A_PLAN_SHARE, "not json")
        assertReason(SharedPlanError.UNSUPPORTED_VERSION, """{"format":"accbot-plan-share","version":99}""")
    }

    @Test
    fun `rejects unsafe share id and malformed data`() {
        val valid = SharedPlanCodec.encode(
            SharedPlanCodec.createSnapshot(plan, transactions, shareId, isSandbox = false, includeAmounts = true)
        )
        assertReason(SharedPlanError.INVALID, valid.replace(shareId, "../../databases/accbot"))
        assertReason(SharedPlanError.INVALID, valid.replace("\"BUY\"", "\"STEAL\""))
        assertReason(SharedPlanError.INVALID, valid.replace("\"crypto\": \"BTC\"", "\"crypto\": \"<b>\""))
    }

    @Test
    fun `store replaces older snapshot and ignores outdated one`() = runTest {
        val vault = FakeVault()
        val store = SharedPlanStore(tmp.newFolder("shared"), object : SharedPlanStore.ShareIdRegistry {
            override fun shareIdFor(planId: Long, isSandbox: Boolean) = shareId
        }, vault)
        fun snapshotAt(seconds: Long) = SharedPlanCodec.decode(
            SharedPlanCodec.encode(
                SharedPlanCodec.createSnapshot(
                    plan, transactions, shareId, isSandbox = false, includeAmounts = false,
                    now = t0.plusSeconds(seconds)
                )
            )
        )

        val first = store.save(snapshotAt(100))
        assertTrue(first is SharedPlanStore.SaveResult.Saved && !first.replaced)
        val second = store.save(snapshotAt(200))
        assertTrue(second is SharedPlanStore.SaveResult.Saved && second.replaced)
        val outdated = store.save(snapshotAt(150))
        assertTrue(outdated is SharedPlanStore.SaveResult.Outdated)

        val stored = store.refresh()
        assertEquals(1, stored.size)
        assertEquals(t0.plusSeconds(200), stored.single().sharedAt)

        // A live share stores its key; a later plain share removes it again.
        val key = ExchangeCredentials(Exchange.COINMATE, "read", "only", clientId = "1")
        store.save(snapshotAt(300), key)
        assertEquals(key, store.credentials(shareId))
        assertTrue(store.refresh().single().liveUpdates)
        store.save(snapshotAt(400))
        assertNull(store.credentials(shareId))
        assertFalse(store.refresh().single().liveUpdates)

        store.save(snapshotAt(500), key)
        store.delete(shareId)
        assertTrue(store.refresh().isEmpty())
        assertNull(vault.get(shareId))
    }

    @Test
    fun `live share is encrypted and carries the read-only key`() {
        val snapshot = SharedPlanCodec.createSnapshot(plan, transactions, shareId, isSandbox = false, includeAmounts = true)
        val access = SharedPlanAccessDto(
            exchange = "COINMATE", apiKey = "ro-key", apiSecret = "ro-secret", clientId = "42", verifiedReadOnly = false
        )
        val file = SharedPlanCodec.encodeShare(snapshot, access, "correct horse", ::fakeEncrypt)
        assertFalse(file.contains("ro-secret"))
        assertFalse(file.contains("Spoření"))

        assertReason(SharedPlanError.PASSWORD_REQUIRED) { SharedPlanCodec.decodeShare(file, null, ::fakeDecrypt) }
        assertReason(SharedPlanError.WRONG_PASSWORD) { SharedPlanCodec.decodeShare(file, "nope", ::fakeDecrypt) }

        val decoded = SharedPlanCodec.decodeShare(file, "correct horse", ::fakeDecrypt)
        assertEquals("ro-key", decoded.credentials?.apiKey)
        assertEquals("42", decoded.credentials?.clientId)
        assertTrue(decoded.plan.liveUpdates)
        assertFalse(decoded.plan.verifiedReadOnly)

        // Stored copies never contain the key.
        val stored = SharedPlanCodec.encode(decoded.plan)
        assertFalse(stored.contains("ro-key"))
        assertTrue(SharedPlanCodec.decode(stored).liveUpdates)
        assertFalse(decoded.plan.fullAccessKey)
    }

    @Test
    fun `plan key share is marked as full access and keeps the mark in storage`() {
        val snapshot = SharedPlanCodec.createSnapshot(plan, transactions, shareId, isSandbox = false, includeAmounts = true)
        val access = SharedPlanAccessDto(
            exchange = "COINMATE", apiKey = "plan-key", apiSecret = "plan-secret", clientId = "42",
            verifiedReadOnly = false, fullAccess = true
        )
        val file = SharedPlanCodec.encodeShare(snapshot, access, "correct horse", ::fakeEncrypt)
        assertFalse(file.contains("plan-secret"))

        val decoded = SharedPlanCodec.decodeShare(file, "correct horse", ::fakeDecrypt)
        assertTrue(decoded.plan.liveUpdates)
        assertTrue(decoded.plan.fullAccessKey)
        assertFalse(decoded.plan.verifiedReadOnly)

        val stored = SharedPlanCodec.encode(decoded.plan)
        assertFalse(stored.contains("plan-key"))
        assertTrue(SharedPlanCodec.decode(stored).fullAccessKey)
    }

    @Test
    fun `plain share must not carry a key`() {
        val snapshot = SharedPlanCodec.createSnapshot(plan, transactions, shareId, isSandbox = false, includeAmounts = true)
        val leaked = SharedPlanCodec.encode(snapshot.copy(access = SharedPlanAccessDto(exchange = "COINMATE", apiKey = "k", apiSecret = "s")))
        assertReason(SharedPlanError.INVALID) { SharedPlanCodec.decodeShare(leaked, null, ::fakeDecrypt) }
    }

    @Test
    fun `refresh merges new orders once and skips known ones`() {
        val shared = SharedPlanCodec.decode(
            SharedPlanCodec.encode(
                SharedPlanCodec.createSnapshot(
                    plan, transactions, shareId, isSandbox = false, includeAmounts = true,
                    now = t0.plusSeconds(5 * 86_400)
                )
            )
        )
        fun fill(orderId: String, day: Long, crypto: String, fiat: String, side: String = "BUY") = HistoricalTrade(
            orderId = orderId, timestamp = t0.plusSeconds(day * 86_400), crypto = "BTC", fiat = "CZK",
            cryptoAmount = BigDecimal(crypto), fiatAmount = BigDecimal(fiat), price = BigDecimal.ONE,
            fee = BigDecimal("1"), feeAsset = "CZK", side = side
        )
        val merged = RefreshSharedPlanUseCase.merge(
            shared,
            listOf(
                fill("12345", 2, "0.0005", "500"),           // trade ID of a snapshot order
                fill("new-1", 6, "0.0003", "300"),          // two fills of one order
                fill("new-1", 6, "0.0002", "200"),
                fill("other-pair", 7, "1", "1").copy(crypto = "ETH")
            )
        )
        assertEquals(shared.trades.size + 1, merged.size)
        val added = merged.last()
        assertEquals(0, BigDecimal("0.0005").compareTo(added.cryptoAmount))
        assertEquals(0, BigDecimal("500").compareTo(added.fiatAmount))
        assertEquals(0, BigDecimal("1000000").compareTo(added.price))

        // A second refresh with the same history adds nothing.
        val again = RefreshSharedPlanUseCase.merge(shared.copy(trades = merged), listOf(fill("new-1", 6, "0.0005", "500")))
        assertEquals(merged.size, again.size)
    }

    private class FakeVault : SharedPlanStore.KeyVault {
        private val keys = mutableMapOf<String, ExchangeCredentials>()
        override fun get(shareId: String) = keys[shareId]
        override fun put(shareId: String, credentials: ExchangeCredentials) { keys[shareId] = credentials }
        override fun remove(shareId: String) { keys.remove(shareId) }
    }

    /** Test stand-in for BackupCryptoUseCase (which needs android.util.Base64). */
    private fun fakeEncrypt(plaintext: ByteArray, password: String): String =
        java.util.Base64.getEncoder().encodeToString(password.toByteArray() + 0 + plaintext.map { (it.toInt() xor 0x5a).toByte() })

    private fun fakeDecrypt(data: String, password: String): ByteArray {
        val bytes = java.util.Base64.getDecoder().decode(data)
        val split = bytes.indexOf(0)
        require(String(bytes.copyOfRange(0, split)) == password) { "bad tag" }
        return bytes.copyOfRange(split + 1, bytes.size).map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    }

    private fun assertReason(expected: SharedPlanError, block: () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: SharedPlanException) {
            assertEquals(expected, e.reason)
        }
    }

    private fun assertReason(expected: SharedPlanError, json: String) {
        try {
            SharedPlanCodec.decode(json)
            fail("Expected $expected")
        } catch (e: SharedPlanException) {
            assertEquals(expected, e.reason)
        }
    }
}
