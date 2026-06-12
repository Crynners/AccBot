package com.accbot.dca.presentation.screens.plans.sell

import com.accbot.dca.presentation.screens.plans.sell.LadderGenerator.AmountMode
import org.junit.Assert.assertEquals
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
    fun `equal fiat - levnejsi ordery prodavaji vic crypta`() {
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
    }

    @Test(expected = IllegalArgumentException::class)
    fun `count menez nez 2 hodi exception`() {
        LadderGenerator.generate(
            BigDecimal("1"), BigDecimal("1000"), BigDecimal("2000"), 1, AmountMode.EQUAL_CRYPTO
        )
    }
}
