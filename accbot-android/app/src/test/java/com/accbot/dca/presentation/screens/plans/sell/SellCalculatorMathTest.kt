package com.accbot.dca.presentation.screens.plans.sell

import com.accbot.dca.presentation.screens.plans.sell.SellCalculatorMath.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SellCalculatorMathTest {

    private val fee = BigDecimal("0.0035")

    @Test
    fun `A a P editovane - dopocita N`() {
        val (a, p, n) = SellCalculatorMath.recompute(
            a = BigDecimal("1"),
            p = BigDecimal("1000000"),
            n = null,
            feeRate = fee,
            lastTwoEdited = listOf(Field.PRICE, Field.AMOUNT)
        )
        // 1 * 1000000 * 0.9965 = 996500
        assertEquals(0, BigDecimal("996500.00").compareTo(n!!))
        assertEquals(0, BigDecimal("1").compareTo(a!!))
        assertEquals(0, BigDecimal("1000000").compareTo(p!!))
    }

    @Test
    fun `A a N editovane - dopocita P`() {
        val (_, p, _) = SellCalculatorMath.recompute(
            a = BigDecimal("1"),
            p = null,
            n = BigDecimal("996500"),
            feeRate = fee,
            lastTwoEdited = listOf(Field.NET, Field.AMOUNT)
        )
        // 996500 / (1 * 0.9965) = 1000000
        assertEquals(0, BigDecimal("1000000.00").compareTo(p!!))
    }

    @Test
    fun `P a N editovane - dopocita A`() {
        val (a, _, _) = SellCalculatorMath.recompute(
            a = null,
            p = BigDecimal("1000000"),
            n = BigDecimal("996500"),
            feeRate = fee,
            lastTwoEdited = listOf(Field.NET, Field.PRICE)
        )
        // 996500 / (1000000 * 0.9965) = 1.0
        assertEquals(0, BigDecimal("1.00000000").compareTo(a!!))
    }

    @Test
    fun `mene nez 2 editovana pole - nedopocitava`() {
        val (_, p, n) = SellCalculatorMath.recompute(
            a = BigDecimal("1"),
            p = null,
            n = null,
            feeRate = fee,
            lastTwoEdited = listOf(Field.AMOUNT)
        )
        assertNull(p)
        assertNull(n)
    }

    @Test
    fun `chybejici vstupy v computed dvojici - vrati null`() {
        val (_, _, n) = SellCalculatorMath.recompute(
            a = null,
            p = BigDecimal("1000000"),
            n = null,
            feeRate = fee,
            lastTwoEdited = listOf(Field.PRICE, Field.AMOUNT)
        )
        // computed = NET, ale a je null -> n = null
        assertNull(n)
    }
}
