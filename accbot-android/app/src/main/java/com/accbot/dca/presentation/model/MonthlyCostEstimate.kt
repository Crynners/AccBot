package com.accbot.dca.presentation.model

import java.math.BigDecimal

data class MonthlyCostEstimate(
    val minMonthly: BigDecimal,
    val maxMonthly: BigDecimal,
    val currentMonthly: BigDecimal? = null,
    val currentInfo: String? = null
)
