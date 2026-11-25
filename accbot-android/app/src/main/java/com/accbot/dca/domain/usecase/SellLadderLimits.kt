package com.accbot.dca.domain.usecase

/** Allowed number of orders in a sell ladder, shared by validation and the sell wizard. */
object SellLadderLimits {
    const val MIN_ORDER_COUNT = 2
    const val MAX_ORDER_COUNT = 20
}
