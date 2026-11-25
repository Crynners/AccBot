package com.accbot.dca.presentation.screens.plans.sell

import com.accbot.dca.presentation.screens.plans.sell.LadderGenerator.AmountMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class LadderGeneratorTest {

    @Test
    fun `equal crypto - 5 orderu po 0,2 BTC v rozsahu cen`() {
        val orders = LadderGenerator.generate(
            totalAmount = BigDecimal("1"),
            from = BigDecimal("2000000"),
            to = BigDecimal("2400000"),
            count = 5,
            mode = AmountMode.EQUAL_CRYPTO
        )
        assertEquals(5, orders.size)
        assertEquals(0, BigDecimal("2000000.00").compareTo(orders[0].limitPrice))
        assertEquals(0, BigDecimal("2400000.00").compareTo(orders[4].limitPrice))
        // sum amounts == totalAmount (with the last order absorbing rounding drobky)
        val total = orders.fold(BigDecimal.ZERO) { acc, o -> acc + o.cryptoAmount }
        assertEquals(0, BigDecimal("1").compareTo(total))
    }

    @Test
    fun `equal fiat - levnejsi ordery prodavaji vic crypta a soucet odpovida total`() {
        val orders = LadderGenerator.generate(
            totalAmount = BigDecimal("1"),
            from = BigDecimal("1000000"),
            to = BigDecimal("2000000"),
            count = 4,
            mode = AmountMode.EQUAL_FIAT
        )
        assertEquals(4, orders.size)
        assertTrue(
            "cheapest order should sell more crypto than most expensive",
            orders[0].cryptoAmount > orders[3].cryptoAmount
        )
        val total = orders.fold(BigDecimal.ZERO) { acc, o -> acc + o.cryptoAmount }
        assertEquals(0, BigDecimal("1").compareTo(total))
        // Each leg produces almost identical fiat value (~350,877.19)
        val grossValues = orders.map { it.cryptoAmount * it.limitPrice }
        val spread = grossValues.max() - grossValues.min()
        assertTrue("equal fiat legs should have nearly equal fiat proceeds, spread=$spread", spread < BigDecimal("1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `count menez nez 2 hodi exception`() {
        LadderGenerator.generate(
            BigDecimal("1"), BigDecimal("1000"), BigDecimal("2000"), 1, AmountMode.EQUAL_CRYPTO
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `count nad MAX_ORDER_COUNT hodi exception`() {
        LadderGenerator.generate(
            BigDecimal("1"), BigDecimal("1000"), BigDecimal("2000"),
            LadderGenerator.MAX_ORDER_COUNT + 1, AmountMode.EQUAL_CRYPTO
        )
    }

    @Test
    fun `solveToPriceForNetTarget pro EQUAL_FIAT dopocita presny cisty vynos`() {
        val total = BigDecimal("1")
        val from = BigDecimal("1000000")
        val count = 4
        val fee = BigDecimal("0.0035")
        val targetNet = BigDecimal("1500000")

        val solvedTo = LadderGenerator.solveToPriceForNetTarget(
            totalAmount = total,
            from = from,
            count = count,
            mode = AmountMode.EQUAL_FIAT,
            feeRate = fee,
            targetNet = targetNet
        )
        assertNotNull(solvedTo)
        val orders = LadderGenerator.generate(total, from, solvedTo!!, count, AmountMode.EQUAL_FIAT)
        val actualNet = LadderGenerator.totalNetProceeds(orders, fee)
        val diff = (actualNet - targetNet).abs()
        assertTrue("Expected actualNet ($actualNet) within 5 fiat of target ($targetNet)", diff <= BigDecimal("5"))
    }

    @Test
    fun `solveToPriceForNetTarget pro EQUAL_FIAT nad asymptotou vraci null`() {
        // For count=4, from=1M, total=1, fee=0: harmonic mean < 4 * 1M = 4M
        val solved = LadderGenerator.solveToPriceForNetTarget(
            totalAmount = BigDecimal("1"),
            from = BigDecimal("1000000"),
            count = 4,
            mode = AmountMode.EQUAL_FIAT,
            feeRate = BigDecimal.ZERO,
            targetNet = BigDecimal("4000000")
        )
        assertNull(solved)
    }

    @Test
    fun `solveToPriceForNetTarget pro EQUAL_CRYPTO dopocita presny cisty vynos`() {
        val total = BigDecimal("1")
        val from = BigDecimal("1000000")
        val count = 5
        val fee = BigDecimal("0.0035")
        val targetNet = BigDecimal("1500000")

        val solvedTo = LadderGenerator.solveToPriceForNetTarget(
            totalAmount = total,
            from = from,
            count = count,
            mode = AmountMode.EQUAL_CRYPTO,
            feeRate = fee,
            targetNet = targetNet
        )
        assertNotNull(solvedTo)
        val orders = LadderGenerator.generate(total, from, solvedTo!!, count, AmountMode.EQUAL_CRYPTO)
        val actualNet = LadderGenerator.totalNetProceeds(orders, fee)
        val diff = (actualNet - targetNet).abs()
        assertTrue("Expected actualNet ($actualNet) within 5 fiat of target ($targetNet)", diff <= BigDecimal("5"))
    }
}
