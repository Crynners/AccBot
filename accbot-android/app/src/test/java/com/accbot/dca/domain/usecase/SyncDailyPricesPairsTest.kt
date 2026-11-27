package com.accbot.dca.domain.usecase

import com.accbot.dca.domain.usecase.SyncDailyPricesUseCase.PairToSync
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SyncDailyPricesPairsTest {

    private val jan = LocalDate.of(2024, 1, 1)
    private val mar = LocalDate.of(2024, 3, 1)
    private val jun = LocalDate.of(2024, 6, 1)

    @Test
    fun `extra pairs use the earliest start per pair`() {
        val pairs = SyncDailyPricesUseCase.pairsToSync(
            own = emptyList(),
            extraPairs = listOf(
                ExtraPricePair("BTC", "CZK", jun),
                ExtraPricePair("BTC", "CZK", jan),
                ExtraPricePair("ETH", "EUR", mar),
                ExtraPricePair("BTC", "CZK", mar)
            )
        )
        assertEquals(listOf(PairToSync("BTC", "CZK", jan), PairToSync("ETH", "EUR", mar)), pairs)
    }

    @Test
    fun `held pair also gets the start of a shared plan of the same pair`() {
        val pairs = SyncDailyPricesUseCase.pairsToSync(
            own = listOf("BTC" to "CZK", "ETH" to "EUR"),
            extraPairs = listOf(ExtraPricePair("BTC", "CZK", mar), ExtraPricePair("BTC", "CZK", jan))
        )
        // The use case then starts at the earlier of this and the user's own first purchase.
        assertEquals(
            listOf(PairToSync("BTC", "CZK", jan), PairToSync("ETH", "EUR", null)),
            pairs
        )
    }
}
