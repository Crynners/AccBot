package com.accbot.dca.domain.usecase

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ValidateSellOrderUseCaseLossTest {

    @Test
    fun `pod nakupni cenou vraci LossWarning`() {
        val w = ValidateSellOrderUseCase.checkLoss(
            cryptoAmount = BigDecimal("1"),
            limitPrice = BigDecimal("900000"),
            avgBuyPrice = BigDecimal("1000000"),
            feeRate = BigDecimal("0.0035")
        )
        assertNotNull(w)
    }

    @Test
    fun `tesne nad nakupni cenou ale po fee ztrata vraci LossWarning`() {
        // P=1003500, avg=1M, fee=0.0035 -> netFiat = 1003500 * 0.9965 ~= 999988.75
        // netProfit = 999988.75 - 1000000 = -11.25 < 0
        val w = ValidateSellOrderUseCase.checkLoss(
            cryptoAmount = BigDecimal("1"),
            limitPrice = BigDecimal("1003500"),
            avgBuyPrice = BigDecimal("1000000"),
            feeRate = BigDecimal("0.0035")
        )
        assertNotNull(w)
    }

    @Test
    fun `dostatecne nad nakupni cenou vraci null`() {
        val w = ValidateSellOrderUseCase.checkLoss(
            cryptoAmount = BigDecimal("1"),
            limitPrice = BigDecimal("1100000"),
            avgBuyPrice = BigDecimal("1000000"),
            feeRate = BigDecimal("0.0035")
        )
        assertNull(w)
    }

    @Test
    fun `null avg vraci null`() {
        val w = ValidateSellOrderUseCase.checkLoss(
            cryptoAmount = BigDecimal("1"),
            limitPrice = BigDecimal("900000"),
            avgBuyPrice = null,
            feeRate = BigDecimal("0.0035")
        )
        assertNull(w)
    }
}
