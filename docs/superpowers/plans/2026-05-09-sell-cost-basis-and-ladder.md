# Sell wizard - cost basis + ladder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rozsirit sell wizard o cost basis kalkulacku (timestamp-aware cheapest-first), tripolovou kalkulacku (mnozstvi/cena/cisty vynos) s fee math, loss warning, profit summary, a volitelny ladder mod pro scale-out strategie. Anti-emocionalni decision support.

**Architecture:** Cost basis algoritmus je cista funkce (TDD-friendly), volana z `SellWizardViewModel`. Tripolova kalkulacka je separatni pure helper. ViewModel drzi state machine pro single i ladder mod. UI v existujicim `SellWizardBottomSheet` (rozsireni, ne nova obrazovka). Zadna DB schema zmena, vse stateless.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, Room DAO (read-only pro nas), kotlinx.coroutines, JUnit 4 + Kotlin coroutines test (nove pridavame).

**Spec:** `docs/superpowers/specs/2026-05-09-sell-cost-basis-and-ladder-design.md`

**Existing files of interest:**
- `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardBottomSheet.kt`
- `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardViewModel.kt`
- `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCase.kt`
- `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/PlaceLimitSellUseCase.kt`
- `accbot-android/app/src/main/java/com/accbot/dca/exchange/ExchangeApi.kt`
- `accbot-android/app/src/main/java/com/accbot/dca/data/local/Daos.kt:218` - `getTransactionsByPlanSync`
- `accbot-android/app/src/main/java/com/accbot/dca/data/local/Entities.kt` - `TransactionEntity`

**Branch:** zustavame na `feature/dca-sell-extension`.

---

## Faze 1: Foundation - model a algoritmus

### Task 1: Pridat RemainingInventory data class

**Files:**
- Create: `accbot-android/app/src/main/java/com/accbot/dca/domain/model/RemainingInventory.kt`

- [ ] **Krok 1: Vytvorit soubor**

```kotlin
package com.accbot.dca.domain.model

import java.math.BigDecimal

/**
 * Vystup CalculatePlanCostBasisUseCase - timestamp-aware cheapest-first
 * remaining inventory po aplikaci historickych a pending sells.
 */
data class RemainingInventory(
    /** Soucet zbyvajiciho crypta napric vsemi buys s nezkonzumovanou casti. */
    val available: BigDecimal,

    /** Volume-weighted prumerna nakupni cena z [perBuyDetail]. Null kdyz available == 0. */
    val weightedAvgPrice: BigDecimal?,

    /** Per-buy zbytky (jen buys s remaining > 0). Pro debug a future per-fill features. */
    val perBuyDetail: List<RemainingBuy>,

    /** > 0 kdyz historicke sells presahly buys (data inconsistency, napr. po importu). */
    val deficit: BigDecimal
)

data class RemainingBuy(
    val transactionId: Long,
    val price: BigDecimal,
    val remaining: BigDecimal
)
```

- [ ] **Krok 2: Build check**

Run:
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd accbot-android && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Krok 3: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/domain/model/RemainingInventory.kt
git commit -m "feat(sell): add RemainingInventory model for cost basis algorithm"
```

---

### Task 2: Setup unit test infra

Projekt zatim nema `src/test/` (jen androidTest + screenshotTest). Pridame JUnit 4 + Kotlin test deps a vytvorime test source set.

**Files:**
- Modify: `accbot-android/app/build.gradle.kts`
- Create: `accbot-android/app/src/test/java/com/accbot/dca/.gitkeep` (placeholder pro git)

- [ ] **Krok 1: Pridat test dependencies**

V `accbot-android/app/build.gradle.kts` v sekci `dependencies { ... }` pridat (pokud uz tam nejsou):

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
```

(Verze sladit s existujicimi - mrknout do `gradle/libs.versions.toml` pokud projekt pouziva version catalog. Pokud existuje `testImplementation(libs.junit)` apod., pouzit aliasy.)

- [ ] **Krok 2: Vytvorit test source set folder**

```bash
mkdir -p accbot-android/app/src/test/java/com/accbot/dca
touch accbot-android/app/src/test/java/com/accbot/dca/.gitkeep
```

- [ ] **Krok 3: Build check**

Run:
```bash
cd accbot-android && ./gradlew :app:compileDebugUnitTestKotlin
```

Expected: BUILD SUCCESSFUL (zadne testy zatim, jen kompilace test source setu).

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/build.gradle.kts accbot-android/app/src/test/
git commit -m "chore: setup JUnit 4 + coroutines test infra"
```

---

### Task 3: CalculatePlanCostBasisUseCase + unit testy (TDD)

**Approach:** Algoritmus extrahovat do pure funkce v companion objectu, aby se daly testovat bez DB / Hilt.

**Files:**
- Create: `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/CalculatePlanCostBasisUseCase.kt`
- Create: `accbot-android/app/src/test/java/com/accbot/dca/domain/usecase/CalculatePlanCostBasisUseCaseTest.kt`

- [ ] **Krok 1: Napsat selhavajici testy nejdrive (TDD)**

Soubor `accbot-android/app/src/test/java/com/accbot/dca/domain/usecase/CalculatePlanCostBasisUseCaseTest.kt`:

```kotlin
package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CalculatePlanCostBasisUseCaseTest {

    private val t0: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private fun ts(daysOffset: Long): Instant = t0.plusSeconds(daysOffset * 86_400)

    private fun buy(
        id: Long,
        crypto: BigDecimal,
        price: BigDecimal,
        executedAt: Instant,
        status: TransactionStatus = TransactionStatus.COMPLETED
    ): TransactionEntity = TransactionEntity(
        id = id,
        planId = 1,
        connectionId = 1,
        exchange = Exchange.COINMATE,
        crypto = "BTC",
        fiat = "CZK",
        fiatAmount = crypto * price,
        cryptoAmount = crypto,
        price = price,
        fee = BigDecimal.ZERO,
        feeAsset = "CZK",
        status = status,
        exchangeOrderId = "buy-$id",
        executedAt = executedAt,
        side = TransactionSide.BUY
    )

    private fun sell(
        id: Long,
        crypto: BigDecimal,
        price: BigDecimal,
        executedAt: Instant,
        status: TransactionStatus = TransactionStatus.COMPLETED,
        requested: BigDecimal? = null
    ): TransactionEntity = TransactionEntity(
        id = id,
        planId = 1,
        connectionId = 1,
        exchange = Exchange.COINMATE,
        crypto = "BTC",
        fiat = "CZK",
        fiatAmount = crypto * price,
        cryptoAmount = crypto,
        price = price,
        fee = BigDecimal.ZERO,
        feeAsset = "CZK",
        status = status,
        exchangeOrderId = "sell-$id",
        executedAt = executedAt,
        side = TransactionSide.SELL,
        requestedCryptoAmount = requested ?: crypto,
        limitPrice = price
    )

    @Test
    fun `prazdny plan vraci available=0 a avg=null`() {
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(emptyList())
        assertEquals(BigDecimal.ZERO, result.available)
        assertNull(result.weightedAvgPrice)
        assertTrue(result.perBuyDetail.isEmpty())
        assertEquals(BigDecimal.ZERO, result.deficit)
    }

    @Test
    fun `jeden buy bez sells - available a avg jsou z buyu`() {
        val txs = listOf(buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)))
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal("1").compareTo(result.available))
        assertEquals(0, BigDecimal("1000000").compareTo(result.weightedAvgPrice!!))
    }

    @Test
    fun `dva buys, sell konzumuje cheapest first`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),  // cheaper
            buy(2, BigDecimal("1"), BigDecimal("2000000"), ts(1)),
            sell(3, BigDecimal("0.5"), BigDecimal("2500000"), ts(2))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        // 0.5 BTC zkonzumovano z 1M buyu, zbyva 0.5 BTC @ 1M + 1 BTC @ 2M
        assertEquals(0, BigDecimal("1.5").compareTo(result.available))
        // weighted avg = (0.5 × 1M + 1 × 2M) / 1.5 = 5/3 M = 1666666.67
        val expected = BigDecimal("1666666.66666667")
        assertEquals(0, expected.compareTo(result.weightedAvgPrice!!))
    }

    @Test
    fun `novy levny buy po sellu neovlivni avg pro driv prodane`() {
        // Buy 1M, sell 0.5 (consumes 0.5 z 1M), pak buy 800k cheaper.
        // Cheapest-first by retroactivne mohl konzumovat 800k buy, ale timestamp filter to nedovoli.
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            sell(2, BigDecimal("0.5"), BigDecimal("2000000"), ts(1)),
            buy(3, BigDecimal("0.5"), BigDecimal("800000"), ts(2))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        // Sell @ts(1) vidi jen buy 1 (ts(0)). Konzumuje 0.5 z 1M.
        // Remaining: 0.5 @ 1M + 0.5 @ 800k = avg (500k + 400k) / 1.0 = 900k
        assertEquals(0, BigDecimal("1.0").compareTo(result.available))
        assertEquals(0, BigDecimal("900000").compareTo(result.weightedAvgPrice!!))
    }

    @Test
    fun `PENDING sell rezervuje cheapest`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            sell(2, BigDecimal("0"), BigDecimal("3000000"), ts(1),
                 status = TransactionStatus.PENDING, requested = BigDecimal("0.5"))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        // Pending rezervuje 0.5 z 1M buyu. Remaining 0.5 @ 1M.
        assertEquals(0, BigDecimal("0.5").compareTo(result.available))
        assertEquals(0, BigDecimal("1000000").compareTo(result.weightedAvgPrice!!))
    }

    @Test
    fun `PARTIAL sell pouziva requested ne filled`() {
        // PARTIAL: requested 0.5, filled 0.2 -> efektivne konzumuje 0.5 (cele rezervuje)
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            sell(2, BigDecimal("0.2"), BigDecimal("3000000"), ts(1),
                 status = TransactionStatus.PARTIAL, requested = BigDecimal("0.5"))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal("0.5").compareTo(result.available))
    }

    @Test
    fun `FAILED sell se ignoruje`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            sell(2, BigDecimal("0.5"), BigDecimal("3000000"), ts(1),
                 status = TransactionStatus.FAILED)
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal("1").compareTo(result.available))
    }

    @Test
    fun `negative inventory - sells presahly buys, deficit non-zero`() {
        val txs = listOf(
            buy(1, BigDecimal("0.5"), BigDecimal("1000000"), ts(0)),
            sell(2, BigDecimal("1"), BigDecimal("2000000"), ts(1))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal.ZERO.compareTo(result.available))
        assertNull(result.weightedAvgPrice)
        assertEquals(0, BigDecimal("0.5").compareTo(result.deficit))
    }

    @Test
    fun `tie na cene rozhodne starsi executedAt napred`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            buy(2, BigDecimal("1"), BigDecimal("1000000"), ts(1)),
            sell(3, BigDecimal("0.5"), BigDecimal("2000000"), ts(2))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        // Sell konzumuje 0.5 ze starsiho buyu. Remaining: 0.5 z buy 1 + 1 z buy 2.
        assertEquals(0, BigDecimal("1.5").compareTo(result.available))
        val cheap1 = result.perBuyDetail.firstOrNull { it.transactionId == 1L }
        assertEquals(0, BigDecimal("0.5").compareTo(cheap1?.remaining ?: BigDecimal.ZERO))
    }
}
```

**Pozn.:** Pokud `TransactionEntity` ma jine pole / jine pojmenovani, sladit s realnym definici v `Entities.kt`. Vsechna pouzita pole tam jsou (`id`, `planId`, `connectionId`, `exchange`, `crypto`, `fiat`, `fiatAmount`, `cryptoAmount`, `price`, `fee`, `feeAsset`, `status`, `exchangeOrderId`, `executedAt`, `side`, `requestedCryptoAmount`, `limitPrice`).

- [ ] **Krok 2: Spustit testy a overit ze selhavaji s "computeCostBasis is not defined"**

Run:
```bash
cd accbot-android && ./gradlew :app:testDebugUnitTest --tests "com.accbot.dca.domain.usecase.CalculatePlanCostBasisUseCaseTest"
```

Expected: kompilacni chyba "unresolved reference: CalculatePlanCostBasisUseCase".

- [ ] **Krok 3: Implementovat CalculatePlanCostBasisUseCase**

Soubor `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/CalculatePlanCostBasisUseCase.kt`:

```kotlin
package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.RemainingBuy
import com.accbot.dca.domain.model.RemainingInventory
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Spocita zbyvajici inventar (a vazenou prumernou nakupni cenu) pro plan
 * pomoci timestamp-aware cheapest-first algoritmu.
 *
 * Pure logika v [computeCostBasis] companion funkci pro snadne unit testovani.
 */
class CalculatePlanCostBasisUseCase @Inject constructor(
    private val database: DcaDatabase
) {
    suspend operator fun invoke(planId: Long): RemainingInventory {
        val transactions = database.transactionDao().getTransactionsByPlanSync(planId)
        return computeCostBasis(transactions)
    }

    companion object {
        fun computeCostBasis(transactions: List<TransactionEntity>): RemainingInventory {
            val buys = transactions.filter {
                it.side == TransactionSide.BUY &&
                    (it.status == TransactionStatus.COMPLETED || it.status == TransactionStatus.PARTIAL)
            }

            val sells = transactions.filter {
                it.side == TransactionSide.SELL &&
                    (it.status == TransactionStatus.COMPLETED ||
                        it.status == TransactionStatus.PARTIAL ||
                        it.status == TransactionStatus.PENDING)
            }.sortedBy { it.executedAt }

            val consumed = HashMap<Long, BigDecimal>(buys.size)
            for (b in buys) consumed[b.id] = BigDecimal.ZERO

            var totalDeficit = BigDecimal.ZERO

            for (sell in sells) {
                val toConsume = effectiveConsumption(sell)
                if (toConsume <= BigDecimal.ZERO) continue
                var remaining = toConsume

                val eligible = buys
                    .filter { it.executedAt.isBefore(sell.executedAt) }
                    .filter {
                        (it.cryptoAmount - (consumed[it.id] ?: BigDecimal.ZERO)) > BigDecimal.ZERO
                    }
                    .sortedWith(compareBy({ it.price }, { it.executedAt }))

                for (b in eligible) {
                    if (remaining <= BigDecimal.ZERO) break
                    val available = b.cryptoAmount - (consumed[b.id] ?: BigDecimal.ZERO)
                    val take = remaining.min(available)
                    consumed[b.id] = (consumed[b.id] ?: BigDecimal.ZERO) + take
                    remaining -= take
                }

                if (remaining > BigDecimal.ZERO) totalDeficit = totalDeficit + remaining
            }

            val perBuyDetail = buys.mapNotNull { b ->
                val left = b.cryptoAmount - (consumed[b.id] ?: BigDecimal.ZERO)
                if (left > BigDecimal.ZERO) RemainingBuy(b.id, b.price, left) else null
            }

            val available = perBuyDetail.fold(BigDecimal.ZERO) { acc, rb -> acc + rb.remaining }
            val weightedAvg = if (available > BigDecimal.ZERO) {
                val sumCost = perBuyDetail.fold(BigDecimal.ZERO) { acc, rb ->
                    acc + rb.remaining * rb.price
                }
                sumCost.divide(available, 8, RoundingMode.HALF_UP)
            } else null

            return RemainingInventory(
                available = available,
                weightedAvgPrice = weightedAvg,
                perBuyDetail = perBuyDetail,
                deficit = totalDeficit
            )
        }

        /**
         * Mnozstvi crypta, ktere ten sell rezervuje/zkonzumuje. Pro PENDING/PARTIAL = requested
         * (cela rezervace, vc. unfilled cast). Pro COMPLETED = cryptoAmount (= requested).
         */
        private fun effectiveConsumption(sell: TransactionEntity): BigDecimal {
            val requested = sell.requestedCryptoAmount ?: BigDecimal.ZERO
            return requested.max(sell.cryptoAmount)
        }
    }
}
```

- [ ] **Krok 4: Spustit testy a overit ze prochazeji**

Run:
```bash
cd accbot-android && ./gradlew :app:testDebugUnitTest --tests "com.accbot.dca.domain.usecase.CalculatePlanCostBasisUseCaseTest"
```

Expected: 9 testu PASS.

Pokud nejaky selhe, opravit implementaci, ne test (pokud test nepoukazuje na chybu zamerne).

- [ ] **Krok 5: Build check**

Run:
```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Krok 6: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/CalculatePlanCostBasisUseCase.kt
git add accbot-android/app/src/test/java/com/accbot/dca/domain/usecase/CalculatePlanCostBasisUseCaseTest.kt
git commit -m "feat(sell): add CalculatePlanCostBasisUseCase with timestamp-aware cheapest-first"
```

---

## Faze 2: Fee plumbing

### Task 4: Pridat estimatedTakerFeeRate do ExchangeApi

**Files:**
- Modify: `accbot-android/app/src/main/java/com/accbot/dca/exchange/ExchangeApi.kt`
- Modify: `accbot-android/app/src/main/java/com/accbot/dca/exchange/CoinmateApi.kt`
- Modify: `accbot-android/app/src/main/java/com/accbot/dca/exchange/BinanceApi.kt`
- Modify: `accbot-android/app/src/main/java/com/accbot/dca/exchange/CoinbaseApi.kt`
- Modify: `accbot-android/app/src/main/java/com/accbot/dca/exchange/OtherExchanges.kt` (Kraken, KuCoin, Bitfinex, Huobi)

- [ ] **Krok 1: Pridat property do ExchangeApi interface**

V `ExchangeApi.kt` interfejsu (poblize existujiciho `supportsLimitSell: Boolean`):

```kotlin
/**
 * Odhadovany taker fee rate (e.g. 0.0035 = 0.35%) pro decision support v UI.
 * Hodnota nemusi presne odpovidat realnemu fee uzivatele (lower tier, BNB discount, ...).
 */
val estimatedTakerFeeRate: BigDecimal
```

- [ ] **Krok 2: Implementovat v CoinmateApi**

V `CoinmateApi.kt`, doplnit pod existujici `takerFeeRate`:

```kotlin
override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.0035")
```

(Existujici `private val takerFeeRate` se pouziva interne pro fallback fee - nezasahovat, je to jiny use case.)

- [ ] **Krok 3: Implementovat v BinanceApi**

V `BinanceApi.kt`:

```kotlin
override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.001")
```

- [ ] **Krok 4: Implementovat v CoinbaseApi**

V `CoinbaseApi.kt`:

```kotlin
override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.0040")
```

- [ ] **Krok 5: Implementovat v Kraken/KuCoin/Bitfinex/Huobi (OtherExchanges.kt)**

V kazde z trid:

```kotlin
// KrakenApi
override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.0026")

// KuCoinApi
override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.001")

// BitfinexApi
override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.002")

// HuobiApi
override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.002")
```

- [ ] **Krok 6: Build check**

Run:
```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. Pokud failne s "class is not abstract and does not implement abstract member estimatedTakerFeeRate" - chybi implementace v nektere z trid.

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/exchange/
git commit -m "feat(sell): add estimatedTakerFeeRate to ExchangeApi for fee math in wizard"
```

---

## Faze 3: Validation logic

### Task 5: Pridat LossWarning do ValidateSellOrderUseCase

**Files:**
- Modify: `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCase.kt`
- Create: `accbot-android/app/src/test/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCaseLossTest.kt`

- [ ] **Krok 1: Pridat LossWarning do sealed SellValidation**

V `ValidateSellOrderUseCase.kt` v `sealed class SellValidation`:

```kotlin
data class LossWarning(val lossFiat: BigDecimal, val lossPct: Double) : SellValidation()
```

- [ ] **Krok 2: Rozsirit invoke o avg buy price + fee rate vstupy**

Modifikovat signature:

```kotlin
suspend operator fun invoke(
    planId: Long,
    cryptoAmount: BigDecimal,
    limitPrice: BigDecimal,
    minOrderSize: BigDecimal,
    currentSpot: BigDecimal?,
    avgBuyPrice: BigDecimal?,        // NOVE
    feeRate: BigDecimal              // NOVE
): List<SellValidation>
```

Pridat trigger logiku (umistit za existujici `instantFill` / `farFromMarket` checks, pred final `result.isEmpty()`):

```kotlin
if (avgBuyPrice != null && avgBuyPrice > BigDecimal.ZERO) {
    val grossFiat = cryptoAmount * limitPrice
    val netFiat = grossFiat * (BigDecimal.ONE - feeRate)
    val costBasis = cryptoAmount * avgBuyPrice
    val netProfit = netFiat - costBasis
    if (netProfit < BigDecimal.ZERO) {
        val lossPct = if (costBasis > BigDecimal.ZERO) {
            netProfit.toDouble() / costBasis.toDouble()
        } else 0.0
        result += SellValidation.LossWarning(lossFiat = netProfit.negate(), lossPct = -lossPct)
    }
}
```

- [ ] **Krok 3: Napsat unit testy pro novou logiku**

`accbot-android/app/src/test/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCaseLossTest.kt`:

**Pozn.:** ValidateSellOrderUseCase pouziva `database` injekci. Pro unit test potrebujeme bud (a) refaktorovat loss-check do pure helperu, nebo (b) mockovat `DcaDatabase`.

Refaktor: extrahovat loss-check do internal funkce nebo do companion objectu, testovat ji primo:

V `ValidateSellOrderUseCase.kt` companion:

```kotlin
companion object {
    /**
     * Pure helper pro loss-check, testovany unit testem.
     * Vraci LossWarning kdyz `netProfit < 0`, jinak null.
     */
    internal fun checkLoss(
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal,
        avgBuyPrice: BigDecimal?,
        feeRate: BigDecimal
    ): SellValidation.LossWarning? {
        if (avgBuyPrice == null || avgBuyPrice <= BigDecimal.ZERO) return null
        val grossFiat = cryptoAmount * limitPrice
        val netFiat = grossFiat * (BigDecimal.ONE - feeRate)
        val costBasis = cryptoAmount * avgBuyPrice
        val netProfit = netFiat - costBasis
        if (netProfit >= BigDecimal.ZERO) return null
        val lossPct = if (costBasis > BigDecimal.ZERO) {
            netProfit.toDouble() / costBasis.toDouble()
        } else 0.0
        return SellValidation.LossWarning(lossFiat = netProfit.negate(), lossPct = -lossPct)
    }
}
```

A v `invoke()` zavolat `checkLoss(...)?.let { result += it }`.

Test soubor:

```kotlin
package com.accbot.dca.domain.usecase

import org.junit.Assert.assertEquals
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
        assertEquals(true, w != null)
    }

    @Test
    fun `tesne nad nakupni cenou ale po fee ztrata vraci LossWarning`() {
        // P=1003500, avg=1000000, fee=0.0035
        // grossFiat = 1003500, netFiat = 1003500 × 0.9965 = 999988.75
        // costBasis = 1000000 -> netProfit = -11.25 < 0
        val w = ValidateSellOrderUseCase.checkLoss(
            cryptoAmount = BigDecimal("1"),
            limitPrice = BigDecimal("1003500"),
            avgBuyPrice = BigDecimal("1000000"),
            feeRate = BigDecimal("0.0035")
        )
        assertEquals(true, w != null)
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
```

- [ ] **Krok 4: Spustit testy**

```bash
cd accbot-android && ./gradlew :app:testDebugUnitTest --tests "com.accbot.dca.domain.usecase.ValidateSellOrderUseCaseLossTest"
```

Expected: 4 testy PASS.

- [ ] **Krok 5: Najit existujici call-sites ValidateSellOrderUseCase a pridat avg + feeRate parametry**

Pravdepodobne jen `SellWizardViewModel.kt`. Hledat:

```bash
grep -rn "validateSellOrderUseCase\|ValidateSellOrderUseCase" accbot-android/app/src/main
```

Doplnit volani s pravymi argumenty (avg z `CalculatePlanCostBasisUseCase`, feeRate z `api.estimatedTakerFeeRate`). Tohle se finalizuje az v Tasku 8, zatim staci aby kompilace prosla - lze docasne predat `null` a `BigDecimal.ZERO` a zustanou `LossWarning` skip vetve.

- [ ] **Krok 6: Build check**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCase.kt
git add accbot-android/app/src/test/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCaseLossTest.kt
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardViewModel.kt
git commit -m "feat(sell): LossWarning in ValidateSellOrderUseCase based on net-of-fee profit"
```

---

## Faze 4: Sell calculator helper a ViewModel

### Task 6: SellCalculatorMath pure helper + testy

**Files:**
- Create: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellCalculatorMath.kt`
- Create: `accbot-android/app/src/test/java/com/accbot/dca/presentation/screens/plans/sell/SellCalculatorMathTest.kt`

- [ ] **Krok 1: Implementovat pure helper**

```kotlin
package com.accbot.dca.presentation.screens.plans.sell

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure logika pro tripolovou kalkulacku (Amount, Price, Net).
 * Vztah: N = A × P × (1 - feeRate)
 *
 * Pri editaci jednoho z poli ViewModel zavola [recompute] a zaznamena pole jako
 * "naposledy editovane". Pole, ktere neni v `lastTwoEdited`, je dopocitano.
 */
object SellCalculatorMath {

    enum class Field { AMOUNT, PRICE, NET }

    /**
     * @param a mnozstvi crypta
     * @param p limit cena
     * @param n cisty vynos
     * @param feeRate burzy
     * @param lastTwoEdited pole v poradi nejnovejsi -> druhe nejnovejsi (FIFO buffer)
     * @return updated trojice (a, p, n) s dopocitanym 3. polem
     */
    fun recompute(
        a: BigDecimal?,
        p: BigDecimal?,
        n: BigDecimal?,
        feeRate: BigDecimal,
        lastTwoEdited: List<Field>
    ): Triple<BigDecimal?, BigDecimal?, BigDecimal?> {
        if (lastTwoEdited.size < 2) return Triple(a, p, n)
        val factor = BigDecimal.ONE - feeRate
        val toCompute = Field.values().firstOrNull { it !in lastTwoEdited } ?: return Triple(a, p, n)

        return when (toCompute) {
            Field.NET -> {
                val newN = if (a != null && p != null) (a * p * factor).setScale(2, RoundingMode.HALF_UP)
                           else null
                Triple(a, p, newN)
            }
            Field.PRICE -> {
                val newP = if (a != null && n != null && a > BigDecimal.ZERO && factor > BigDecimal.ZERO)
                    n.divide(a * factor, 2, RoundingMode.HALF_UP)
                else null
                Triple(a, newP, n)
            }
            Field.AMOUNT -> {
                val newA = if (p != null && n != null && p > BigDecimal.ZERO && factor > BigDecimal.ZERO)
                    n.divide(p * factor, 8, RoundingMode.HALF_UP)
                else null
                Triple(newA, p, n)
            }
        }
    }
}
```

- [ ] **Krok 2: Napsat testy**

```kotlin
package com.accbot.dca.presentation.screens.plans.sell

import com.accbot.dca.presentation.screens.plans.sell.SellCalculatorMath.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SellCalculatorMathTest {

    private val fee = BigDecimal("0.0035")  // 0.35%

    @Test
    fun `A a P editovane - dopocita N`() {
        val (a, p, n) = SellCalculatorMath.recompute(
            a = BigDecimal("1"),
            p = BigDecimal("1000000"),
            n = null,
            feeRate = fee,
            lastTwoEdited = listOf(Field.PRICE, Field.AMOUNT)
        )
        // 1 × 1000000 × 0.9965 = 996500
        assertEquals(0, BigDecimal("996500.00").compareTo(n!!))
        assertEquals(BigDecimal("1"), a)
        assertEquals(BigDecimal("1000000"), p)
    }

    @Test
    fun `A a N editovane - dopocita P`() {
        val (a, p, n) = SellCalculatorMath.recompute(
            a = BigDecimal("1"),
            p = null,
            n = BigDecimal("996500"),
            feeRate = fee,
            lastTwoEdited = listOf(Field.NET, Field.AMOUNT)
        )
        // 996500 / (1 × 0.9965) = 1000000
        assertEquals(0, BigDecimal("1000000.00").compareTo(p!!))
    }

    @Test
    fun `P a N editovane - dopocita A`() {
        val (a, p, n) = SellCalculatorMath.recompute(
            a = null,
            p = BigDecimal("1000000"),
            n = BigDecimal("996500"),
            feeRate = fee,
            lastTwoEdited = listOf(Field.NET, Field.PRICE)
        )
        // 996500 / (1000000 × 0.9965) = 1.0
        assertEquals(0, BigDecimal("1.00000000").compareTo(a!!))
    }

    @Test
    fun `mene nez 2 editovana pole - nedopocitava`() {
        val (a, p, n) = SellCalculatorMath.recompute(
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
        val (a, p, n) = SellCalculatorMath.recompute(
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
```

- [ ] **Krok 3: Spustit testy**

```bash
cd accbot-android && ./gradlew :app:testDebugUnitTest --tests "com.accbot.dca.presentation.screens.plans.sell.SellCalculatorMathTest"
```

Expected: 5 testu PASS.

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellCalculatorMath.kt
git add accbot-android/app/src/test/java/com/accbot/dca/presentation/screens/plans/sell/SellCalculatorMathTest.kt
git commit -m "feat(sell): SellCalculatorMath pure helper for amount/price/net field"
```

---

### Task 7: SellWizardViewModel - cost basis prefill + state machine

**Files:**
- Modify: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardViewModel.kt`

- [ ] **Krok 1: Precist aktualni SellWizardViewModel**

```bash
cat accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardViewModel.kt | head -100
```

Pochopit existujici state model. Identifikovat, kde se uchovava amount, limitPrice, kdy se vola `validateSellOrderUseCase` a `placeLimitSellUseCase`.

- [ ] **Krok 2: Doplnit pole do state**

V state data class (`SellWizardState` nebo podobne):

```kotlin
data class SellWizardState(
    // ...existujici pole...
    val avgBuyPrice: String = "",                 // text input, prefill z cost basis
    val avgBuyPriceManual: Boolean = false,       // user prepsal default
    val netFiat: String = "",                     // 3. pole kalkulacky
    val computedRemainingInventory: RemainingInventory? = null,
    val lastTwoEditedFields: List<SellCalculatorMath.Field> = emptyList(),
    val feeRate: BigDecimal = BigDecimal.ZERO,    // z api.estimatedTakerFeeRate
    val lossWarning: SellValidation.LossWarning? = null
)
```

- [ ] **Krok 3: Pridat dependencies do constructoru**

```kotlin
@HiltViewModel
class SellWizardViewModel @Inject constructor(
    private val savedState: SavedStateHandle,  // pokud uz neni
    private val database: DcaDatabase,         // existing
    private val calculatePlanCostBasisUseCase: CalculatePlanCostBasisUseCase,  // NEW
    private val exchangeApiFactory: ExchangeApiFactory,  // existing or new
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences,
    private val validateSellOrderUseCase: ValidateSellOrderUseCase,
    private val placeLimitSellUseCase: PlaceLimitSellUseCase,
    private val minOrderSizeRepository: MinOrderSizeRepository
) : ViewModel() { ... }
```

- [ ] **Krok 4: Pri inicializaci viewmodelu (load planId) spustit cost basis a fee rate fetch**

```kotlin
init {
    val planId = savedState.get<Long>("planId") ?: return
    viewModelScope.launch {
        val plan = database.dcaPlanDao().getPlanById(planId) ?: return@launch
        val credentials = credentialsStore.getCredentials(
            plan.connectionId, userPreferences.isSandboxMode()
        ) ?: return@launch
        val api = exchangeApiFactory.create(credentials)

        val inventory = calculatePlanCostBasisUseCase(planId)
        _state.update {
            it.copy(
                computedRemainingInventory = inventory,
                avgBuyPrice = inventory.weightedAvgPrice?.toPlainString() ?: "",
                feeRate = api.estimatedTakerFeeRate
            )
        }
    }
}
```

- [ ] **Krok 5: Reagovat na editaci poli (amount/price/net)**

Pridat handlery:

```kotlin
fun onAmountChange(text: String) {
    _state.update { st ->
        val a = text.toBigDecimalOrNull()
        val p = st.limitPrice.toBigDecimalOrNull()
        val n = st.netFiat.toBigDecimalOrNull()
        val newLastTwo = listOf(SellCalculatorMath.Field.AMOUNT) +
            st.lastTwoEditedFields.filter { it != SellCalculatorMath.Field.AMOUNT }.take(1)
        val (newA, newP, newN) = SellCalculatorMath.recompute(a, p, n, st.feeRate, newLastTwo)
        st.copy(
            amount = text,
            limitPrice = newP?.toPlainString() ?: st.limitPrice,
            netFiat = newN?.toPlainString() ?: st.netFiat,
            lastTwoEditedFields = newLastTwo
        )
    }
    revalidate()
}

fun onLimitPriceChange(text: String) {
    _state.update { st ->
        val a = st.amount.toBigDecimalOrNull()
        val p = text.toBigDecimalOrNull()
        val n = st.netFiat.toBigDecimalOrNull()
        val newLastTwo = listOf(SellCalculatorMath.Field.PRICE) +
            st.lastTwoEditedFields.filter { it != SellCalculatorMath.Field.PRICE }.take(1)
        val (newA, newP, newN) = SellCalculatorMath.recompute(a, p, n, st.feeRate, newLastTwo)
        st.copy(
            amount = newA?.toPlainString() ?: st.amount,
            limitPrice = text,
            netFiat = newN?.toPlainString() ?: st.netFiat,
            lastTwoEditedFields = newLastTwo
        )
    }
    revalidate()
}

fun onNetFiatChange(text: String) {
    _state.update { st ->
        val a = st.amount.toBigDecimalOrNull()
        val p = st.limitPrice.toBigDecimalOrNull()
        val n = text.toBigDecimalOrNull()
        val newLastTwo = listOf(SellCalculatorMath.Field.NET) +
            st.lastTwoEditedFields.filter { it != SellCalculatorMath.Field.NET }.take(1)
        val (newA, newP, newN) = SellCalculatorMath.recompute(a, p, n, st.feeRate, newLastTwo)
        st.copy(
            amount = newA?.toPlainString() ?: st.amount,
            limitPrice = newP?.toPlainString() ?: st.limitPrice,
            netFiat = text,
            lastTwoEditedFields = newLastTwo
        )
    }
    revalidate()
}
```

- [ ] **Krok 6: Manualni override avg buy**

```kotlin
fun onAvgBuyPriceChange(text: String) {
    _state.update { it.copy(avgBuyPrice = text, avgBuyPriceManual = text.isNotBlank()) }
    revalidate()
}

fun onResetAvgBuyPrice() {
    _state.update { st ->
        st.copy(
            avgBuyPrice = st.computedRemainingInventory?.weightedAvgPrice?.toPlainString() ?: "",
            avgBuyPriceManual = false
        )
    }
    revalidate()
}
```

- [ ] **Krok 7: Aktualizovat revalidate s avgBuy a feeRate**

```kotlin
private fun revalidate() {
    viewModelScope.launch {
        val st = _state.value
        val avg = st.avgBuyPrice.toBigDecimalOrNull()
        val amount = st.amount.toBigDecimalOrNull()
        val price = st.limitPrice.toBigDecimalOrNull()
        if (amount == null || price == null) return@launch

        val results = validateSellOrderUseCase(
            planId = st.planId,
            cryptoAmount = amount,
            limitPrice = price,
            minOrderSize = st.minOrderSize ?: BigDecimal.ZERO,
            currentSpot = st.currentSpot,
            avgBuyPrice = avg,
            feeRate = st.feeRate
        )

        val loss = results.filterIsInstance<SellValidation.LossWarning>().firstOrNull()
        _state.update { it.copy(validation = results, lossWarning = loss) }
    }
}
```

- [ ] **Krok 8: Build check**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. Pokud je tam neco co konfliktuje s aktualni state, najit & opravit; struktura ViewModelu je popsana obecne, sladit s realnou.

- [ ] **Krok 9: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardViewModel.kt
git commit -m "feat(sell): wire cost basis + 3-field calculator into SellWizardViewModel"
```

---

## Faze 5: UI - single mod

### Task 8: Avg buy price field + reset tlacitko

**Files:**
- Modify: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardBottomSheet.kt`
- Modify: `accbot-android/app/src/main/res/values/strings.xml`
- Modify: `accbot-android/app/src/main/res/values-cs/strings.xml`

- [ ] **Krok 1: Pridat stringy**

`values/strings.xml`:

```xml
<string name="sell_wizard_avg_buy_price">Average buy price</string>
<string name="sell_wizard_avg_buy_price_helper_auto">Auto-calculated from this plan</string>
<string name="sell_wizard_avg_buy_price_helper_manual">Manually entered</string>
<string name="sell_wizard_avg_buy_price_reset">Calculate from plan</string>
<string name="sell_wizard_avg_buy_price_required">Enter manually (no buys yet or all sold)</string>
```

`values-cs/strings.xml`:

```xml
<string name="sell_wizard_avg_buy_price">Prumerna nakupni cena</string>
<string name="sell_wizard_avg_buy_price_helper_auto">Spocitano z planu</string>
<string name="sell_wizard_avg_buy_price_helper_manual">Zadano rucne</string>
<string name="sell_wizard_avg_buy_price_reset">Spocitat z planu</string>
<string name="sell_wizard_avg_buy_price_required">Zadej rucne (zadne buys nebo vse prodano)</string>
```

- [ ] **Krok 2: Pridat OutlinedTextField pro avg buy price (uvnitr SellWizardBottomSheet, krok 1)**

Najit zacatek wizardu (Composable function `SellWizardStep1` nebo podobne) a pridat na zacatek, pred existujici pole pro mnozstvi:

```kotlin
OutlinedTextField(
    value = state.avgBuyPrice,
    onValueChange = viewModel::onAvgBuyPriceChange,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(stringResource(R.string.sell_wizard_avg_buy_price)) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    supportingText = {
        val res = when {
            state.computedRemainingInventory?.weightedAvgPrice == null -> R.string.sell_wizard_avg_buy_price_required
            state.avgBuyPriceManual -> R.string.sell_wizard_avg_buy_price_helper_manual
            else -> R.string.sell_wizard_avg_buy_price_helper_auto
        }
        Text(stringResource(res))
    },
    trailingIcon = {
        if (state.avgBuyPriceManual && state.computedRemainingInventory?.weightedAvgPrice != null) {
            TextButton(onClick = viewModel::onResetAvgBuyPrice) {
                Text(stringResource(R.string.sell_wizard_avg_buy_price_reset))
            }
        }
    }
)
```

- [ ] **Krok 3: Build check**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
```

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardBottomSheet.kt
git add accbot-android/app/src/main/res/values/strings.xml
git add accbot-android/app/src/main/res/values-cs/strings.xml
git commit -m "feat(sell): avg buy price field with auto-prefill + manual override"
```

---

### Task 9: Net fiat pole + presety

**Files:**
- Modify: `SellWizardBottomSheet.kt`
- Modify: `strings.xml` (cs + en)

- [ ] **Krok 1: Stringy**

```xml
<!-- en -->
<string name="sell_wizard_net_fiat">Net proceeds (after fee)</string>
<string name="sell_wizard_net_preset_label">Profit on this transaction</string>
<string name="sell_wizard_net_preset_10">+10%</string>
<string name="sell_wizard_net_preset_20">+20%</string>
<string name="sell_wizard_net_preset_50">+50%</string>
<string name="sell_wizard_net_preset_100">+100%</string>

<!-- cs -->
<string name="sell_wizard_net_fiat">Cisty vynos (po fee)</string>
<string name="sell_wizard_net_preset_label">Zisk na transakci</string>
<!-- preset values stejne -->
```

- [ ] **Krok 2: Pridat OutlinedTextField + preset Row pod limit price field**

```kotlin
OutlinedTextField(
    value = state.netFiat,
    onValueChange = viewModel::onNetFiatChange,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(stringResource(R.string.sell_wizard_net_fiat)) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
)

Text(
    stringResource(R.string.sell_wizard_net_preset_label),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    listOf(0.10 to R.string.sell_wizard_net_preset_10,
           0.20 to R.string.sell_wizard_net_preset_20,
           0.50 to R.string.sell_wizard_net_preset_50,
           1.00 to R.string.sell_wizard_net_preset_100).forEach { (factor, label) ->
        FilterChip(
            selected = false,
            onClick = { viewModel.applyNetPreset(factor) },
            label = { Text(stringResource(label)) }
        )
    }
}
```

- [ ] **Krok 3: ViewModel handler**

V `SellWizardViewModel`:

```kotlin
fun applyNetPreset(profitTarget: Double) {
    val st = _state.value
    val a = st.amount.toBigDecimalOrNull() ?: return
    val avg = st.avgBuyPrice.toBigDecimalOrNull() ?: return
    if (avg <= BigDecimal.ZERO || a <= BigDecimal.ZERO) return
    val target = a * avg * (BigDecimal.ONE + BigDecimal(profitTarget))
    onNetFiatChange(target.setScale(2, RoundingMode.HALF_UP).toPlainString())
}
```

- [ ] **Krok 4: Build a commit**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/
git add accbot-android/app/src/main/res/values/strings.xml
git add accbot-android/app/src/main/res/values-cs/strings.xml
git commit -m "feat(sell): net proceeds field + profit-target presets"
```

---

### Task 10: Cenove presety dropdown (% z avg / % ze spotu)

**Files:**
- Modify: `SellWizardBottomSheet.kt`
- Modify: `SellWizardViewModel.kt`
- Modify: `strings.xml` (cs + en)

- [ ] **Krok 1: Stringy**

```xml
<!-- en -->
<string name="sell_wizard_price_preset_mode_avg">% above avg buy</string>
<string name="sell_wizard_price_preset_mode_spot">% above spot</string>

<!-- cs -->
<string name="sell_wizard_price_preset_mode_avg">% z avg buy</string>
<string name="sell_wizard_price_preset_mode_spot">% ze spotu</string>
```

- [ ] **Krok 2: ViewModel state + handlery**

V `SellWizardState`:

```kotlin
val priceP resetMode: PricePresetMode = PricePresetMode.AVG_BUY,

enum class PricePresetMode { AVG_BUY, SPOT }
```

Handlery:

```kotlin
fun onPricePresetModeChange(mode: PricePresetMode) {
    _state.update { it.copy(pricePresetMode = mode) }
}

fun applyPricePreset(factor: Double) {
    val st = _state.value
    val basis = when (st.pricePresetMode) {
        PricePresetMode.AVG_BUY -> st.avgBuyPrice.toBigDecimalOrNull()
        PricePresetMode.SPOT -> st.currentSpot
    } ?: return
    val newPrice = basis * (BigDecimal.ONE + BigDecimal(factor))
    onLimitPriceChange(newPrice.setScale(2, RoundingMode.HALF_UP).toPlainString())
}
```

- [ ] **Krok 3: UI - DropdownMenu nahore presetu**

```kotlin
var modeMenuOpen by remember { mutableStateOf(false) }
Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(when (state.pricePresetMode) {
            PricePresetMode.AVG_BUY -> R.string.sell_wizard_price_preset_mode_avg
            PricePresetMode.SPOT -> R.string.sell_wizard_price_preset_mode_spot
        }),
        modifier = Modifier.clickable { modeMenuOpen = true }
    )
    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sell_wizard_price_preset_mode_avg)) },
            onClick = { viewModel.onPricePresetModeChange(PricePresetMode.AVG_BUY); modeMenuOpen = false }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sell_wizard_price_preset_mode_spot)) },
            onClick = { viewModel.onPricePresetModeChange(PricePresetMode.SPOT); modeMenuOpen = false }
        )
    }
}

Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    listOf(0.05, 0.10, 0.20, 0.50).forEach { factor ->
        FilterChip(
            selected = false,
            onClick = { viewModel.applyPricePreset(factor) },
            label = { Text("+${(factor * 100).toInt()}%") }
        )
    }
}
```

- [ ] **Krok 4: Build a commit**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/
git add accbot-android/app/src/main/res/values/strings.xml
git add accbot-android/app/src/main/res/values-cs/strings.xml
git commit -m "feat(sell): price presets with avg-buy/spot mode toggle"
```

---

### Task 11: Summary - profit per coin, fee, cisty zisk, remaining avg, postup k cili

**Files:**
- Modify: `SellWizardBottomSheet.kt` (summary sekce)
- Modify: `SellWizardViewModel.kt` (computed summary state)
- Modify: `strings.xml`

- [ ] **Krok 1: Pridat computed summary do state**

```kotlin
data class SellSummary(
    val profitPerCoin: BigDecimal? = null,
    val grossProfit: BigDecimal? = null,
    val estimatedFee: BigDecimal? = null,
    val netProfit: BigDecimal? = null,
    val netProfitPct: Double? = null,
    val remainingAfter: RemainingInventory? = null,
    val targetProgressPct: Double? = null
)

val summary: SellSummary = SellSummary()
```

- [ ] **Krok 2: Pridat computeSummary v ViewModelu po revalidate**

```kotlin
private fun computeSummary() {
    val st = _state.value
    val a = st.amount.toBigDecimalOrNull()
    val p = st.limitPrice.toBigDecimalOrNull()
    val avg = st.avgBuyPrice.toBigDecimalOrNull()
    if (a == null || p == null || avg == null || a <= BigDecimal.ZERO) {
        _state.update { it.copy(summary = SellSummary()) }
        return
    }
    val factor = BigDecimal.ONE - st.feeRate
    val grossFiat = a * p
    val estimatedFee = grossFiat * st.feeRate
    val netFiat = grossFiat * factor
    val costBasis = a * avg
    val grossProfit = grossFiat - costBasis
    val netProfit = netFiat - costBasis
    val netProfitPct = if (costBasis > BigDecimal.ZERO) netProfit.toDouble() / costBasis.toDouble() else 0.0

    // hypoteticky pridat ten sell mezi historicke a prepocitat remaining
    val remaining = computeRemainingAfterHypotheticalSell(st.planId, a, p)

    // postup k cili
    val plan = st.plan
    val target = plan?.targetProfitAmount
    val realizedSoFar = computeRealizedPnLSoFar(st.planId)
    val progress = if (target != null && target > BigDecimal.ZERO)
        (realizedSoFar + netProfit).toDouble() / target.toDouble()
    else null

    _state.update {
        it.copy(summary = SellSummary(
            profitPerCoin = p - avg,
            grossProfit = grossProfit,
            estimatedFee = estimatedFee,
            netProfit = netProfit,
            netProfitPct = netProfitPct,
            remainingAfter = remaining,
            targetProgressPct = progress
        ))
    }
}
```

`computeRemainingAfterHypotheticalSell` - vlozit fake SELL transakci do listu transakci a zavolat `CalculatePlanCostBasisUseCase.computeCostBasis(...)`:

```kotlin
private suspend fun computeRemainingAfterHypotheticalSell(
    planId: Long,
    cryptoAmount: BigDecimal,
    price: BigDecimal
): RemainingInventory {
    val txs = database.transactionDao().getTransactionsByPlanSync(planId)
    val fakeSell = TransactionEntity(
        id = -1, planId = planId, connectionId = 0,
        exchange = Exchange.COINMATE, crypto = "?", fiat = "?",
        fiatAmount = cryptoAmount * price,
        cryptoAmount = cryptoAmount,
        price = price, fee = BigDecimal.ZERO, feeAsset = "",
        status = TransactionStatus.COMPLETED,
        exchangeOrderId = "fake",
        executedAt = Instant.now(),
        side = TransactionSide.SELL,
        requestedCryptoAmount = cryptoAmount,
        limitPrice = price
    )
    return CalculatePlanCostBasisUseCase.computeCostBasis(txs + fakeSell)
}
```

`computeRealizedPnLSoFar` - pouzit existujici `CalculatePlanPnLUseCase` (lifetime accounting), ktery uz mame:

```kotlin
private suspend fun computeRealizedPnLSoFar(planId: Long): BigDecimal {
    return calculatePlanPnLUseCase(planId, currentMarketPrice = null).realizedPnL
        ?: BigDecimal.ZERO
}
```

(Pridat `CalculatePlanPnLUseCase` do constructor injection pokud tam neni.)

- [ ] **Krok 3: UI - Summary sekce**

```kotlin
@Composable
fun SellSummarySection(
    summary: SellSummary,
    lossWarning: SellValidation.LossWarning?,
    target: BigDecimal?,
    fiat: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sell_wizard_summary_title), style = MaterialTheme.typography.titleSmall)

        SummaryRow(stringResource(R.string.sell_wizard_summary_profit_per_coin), summary.profitPerCoin, fiat)
        SummaryRow(stringResource(R.string.sell_wizard_summary_gross_profit), summary.grossProfit, fiat)
        SummaryRow(stringResource(R.string.sell_wizard_summary_fee), summary.estimatedFee?.negate(), fiat)
        SummaryRow(
            label = stringResource(R.string.sell_wizard_summary_net_profit),
            value = summary.netProfit,
            fiat = fiat,
            highlightLoss = (summary.netProfit?.signum() ?: 0) < 0,
            extra = summary.netProfitPct?.let { " (${"%+.1f".format(it * 100)}%)" }
        )
        summary.remainingAfter?.let { ri ->
            val avgText = ri.weightedAvgPrice?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "-"
            Text(
                stringResource(R.string.sell_wizard_summary_remaining_after) +
                    ": ${ri.available.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()} @ $avgText"
            )
        }
        if (target != null && summary.targetProgressPct != null) {
            Text(
                stringResource(R.string.sell_wizard_summary_target_progress) +
                    ": ${"%.0f".format(summary.targetProgressPct * 100)}%"
            )
            LinearProgressIndicator(
                progress = { summary.targetProgressPct.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: BigDecimal?,
    fiat: String,
    highlightLoss: Boolean = false,
    extra: String? = null
) {
    if (value == null) return
    val color = if (highlightLoss) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${value.setScale(2, RoundingMode.HALF_UP).toPlainString()} $fiat${extra ?: ""}",
            color = color
        )
    }
}
```

- [ ] **Krok 4: Stringy**

```xml
<!-- en -->
<string name="sell_wizard_summary_title">Summary</string>
<string name="sell_wizard_summary_profit_per_coin">Profit per coin</string>
<string name="sell_wizard_summary_gross_profit">Gross profit</string>
<string name="sell_wizard_summary_fee">Estimated fee</string>
<string name="sell_wizard_summary_net_profit">Net profit (after fee)</string>
<string name="sell_wizard_summary_remaining_after">After this sell</string>
<string name="sell_wizard_summary_target_progress">Plan target progress</string>

<!-- cs (anglicke radky uz mas, nahradit) -->
<string name="sell_wizard_summary_title">Souhrn</string>
<string name="sell_wizard_summary_profit_per_coin">Zisk na coin</string>
<string name="sell_wizard_summary_gross_profit">Hruby zisk</string>
<string name="sell_wizard_summary_fee">Odhad fee</string>
<string name="sell_wizard_summary_net_profit">Cisty zisk (po fee)</string>
<string name="sell_wizard_summary_remaining_after">Po prodeji</string>
<string name="sell_wizard_summary_target_progress">Postup k cili planu</string>
```

- [ ] **Krok 5: Build a commit**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
git add ...
git commit -m "feat(sell): rich summary with profit, fee, remaining inventory, target progress"
```

---

### Task 12: Loss warning banner

**Files:**
- Modify: `SellWizardBottomSheet.kt`
- Modify: `strings.xml`

- [ ] **Krok 1: Stringy**

```xml
<!-- en -->
<string name="sell_wizard_loss_below_buy">You are selling below your buy price: %1$s</string>
<string name="sell_wizard_loss_after_fee">After fee, you are selling at a loss: %1$s</string>

<!-- cs -->
<string name="sell_wizard_loss_below_buy">Prodavas pod nakupni cenou: %1$s</string>
<string name="sell_wizard_loss_after_fee">Po fee prodavas se ztratou: %1$s</string>
```

- [ ] **Krok 2: Banner v UI mezi summary a tlacitkem Pokracovat**

```kotlin
state.lossWarning?.let { warn ->
    val limitPrice = state.limitPrice.toBigDecimalOrNull()
    val avg = state.avgBuyPrice.toBigDecimalOrNull()
    val isPriceBelowAvg = limitPrice != null && avg != null && limitPrice < avg
    val resId = if (isPriceBelowAvg) R.string.sell_wizard_loss_below_buy
                else R.string.sell_wizard_loss_after_fee
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null,
                 tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(resId, formatFiat(warn.lossFiat, state.fiat)),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
```

- [ ] **Krok 3: Build a commit**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
git add ...
git commit -m "feat(sell): loss warning banner triggered by net-profit < 0"
```

---

### Task 13: Manualni overeni single mod konci-konce

Bez code zmen. Otevrit appku v emulatoru / fyzickem zarizeni:

- [ ] **Krok 1: Build debug APK**

```bash
cd accbot-android && ./gradlew :app:assembleDebug
```

- [ ] **Krok 2: Nainstalovat na zarizeni**

```bash
adb install -r accbot-android/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Krok 3: Scenare pro overeni single modu**

- **Plan s buys**: otevrit sell wizard, avg pole prefilled. Zkusit zmenit, reset pres tlacitko. Vrati se k auto.
- **Prazdny plan (zadne buys)**: otevrit sell wizard, avg pole prazdne, helper text "Zadej rucne". Vyplnit, validace projde.
- **Editovat A a P**: N se dopocita. Editovat A a N: P se dopocita. Editovat P a N: A se dopocita.
- **Cenove presety**: AVG mode, +10% -> P = avg × 1.10. Prepnout na SPOT mode, +10% -> P = spot × 1.10.
- **Net presety**: +20% -> N = A × avg × 1.20.
- **Loss case**: zadat P = avg - 1, banner "Prodavas pod nakupni cenou". Zadat P tesne nad avg (~ 0.3% nad), banner "Po fee se ztratou".
- **Summary**: vsechny radky prochazeji, profit cervene pri ztrate, target progress jen pokud `targetProfitAmount` na planu nastaveno.

- [ ] **Krok 4: Pripadne opravy**

Pokud nektery scenar selhe, opravit konkretni bug. Maly commit `fix(sell): ...`.

---

## Faze 6: Ladder mode

### Task 14: PlaceLadderSellUseCase

**Files:**
- Create: `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/PlaceLadderSellUseCase.kt`

- [ ] **Krok 1: Implementovat use case**

```kotlin
package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.local.toEntity
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.exchange.ExchangeApiFactory
import java.math.BigDecimal
import javax.inject.Inject

data class LadderOrder(val cryptoAmount: BigDecimal, val limitPrice: BigDecimal)

sealed class LadderResult {
    data class AllPlaced(val placedTxIds: List<Long>) : LadderResult()
    data class PartialFailure(
        val placedTxIds: List<Long>,
        val failedAtIndex: Int,
        val totalCount: Int,
        val reason: String
    ) : LadderResult()
}

class PlaceLadderSellUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
) {
    suspend operator fun invoke(
        planId: Long,
        orders: List<LadderOrder>
    ): LadderResult {
        if (orders.size < 2) return LadderResult.PartialFailure(
            emptyList(), 0, orders.size, "Ladder vyzaduje aspon 2 ordery"
        )

        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: return LadderResult.PartialFailure(emptyList(), 0, orders.size, "Plan neexistuje")
        val credentials = credentialsStore.getCredentials(
            plan.connectionId, userPreferences.isSandboxMode()
        ) ?: return LadderResult.PartialFailure(emptyList(), 0, orders.size, "Chybi credentials")

        val api = exchangeApiFactory.create(credentials)
        val placed = mutableListOf<Long>()

        orders.forEachIndexed { idx, order ->
            val result = api.limitSell(plan.crypto, plan.fiat, order.cryptoAmount, order.limitPrice)
            when (result) {
                is DcaResult.Success -> {
                    val tx = result.transaction.copy(planId = planId, connectionId = plan.connectionId)
                    val id = database.transactionDao().insertTransaction(tx.toEntity())
                    placed += id
                }
                is DcaResult.Error -> {
                    return LadderResult.PartialFailure(placed, idx, orders.size, result.message)
                }
            }
        }

        try { resolvePendingTransactionsUseCase() } catch (_: Exception) {}
        return LadderResult.AllPlaced(placed)
    }
}
```

- [ ] **Krok 2: Build check**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
```

- [ ] **Krok 3: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/PlaceLadderSellUseCase.kt
git commit -m "feat(sell): PlaceLadderSellUseCase with stop-and-report failure handling"
```

---

### Task 15: Ladder generator helper + testy

**Files:**
- Create: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/LadderGenerator.kt`
- Create: `accbot-android/app/src/test/java/com/accbot/dca/presentation/screens/plans/sell/LadderGeneratorTest.kt`

- [ ] **Krok 1: Implementovat generator**

```kotlin
package com.accbot.dca.presentation.screens.plans.sell

import com.accbot.dca.domain.usecase.LadderOrder
import java.math.BigDecimal
import java.math.RoundingMode

object LadderGenerator {

    enum class AmountMode { EQUAL_CRYPTO, EQUAL_FIAT }

    /**
     * Generuje N orderu s linearne rozprostrenymi cenami od `from` do `to`.
     * @param totalAmount celkove crypto k prodeji
     * @param mode rozdeleni mezi ordery
     */
    fun generate(
        totalAmount: BigDecimal,
        from: BigDecimal,
        to: BigDecimal,
        count: Int,
        mode: AmountMode
    ): List<LadderOrder> {
        require(count >= 2) { "count >= 2" }
        require(totalAmount > BigDecimal.ZERO) { "totalAmount > 0" }
        require(from > BigDecimal.ZERO && to > BigDecimal.ZERO) { "ceny > 0" }

        val prices = (0 until count).map { i ->
            from + (to - from) * BigDecimal(i) / BigDecimal(count - 1)
        }

        return when (mode) {
            AmountMode.EQUAL_CRYPTO -> {
                val per = totalAmount.divide(BigDecimal(count), 8, RoundingMode.DOWN)
                // Korekce zaokrouhleni na poslednim orderu (zbyle drobky pridat)
                val drobky = totalAmount - per * BigDecimal(count)
                prices.mapIndexed { i, p ->
                    val a = if (i == count - 1) per + drobky else per
                    LadderOrder(a, p.setScale(2, RoundingMode.HALF_UP))
                }
            }
            AmountMode.EQUAL_FIAT -> {
                val totalGross = (prices.sum()) * totalAmount / BigDecimal(count)  // pro odhad equal fiat
                // Equal fiat: kazdy order vygeneruje stejny gross fiat (totalGross / N)
                // amount_i = (totalGross / N) / price_i
                val perOrderFiat = totalGross.divide(BigDecimal(count), 8, RoundingMode.HALF_UP)
                val amounts = prices.map { p ->
                    perOrderFiat.divide(p, 8, RoundingMode.DOWN)
                }
                val sumAmounts = amounts.fold(BigDecimal.ZERO) { acc, x -> acc + x }
                // Skalovat aby suma sedela na totalAmount
                val scale = if (sumAmounts > BigDecimal.ZERO) totalAmount.divide(sumAmounts, 8, RoundingMode.HALF_UP)
                            else BigDecimal.ONE
                amounts.mapIndexed { i, a ->
                    val scaled = (a * scale).setScale(8, RoundingMode.DOWN)
                    LadderOrder(scaled, prices[i].setScale(2, RoundingMode.HALF_UP))
                }
            }
        }
    }

    private fun List<BigDecimal>.sum(): BigDecimal = fold(BigDecimal.ZERO) { acc, x -> acc + x }
}
```

- [ ] **Krok 2: Testy**

```kotlin
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
        // ceny linearne: 2.0M, 2.1M, 2.2M, 2.3M, 2.4M
        assertEquals(0, BigDecimal("2000000.00").compareTo(orders[0].limitPrice))
        assertEquals(0, BigDecimal("2400000.00").compareTo(orders[4].limitPrice))
        // mnozstvi: kazdy 0.2 BTC, suma = 1
        val total = orders.fold(BigDecimal.ZERO) { acc, o -> acc + o.cryptoAmount }
        assertEquals(0, BigDecimal("1.00000000").compareTo(total))
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
        // levnejsi (index 0) -> vetsi mnozstvi
        assertTrue(orders[0].cryptoAmount > orders[3].cryptoAmount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `count menez nez 2 hodi exception`() {
        LadderGenerator.generate(BigDecimal("1"), BigDecimal("1000"), BigDecimal("2000"), 1, AmountMode.EQUAL_CRYPTO)
    }
}
```

- [ ] **Krok 3: Spustit testy**

```bash
cd accbot-android && ./gradlew :app:testDebugUnitTest --tests "com.accbot.dca.presentation.screens.plans.sell.LadderGeneratorTest"
```

Expected: 3 testy PASS.

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/LadderGenerator.kt
git add accbot-android/app/src/test/java/com/accbot/dca/presentation/screens/plans/sell/LadderGeneratorTest.kt
git commit -m "feat(sell): LadderGenerator pure helper for linear scale-out"
```

---

### Task 16: Ladder validation v ValidateSellOrderUseCase

**Files:**
- Modify: `ValidateSellOrderUseCase.kt`

- [ ] **Krok 1: Pridat ladder validate metodu**

V tride pridat:

```kotlin
suspend fun validateLadder(
    planId: Long,
    orders: List<LadderOrder>,
    minOrderSize: BigDecimal,
    avgBuyPrice: BigDecimal?,
    feeRate: BigDecimal,
    currentSpot: BigDecimal?
): List<SellValidation> {
    val total = orders.fold(BigDecimal.ZERO) { acc, o -> acc + o.cryptoAmount }
    val baseValidation = invoke(
        planId = planId,
        cryptoAmount = total,
        limitPrice = orders.firstOrNull()?.limitPrice ?: BigDecimal.ONE,  // proxy pro available check
        minOrderSize = orders.minOf { it.cryptoAmount },
        currentSpot = null,                  // skip instant-fill / far-from-market u ladderu
        avgBuyPrice = avgBuyPrice,
        feeRate = feeRate
    ).filter {
        // Vyhodit instant-fill / far-from-market warningy z proxy validace
        it !is SellValidation.InstantFillInfo && it !is SellValidation.FarFromMarketWarning
    }.toMutableList()

    // Aggregated loss warning
    val totalLoss = orders.fold(BigDecimal.ZERO) { acc, o ->
        val l = checkLoss(o.cryptoAmount, o.limitPrice, avgBuyPrice, feeRate)?.lossFiat ?: BigDecimal.ZERO
        acc + l
    }
    if (totalLoss > BigDecimal.ZERO) {
        baseValidation += SellValidation.LossWarning(totalLoss, 0.0)
    }

    // Per-order minOrderSize check je uz v invoke pres `minOf` - OK
    // Far-from-market: kdyz prvni cena (nejnizsi) > 3 × spot
    if (currentSpot != null && orders.first().limitPrice > currentSpot * BigDecimal("3")) {
        baseValidation += SellValidation.FarFromMarketWarning(currentSpot)
    }

    return baseValidation.ifEmpty { listOf(SellValidation.Ok) }
}
```

- [ ] **Krok 2: Build a commit**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
git add accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCase.kt
git commit -m "feat(sell): ladder validation with aggregated loss + edge checks"
```

---

### Task 17: SellWizardViewModel - ladder state

**Files:**
- Modify: `SellWizardViewModel.kt`

- [ ] **Krok 1: Rozsirit state**

```kotlin
data class SellWizardState(
    // ...
    val ladderEnabled: Boolean = false,
    val ladderRangeMode: LadderRangeMode = LadderRangeMode.PRICE,
    val ladderFrom: String = "",
    val ladderTo: String = "",
    val ladderCount: String = "5",
    val ladderAmountMode: LadderGenerator.AmountMode = LadderGenerator.AmountMode.EQUAL_CRYPTO,
    val ladderPreview: List<LadderOrder> = emptyList()
)

enum class LadderRangeMode { PRICE, PROFIT_PCT }
```

- [ ] **Krok 2: Pridat ladder handlery**

```kotlin
fun onLadderEnabledChange(enabled: Boolean) {
    _state.update { it.copy(ladderEnabled = enabled) }
    recomputeLadderPreview()
}

fun onLadderRangeModeChange(mode: LadderRangeMode) {
    _state.update { it.copy(ladderRangeMode = mode) }
    recomputeLadderPreview()
}

fun onLadderFromChange(text: String) {
    _state.update { it.copy(ladderFrom = text) }
    recomputeLadderPreview()
}

fun onLadderToChange(text: String) {
    _state.update { it.copy(ladderTo = text) }
    recomputeLadderPreview()
}

fun onLadderCountChange(text: String) {
    _state.update { it.copy(ladderCount = text) }
    recomputeLadderPreview()
}

fun onLadderAmountModeChange(mode: LadderGenerator.AmountMode) {
    _state.update { it.copy(ladderAmountMode = mode) }
    recomputeLadderPreview()
}

private fun recomputeLadderPreview() {
    val st = _state.value
    if (!st.ladderEnabled) {
        _state.update { it.copy(ladderPreview = emptyList()) }
        return
    }
    val total = st.amount.toBigDecimalOrNull() ?: return
    val count = st.ladderCount.toIntOrNull() ?: return
    if (count < 2) return

    val avg = st.avgBuyPrice.toBigDecimalOrNull()
    val (from, to) = when (st.ladderRangeMode) {
        LadderRangeMode.PRICE -> {
            val f = st.ladderFrom.toBigDecimalOrNull() ?: return
            val t = st.ladderTo.toBigDecimalOrNull() ?: return
            f to t
        }
        LadderRangeMode.PROFIT_PCT -> {
            if (avg == null) return
            val fPct = st.ladderFrom.toBigDecimalOrNull() ?: return
            val tPct = st.ladderTo.toBigDecimalOrNull() ?: return
            (avg * (BigDecimal.ONE + fPct / BigDecimal("100"))) to
                (avg * (BigDecimal.ONE + tPct / BigDecimal("100")))
        }
    }
    if (to <= from) return

    val orders = LadderGenerator.generate(total, from, to, count, st.ladderAmountMode)
    _state.update { it.copy(ladderPreview = orders) }
}
```

- [ ] **Krok 3: Build a commit**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
git add ...
git commit -m "feat(sell): ladder state machine in SellWizardViewModel"
```

---

### Task 18: Ladder UI - checkbox, range, count, toggles, preview tabulka

**Files:**
- Modify: `SellWizardBottomSheet.kt`
- Modify: `strings.xml`

- [ ] **Krok 1: Stringy**

```xml
<!-- en -->
<string name="sell_wizard_ladder_enable">Create multiple sell orders</string>
<string name="sell_wizard_ladder_from">From</string>
<string name="sell_wizard_ladder_to">To</string>
<string name="sell_wizard_ladder_count">Number of orders</string>
<string name="sell_wizard_ladder_range_price">Price</string>
<string name="sell_wizard_ladder_range_profit">Profit %</string>
<string name="sell_wizard_ladder_amount_equal_crypto">Equal crypto</string>
<string name="sell_wizard_ladder_amount_equal_fiat">Equal fiat</string>
<string name="sell_wizard_ladder_preview_total">Total at full fill</string>

<!-- cs -->
<string name="sell_wizard_ladder_enable">Vytvorit vice sell orderu</string>
<string name="sell_wizard_ladder_from">Od</string>
<string name="sell_wizard_ladder_to">Do</string>
<string name="sell_wizard_ladder_count">Pocet orderu</string>
<string name="sell_wizard_ladder_range_price">Cena</string>
<string name="sell_wizard_ladder_range_profit">Profit %</string>
<string name="sell_wizard_ladder_amount_equal_crypto">Stejne crypto</string>
<string name="sell_wizard_ladder_amount_equal_fiat">Stejny fiat</string>
<string name="sell_wizard_ladder_preview_total">Celkem pri plnem fillu</string>
```

- [ ] **Krok 2: UI - checkbox + ladder block (visible jen kdyz enabled)**

```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(
        checked = state.ladderEnabled,
        onCheckedChange = viewModel::onLadderEnabledChange
    )
    Text(stringResource(R.string.sell_wizard_ladder_enable))
}

if (state.ladderEnabled) {
    // Range mode toggle (Cena / Profit %)
    SegmentedButton(
        options = listOf(
            LadderRangeMode.PRICE to stringResource(R.string.sell_wizard_ladder_range_price),
            LadderRangeMode.PROFIT_PCT to stringResource(R.string.sell_wizard_ladder_range_profit)
        ),
        selected = state.ladderRangeMode,
        onSelect = viewModel::onLadderRangeModeChange
    )

    Row {
        OutlinedTextField(
            value = state.ladderFrom,
            onValueChange = viewModel::onLadderFromChange,
            label = { Text(stringResource(R.string.sell_wizard_ladder_from)) },
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.ladderTo,
            onValueChange = viewModel::onLadderToChange,
            label = { Text(stringResource(R.string.sell_wizard_ladder_to)) },
            modifier = Modifier.weight(1f)
        )
    }

    OutlinedTextField(
        value = state.ladderCount,
        onValueChange = viewModel::onLadderCountChange,
        label = { Text(stringResource(R.string.sell_wizard_ladder_count)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    SegmentedButton(
        options = listOf(
            LadderGenerator.AmountMode.EQUAL_CRYPTO to stringResource(R.string.sell_wizard_ladder_amount_equal_crypto),
            LadderGenerator.AmountMode.EQUAL_FIAT to stringResource(R.string.sell_wizard_ladder_amount_equal_fiat)
        ),
        selected = state.ladderAmountMode,
        onSelect = viewModel::onLadderAmountModeChange
    )

    LadderPreviewTable(
        orders = state.ladderPreview,
        avg = state.avgBuyPrice.toBigDecimalOrNull(),
        feeRate = state.feeRate
    )
}
```

(Pokud `SegmentedButton` nemate, pouzit FilterChip Row alternativu.)

- [ ] **Krok 3: Composable LadderPreviewTable**

```kotlin
@Composable
fun LadderPreviewTable(orders: List<LadderOrder>, avg: BigDecimal?, feeRate: BigDecimal) {
    if (orders.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("#", modifier = Modifier.weight(0.5f))
            Text(stringResource(R.string.sell_wizard_summary_amount), modifier = Modifier.weight(1.5f))
            Text(stringResource(R.string.sell_wizard_limit_price), modifier = Modifier.weight(2f))
            Text("Profit %", modifier = Modifier.weight(1.5f))
            Text(stringResource(R.string.sell_wizard_summary_net_profit), modifier = Modifier.weight(2f))
        }
        Divider()

        var totalNet = BigDecimal.ZERO
        orders.forEachIndexed { i, o ->
            val gross = o.cryptoAmount * o.limitPrice
            val net = gross * (BigDecimal.ONE - feeRate)
            val profitPct = if (avg != null && avg > BigDecimal.ZERO)
                (o.limitPrice - avg).divide(avg, 4, RoundingMode.HALF_UP).movePointRight(2)
            else null
            totalNet = totalNet + (net - o.cryptoAmount * (avg ?: BigDecimal.ZERO))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${i + 1}", modifier = Modifier.weight(0.5f))
                Text(o.cryptoAmount.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString(),
                     modifier = Modifier.weight(1.5f))
                Text(o.limitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                     modifier = Modifier.weight(2f))
                Text(profitPct?.toPlainString()?.let { "$it%" } ?: "-",
                     modifier = Modifier.weight(1.5f))
                Text(net.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                     modifier = Modifier.weight(2f))
            }
        }
        Divider()
        Text(
            "${stringResource(R.string.sell_wizard_ladder_preview_total)}: " +
                totalNet.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            style = MaterialTheme.typography.titleSmall
        )
    }
}
```

- [ ] **Krok 4: V ladder modu skryt singl-mode pole `Cisty vynos` a presety**

V krocich kde se rendruje single mode UI: obalit do `if (!state.ladderEnabled) { ... }`.

Limit price pole zustava (single nazev), v ladder modu se schova a zobrazi se `Od/Do`.

- [ ] **Krok 5: Build a commit**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
git add ...
git commit -m "feat(sell): ladder UI with range, count, amount-mode toggle, preview table"
```

---

### Task 19: Ladder submit flow + post-submit dialog

**Files:**
- Modify: `SellWizardViewModel.kt`
- Modify: `SellWizardBottomSheet.kt`
- Modify: `strings.xml`

- [ ] **Krok 1: Stringy**

```xml
<!-- en -->
<string name="sell_wizard_ladder_partial_success">Placed %1$d of %2$d orders. Remaining did not proceed: %3$s</string>
<string name="sell_wizard_ladder_all_placed">All %1$d orders placed</string>

<!-- cs -->
<string name="sell_wizard_ladder_partial_success">Vytvoreno %1$d z %2$d orderu. Zbyvajici nepokracovaly: %3$s</string>
<string name="sell_wizard_ladder_all_placed">Vsech %1$d orderu vytvoreno</string>
```

- [ ] **Krok 2: ViewModel - submit ladder**

```kotlin
@Inject lateinit var placeLadderSellUseCase: PlaceLadderSellUseCase

suspend fun submitLadder(): LadderResult? {
    val st = _state.value
    if (!st.ladderEnabled || st.ladderPreview.isEmpty()) return null
    return placeLadderSellUseCase(st.planId, st.ladderPreview)
}
```

- [ ] **Krok 3: Krok 2 wizardu - rozliseni single vs ladder pri submitu**

V kroku 2 (potvrzeni):

```kotlin
val coroutineScope = rememberCoroutineScope()
Button(onClick = {
    coroutineScope.launch {
        if (state.ladderEnabled) {
            val result = viewModel.submitLadder()
            // ukazat dialog dle vysledku
            when (result) {
                is LadderResult.AllPlaced -> showDialog(R.string.sell_wizard_ladder_all_placed, result.placedTxIds.size)
                is LadderResult.PartialFailure -> showDialog(
                    R.string.sell_wizard_ladder_partial_success,
                    result.placedTxIds.size, result.totalCount, result.reason
                )
                null -> {}
            }
            onDismiss()
        } else {
            viewModel.submitSingle()
            onDismiss()
        }
    }
}) {
    Text(stringResource(R.string.sell_wizard_submit))
}
```

- [ ] **Krok 4: Build a commit**

```bash
cd accbot-android && ./gradlew :app:compileDebugKotlin
git add ...
git commit -m "feat(sell): ladder submit flow + partial-success dialog"
```

---

### Task 20: Manualni overeni ladder modu

Bez code zmen.

- [ ] **Krok 1: Build & install debug APK**

```bash
cd accbot-android && ./gradlew :app:assembleDebug && \
adb install -r accbot-android/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Krok 2: Scenare**

- **Aktivace ladder**: zaskrtnout, single pole se schova, ladder pole se ukaze, preview prazdne.
- **Zadat range cena**: from=2000000, to=2400000, count=5, total amount=1 BTC. Preview ukaze 5 orderu po 0.2 BTC s rovnomerne rozprostrenymi cenami.
- **Toggle profit %**: prepnout, pole `Od/Do` jsou ted procenta nad avg. Zadat 10/30, preview vyrenderuje ceny avg×1.10 az avg×1.30.
- **Toggle equal-fiat**: preview se prerovna - levnejsi ordery vetsi mnozstvi.
- **Submit**: kliknout Pokracovat, Krok 2 zobrazi preview + agregat. Submit -> burza dostane N orderu.
- **Stop & report**: jak otestovat? Coinmate sandbox neexistuje, fakov failure jde:
  - Zadat ladder s prilis malymi mnozstvimi (under minOrderSize) - validace by mela zachytit pred submitem.
  - Pokud chces realny stop&report test: nastavit `to` velmi vysoko (mimo limity burzy), 1-2 ordery proveddu, zbytek fail.
- **Plan delete**: po vytvoreni ladderu zkusit smazat plan. Mel by byt blokovany (existujici Task 31 logiky).

- [ ] **Krok 3: Pripadne opravy**

Maly commit `fix(sell): ...` per opravu.

---

## Faze 7: E2E zacleneni

### Task 21: Aktualizace E2E checklistu pro Task 33 / Task 34 z puvodniho planu

**Files:**
- Modify: `docs/superpowers/plans/2026-04-23-dca-sell-extension.md`

- [ ] **Krok 1: V Task 33 (Coinmate manualni sandbox test) doplnit nove scenare:**

```markdown
**Scenar E - cost basis prefill:**
- Otevrit sell wizard na planu s 3 buys ruznych cen
- Overit ze avg buy price prefilled, hodnota matematicky odpovida cheapest-first vypoctu
- Manualni override -> zmeni se hodnota, "✏️ Zadano rucne" indikator
- Reset -> vrati auto

**Scenar F - 3-pole kalkulacka:**
- Zadat A a P, N se dopocita
- Zadat A a N, P se dopocita
- Cenovy preset +20% z avg -> P = avg × 1.20
- Net preset +20% -> N = A × avg × 1.20

**Scenar G - loss warning:**
- Zadat P pod avg buy -> banner "Prodavas pod nakupni cenou", cervene profit
- Zadat P tesne nad avg (~0.3%) -> banner "Po fee se ztratou"

**Scenar H - ladder mode:**
- Zaskrtnout checkbox, zadat from/to/count, total amount
- Preview tabulka renderuje N orderu
- Toggle equal-fiat -> uneven crypto amounts
- Submit -> N PENDING SELL transakci na burze, vsechny v plan-detail open orders

**Scenar I - ladder partial failure:**
- Zadat ladder mimo limity Coinmate (extremne vysoky `to`)
- Submit -> dialog "Vytvoreno X z N orderu, zbyvajici: <reason>"
- Plan-detail ukazuje X PENDING orderu
```

- [ ] **Krok 2: V Task 34 (Binance) totez (analogicke scenare E-I)**

- [ ] **Krok 3: Commit**

```bash
git add docs/superpowers/plans/2026-04-23-dca-sell-extension.md
git commit -m "docs(sell): extend Task 33/34 E2E with cost-basis + ladder scenarios"
```

---

## Summary

**Celkem tasku:** 21
**Predpokladany rozsah:** 2-3 dny pro experienced Kotlin/Compose dev. Vetsina komplexity je v UI state machine a v korektnim provazani s existujicimi flowy.

**Kriticke zavislosti v poradi:**
- Task 1-3 (cost basis foundation) MUSI byt hotove pred 7+ (ViewModel)
- Task 4 (fee plumbing) potreba pred 5 (loss check) a 7 (calculator)
- Task 14-16 (ladder use case + generator + validation) pred 17-19 (UI)
- Tasky 13 a 20 (manualni testy) konci jednotlivych mod
- Task 21 (E2E zacleneni) jen po vsem

**TDD pokryti:**
- `CalculatePlanCostBasisUseCase.computeCostBasis` - 9 testu
- `ValidateSellOrderUseCase.checkLoss` - 4 testy
- `SellCalculatorMath.recompute` - 5 testu
- `LadderGenerator.generate` - 3 testy
- UI a ViewModel state machine - manualni testy v Task 13 / Task 20

**Co se NEmeni:**
- DB schema, migrace
- Backup/restore
- `CalculatePlanPnLUseCase`
- `SellPollingWorker`, `ResolvePendingTransactionsUseCase`
- `PlaceLimitSellUseCase` (ladder = vlastni cesta)

**Out of scope (viz spec):** cache, snapshot avg na sell, hard block na loss, geometric distribuce, atomic batch, persistovane preset preferences.
