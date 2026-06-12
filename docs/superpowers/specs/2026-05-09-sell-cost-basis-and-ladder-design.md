# Sell wizard - cost basis kalkulacka a ladder mode

**Datum:** 2026-05-09
**Branch:** feature/dca-sell-extension
**Status:** Navrh ke schvaleni

## Motivace

Rucni nastaveni sellu otevira prostor emocionalnimu rozhodovani (panic sell, FOMO, anchoring). Soucasny sell wizard pracuje jen s "kolik a za kolik", bez kontextu kolik koin si uzivatel poridil za jakou cenu. Uzivatel tak nevidi, jestli dnesni cena je nad/pod jeho cost basis a jak by sell ovlivnil zbyvajici prumernou nakupni cenu.

Tato funkce pridava:

1. **Vypocteny remaining cost basis** v sell wizardu (timestamp-aware cheapest-first), s moznosti manualniho prepsani.
2. **Tripolovou kalkulacku** (mnozstvi / limit cena / cisty vynos) s automatickym doplnovanim treti hodnoty z dvou zadanych.
3. **Profit preview** v summary vcetne "remaining avg po prodeji".
4. **Loss warning** banner pro prodeje pod cost basis.
5. **Volitelny ladder mod** (checkbox "Vytvorit vice sell orderu") pro pre-commitovane scale-out strategie.

Cil: uzivatel vidi v okamziku zadani vsechna relevantni cisla a rozhoduje se na zaklade faktu, ne emoci.

## Pozadi v kodu

- Sell wizard: `accbot-android/.../presentation/screens/plans/sell/SellWizardBottomSheet.kt` + `SellWizardViewModel.kt`
- Validace: `domain/usecase/ValidateSellOrderUseCase.kt` (vraci sealed `SellValidation`, vicenasobne vysledky v listu)
- Provedeni: `domain/usecase/PlaceLimitSellUseCase.kt` (dnes 1 order, vlozi PENDING SELL transakci)
- PnL: `domain/usecase/CalculatePlanPnLUseCase.kt` (lifetime accounting - **zustava beze zmeny**)
- Polling: `worker/SellPollingWorker.kt` (synchronizace z burzy - **zustava beze zmeny**)
- Schema: `TransactionEntity` ma `side`, `cryptoAmount`, `requestedCryptoAmount`, `executedAt`, `status` - vse uz existuje. **Zadna migrace.**

## Navrh

### 1. Cost basis algoritmus (timestamp-aware cheapest-first)

Novy use case `CalculatePlanCostBasisUseCase`, cista funkce.

**Vstup:** `planId: Long`

**Postup:**

1. Nacist vsechny transakce planu (BUY + SELL).
2. Vyfiltrovat relevantni stavy:
   - BUYs: `COMPLETED` nebo `PARTIAL`
   - SELLs: `COMPLETED`, `PARTIAL`, `PENDING` (pending blokuji inventar)
3. Inicializovat `consumed[buyId] = 0` pro kazdy buy.
4. Pro kazdy sell v poradi podle `executedAt ASC`:
   - Filtr buyu, ktere maji `buy.executedAt < sell.executedAt` AND `buy.cryptoAmount - consumed[buy.id] > 0` (zbyva nezkonzumovana cast).
   - Seradit ASC podle `buy.price`. Tie-break: starsi `executedAt` napred.
   - Cilova konzumace:
     - COMPLETED/PARTIAL sell: `sell.cryptoAmount`
     - PENDING/PARTIAL sell: `sell.requestedCryptoAmount - sell.cryptoAmount` (= unfilled reservation)
   - Konzumovat sekvencne: pro kazdy buy v poradi `take = min(remaining_in_buy, remaining_to_consume)`, zvysit `consumed[buy.id] += take`, snizit `remaining_to_consume -= take`. Pokud po vycerpani vsech eligible buyu zbyva `remaining_to_consume > 0` -> spadne pod edge case "negative inventory" (vraci se ve vystupu).
5. Po projiti vsech sells:
   - `remainingPerBuy[buy] = buy.cryptoAmount - consumed[buy.id]` (ulozit jen kde > 0)
   - `available = sum(remainingPerBuy)`
   - `weightedAvgPrice = available > 0 ? sum(remaining × buy.price) / available : null`

**Vystup:**

```kotlin
data class RemainingInventory(
    val available: BigDecimal,            // sum zbyvajicich crypto
    val weightedAvgPrice: BigDecimal?,    // null pokud available == 0
    val perBuyDetail: List<RemainingBuy>, // pro debug / future features
    val deficit: BigDecimal               // > 0 pokud sells presahly buys
)
```

**Vlastnosti:**

- Plne stateless. Zadna DB schema zmena, zadna persistence, zadny backup tweak.
- Stabilni vuci novym buyum: novy buy s `executedAt > existing_sells` ma `consumed = 0`, plne se zapocita do remaining.
- Reservace z PENDING/PARTIAL nezdvoji prodej cheap inventory.
- Performance: `O(sells × buys × log(buys))` na sort. Pro 2000 buys + 50 sells ~ 1M ops, jednotky ms. **Cache je YAGNI pro v1.**

### 2. Tripolova kalkulacka

**Pole** v sell wizardu Krok 1:

| Pole | Symbol | Vyznam |
|---|---|---|
| Avg buy price | `avg` | cost basis (prefill z algoritmu, editovatelny) |
| Mnozstvi crypto | `A` | kolik prodat |
| Limit cena | `P` | fiat / crypto |
| Cisty vynos | `N` | fiat na ucet po fee |

**Vztah:** `N = A × P × (1 - feeRate)`

**Logika kalkulacky:**

ViewModel si pamatuje `lastTwoEdited: Pair<Field, Field>` (FIFO poradi mezi A/P/N). Kdyz uzivatel napise hodnotu do pole `X`:

1. Pridat `X` na konec `lastTwoEdited`, vyhodit nejstarsi.
2. Pokud jsou vsechna 3 pole vyplnena: dopocitat to, ktere NENI v `lastTwoEdited`.
3. Pokud jen 2 jsou vyplnena: dopocitat 3.
4. Pokud jen 1: nedelat nic.

**Rovnice:**

- `(A, P) -> N = A × P × (1 - feeRate)`
- `(A, N) -> P = N / (A × (1 - feeRate))`
- `(P, N) -> A = N / (P × (1 - feeRate))`

**Avg pole je separatni vstup**, neni soucasti 3-pole kalkulacky (nemeni A/P/N primo, jen ovlivnuje profit ve summary). Tlacitko "Spocitat z planu" resetuje na auto-prefill z `CalculatePlanCostBasisUseCase`.

### 3. Fee plumbing

Rozsirit `ExchangeApi` interface o:

```kotlin
val estimatedTakerFeeRate: BigDecimal
```

Hodnoty:

| Burza | feeRate | Zdroj |
|---|---|---|
| Coinmate | 0.0035 | dnes hardcoded v `CoinmateApi.kt:32` |
| Binance | 0.001 | default taker, ignoruje BNB/VIP discounty |
| KuCoin | 0.001 | default taker |
| Coinbase | 0.0040 | advanced trade base tier |
| Kraken | 0.0026 | base tier |
| Bitfinex / Huobi | 0.002 | placeholder, validace stejne dnes vraci `false` |

V summary radek `Odhadovany fee: X CZK (0.35%)` pro transparentnost. Pokud uzivatel ma nizsi fee tier nebo BNB discount, dostane mirne vic - to je akceptovatelne pro decision support.

### 4. Cenove a vynosove presety

**Pod polem `Limit cena`** dropdown menu s rezimem:

- **% z avg buy** (default): `P = avg × (1 + preset)`. Hodnoty: +5%, +10%, +20%, +50%.
- **% ze spotu**: `P = spot × (1 + preset)`. Stejne hodnoty.

Toggle se ulozi pro session (transient, neperzistovat).

**Pod polem `Mnozstvi`:** zachovat existujici 25% / 50% / 75% / 100% z `available`.

**Pod polem `Cisty vynos`:** presety relativni k cost basis. Cil = "kolik chci na transakci vydelat".

`N = A × avg × (1 + profitTarget)` (cislo, ktere by mi prislo na ucet, kdyby fee byl 0; system pak dopocita `P` zpetne pres `P = N / (A × (1 - feeRate))`, fee je implicitne zahrnut).

Hodnoty: +10%, +20%, +50%, +100%

### 5. Loss warning

`ValidateSellOrderUseCase` doplnit o:

```kotlin
data class LossWarning(val lossFiat: BigDecimal, val lossPct: Double) : SellValidation()
```

**Trigger: `netProfit < 0`**, kde `netProfit = N - A × avg = A × P × (1 - feeRate) - A × avg`.

Pozor: trigger neni jen `P < avg`. Pri P tesne nad avg muze fee uz dostat transakci do realne ztraty. Banner reflektuje skutecnou ekonomickou realitu (anti-emocionalni cil = videt pravdu).

Banner formulace:
- `P < avg`: "Prodavas pod nakupni cenou: -X CZK"
- `P >= avg`, `netProfit < 0`: "Po fee prodavas se ztratou: -X CZK"

Cervene formatovani zisku, wizard normalne projde. **Zadny hard block, zadna dvojita konfirmace.** Pokud uzivatel po nasazeni zjisti, ze potrebuje silnejsi friction, lze pridat pozdeji.

### 6. Summary rozsireni

Sell wizard summary (Krok 1 i Krok 2) zobrazi:

```
--- Souhrn ---
Avg nakupni cena: 1 870 000 CZK    [auto / ✏️ rucne]
Profit per coin:  +230 000 CZK
Hruby zisk:       +5 750 CZK
Odhad fee:        -184 CZK (0.35%)
Cisty zisk:       +5 566 CZK (+12.3%)
Po prodeji:       0.18 BTC, avg 1 920 000 CZK
Postup k cili:    18 666 / 25 000 CZK (75%)
```

**Vypocty:**

- "Hruby zisk" = `A × (P - avg)`
- "Odhad fee" = `A × P × feeRate`
- "Cisty zisk" = `N - A × avg` (kde `N = A × P × (1 - feeRate)`)
- "Cisty zisk %" = `cistyZisk / (A × avg)`
- "Po prodeji - avg" = stejny algoritmus z #1, ale s timto hypotetickym sellem zahrnutym mezi historicke (smaze cheapest-first ze zbytku po existujicich pending+real sells)
- "Postup k cili" = `(realizedPnL + cistyZisk_thisTx) / plan.targetProfitAmount`. Jen pokud `targetProfitAmount != null`.

**Loss case** (`P < avg`): "Cisty zisk" se zobrazi cervene jako "**-X CZK (-Y%)**", plus banner.

### 7. Ladder mode (volitelny)

**Aktivace:** checkbox "Vytvorit vice sell orderu" v Kroku 1.

**UI po zaskrtnuti:**

- Pole `Limit cena` se nahradi dvojici `Od` / `Do`.
- Toggle uvnitr "Cena | Profit %" prepina mezi absolutnimi cenami a % nad cost basis.
- Pole `Cisty vynos` se skryje (nedava smysl pro ladder, derivuje se v preview).
- Nove pole `Pocet orderu` (cele cislo, default 5, min 2, max 10).
- Toggle "Equal crypto | Equal fiat":
  - **Equal crypto**: kazdy order ma `A_i = total / N` BTC.
  - **Equal fiat**: kazdy order ma `A_i = (totalFiatGross / N) / P_i` BTC, takze kazdy vygeneruje stejny gross fiat.
- Distribuce cen: linear, `P_i = from + (to - from) × i / (N - 1)` pro `i = 0..N-1`.

**Preview tabulka** vzdy viditelna pod inputs, re-renders na kazdou zmenu:

```
#   Mnozstvi    Cena         Profit %  Cisty vynos
1   0.05 BTC    2 000 000   +12.3%    99 650
2   0.05 BTC    2 100 000   +17.6%   104 632
3   0.05 BTC    2 200 000   +22.9%   109 615
4   0.05 BTC    2 300 000   +28.1%   114 597
5   0.05 BTC    2 400 000   +33.4%   119 580
                                     --------
                            Celkem:   548 074 CZK
```

Souhrn pod tabulkou: total profit (sum of profits), avg po prodeji vsech orderu (kdyby vsechny fillnuly), postup k cili.

### 8. Provedeni v ladder modu

Novy use case `PlaceLadderSellUseCase`. Vstup: `planId, List<LadderOrder(amount, limitPrice)>`.

**Failure handling: stop & report.**

1. Iterovat ordery sekvencne.
2. Pro kazdy: zavolat `api.limitSell(...)`, vlozit PENDING SELL transakci.
3. Pri prvnim selhani zastavit, vratit `Result(placedTxIds: List<Long>, failedAtIndex: Int, reason: String)`.
4. UI zobrazi `"Vytvoreno X z N orderu. Zbyvajici nepokracovaly: <reason>. Muzes zkusit znovu pro zbyvajici."`
5. Zadny auto-rollback. Pokud uzivatel chce zrusit uz vytvorene, pouzije existujici cancel ikonu na plan-detail.

### 9. Validace v ladder modu

`ValidateSellOrderUseCase` rozsirit o ladder validation:

- Total amount ≤ available (cost-basis-aware, viz #1)
- Per-order amount ≥ minOrderSize: `(total / N) ≥ minOrderSize` (equal-crypto), nebo `min(A_i) ≥ minOrderSize` (equal-fiat)
- `from > 0`, `to > from`, `N >= 2`
- Pokud profit % mod: `from`, `to` mohou byt i zaporne, vrati LossWarning
- Pokud absolutni ceny: `from > 3 × spot` -> `FarFromMarketWarning`
- LossWarning agreguje: `sum(amount_i × max(0, avg - P_i))` napric ordery

## UI rozlozeni

```
+- Sell wizard - Krok 1 ----------+
| Avg nakupni cena             ⓘ |
| [ 1 870 000 CZK   ] (auto)      |
| [ Spocitat z planu ]            |
|                                 |
| ☐ Vytvorit vice sell orderu     |
|                                 |
| Mnozstvi                        |
| [ 0.025 BTC      ]              |
| [25%][50%][75%][100%]           |
|                                 |
| Limit cena      [▼ % z avg]     |
| [ 2 100 000 CZK ]               |
| [+5%][+10%][+20%][+50%]         |
|                                 |
| Cisty vynos                     |
| [ 52 316 CZK    ]               |
| [+10%][+20%][+50%][+100%]       |
|                                 |
| ⚠️ Prodavas se ztratou (red)    |
|                                 |
| --- Souhrn ---                  |
| Avg buy: 1 870 000 ✏️           |
| Profit per coin: +230 000       |
| Hruby zisk: +5 750              |
| Odhad fee: -184 (0.35%)         |
| Cisty zisk: +5 566 (+12.3%)     |
| Po prodeji: 0.18 BTC @ 1 920 000|
| Cil: 18 666 / 25 000 (75%)      |
|                                 |
| [ Pokracovat ]                  |
+---------------------------------+
```

**Po zaskrtnuti ladder checkboxu:**

- Limit cena → dvojice Od/Do + toggle "Cena | %"
- Cisty vynos pole skryto
- Pribude "Pocet orderu" + toggle "Equal crypto | Equal fiat"
- Summary se nahradi preview tabulkou + agregatem

## Implementacni surface

**Nove soubory:**

- `domain/model/RemainingInventory.kt` - data class
- `domain/usecase/CalculatePlanCostBasisUseCase.kt` - algoritmus
- `domain/usecase/PlaceLadderSellUseCase.kt` - multi-order place

**Modifikace:**

- `exchange/ExchangeApi.kt` - pridat `estimatedTakerFeeRate`
- `exchange/CoinmateApi.kt`, `BinanceApi.kt`, `CoinbaseApi.kt`, `OtherExchanges.kt` - implementovat field
- `domain/usecase/ValidateSellOrderUseCase.kt` - `LossWarning`, ladder validation
- `presentation/screens/plans/sell/SellWizardViewModel.kt` - state machine pro 3-pole + ladder
- `presentation/screens/plans/sell/SellWizardBottomSheet.kt` - UI pole, presety, summary, ladder
- `res/values-cs/strings.xml`, `res/values/strings.xml` - nove stringy

**Nemeni se:**

- DB schema, migrace
- Backup/restore (`BackupDataCollector`, `BackupDataRestorer`)
- `CalculatePlanPnLUseCase` (zustava lifetime accounting)
- `SellPollingWorker`, `ResolvePendingTransactionsUseCase`
- `PlaceLimitSellUseCase` (single mod zustava nedotcen, ladder = nova cesta)

**Odhad rozsahu:** 8-10 souboru. Zadna schema migrace. Testovat lze postupne (single mod nejdriv, pak ladder).

## Edge cases

- **Zadne buys / vse prodano**: `available = 0`, `avg = null`, prefill prazdny, vyzaduje manualni vstup. Wizard projde, validace se opira jen o manualni avg.
- **Negative inventory** (`deficit > 0`, sells > buys): banner "Inventar nesedi, zadej avg manualne". Wizard projde s manualnim avg.
- **PARTIAL buy**: pouzit `cryptoAmount` (skutecne koupene), ne `requestedCryptoAmount`. (DCA buys jsou typicky atomic, ale obecne OK.)
- **Multi-connection v planu**: nestane se. Plan ma jednu `connectionId`, sells jdou pres ni.
- **Plan target = null**: skryt radek "Postup k cili".
- **PENDING ladder rozsahem prekracujici available**: validate pred place, hard error.
- **Ladder s 1 orderem**: nedovolit. `N >= 2` (jinak pouzij single mod).
- **Manualni override avg na nesmyslnou hodnotu** (zaporna, 0): hard error v ValidateSellOrderUseCase.

## Out of scope

- **Cache cost basis vypoctu**: YAGNI v1. Vypocet je rychly pro realisticka data. Pokud by se ukazal jako problem, in-memory cache invalidovana z `TransactionDao` flow.
- **Snapshot avg na SELL transakci**: nepotrebujeme, timestamp-aware cheapest-first resi stabilitu.
- **Hard block na loss**: jen warning + visual cue, uzivatel rozhoduje.
- **Geometric distribuce v ladderu**: linear postacuje.
- **Atomic batch place** / rollback pri selhani mid-batch: stop & report staci.
- **Perzistovane preset preference** (% z avg vs % ze spotu): transient session-level.
- **Zmena PnL vypoctu na cheapest-first**: zustava lifetime accounting v `CalculatePlanPnLUseCase`. Mozna pridat druhy radek "remaining cost basis" do PnL card jako future enhancement.
- **Per-buy detail v summary** ("z toho 0.05 BTC z buy z 1.1.2026, 0.10 BTC z buy z 5.3.2026..."): k debugu/future, neni MVP.

## Otevrene otazky pro planovaci fazi

- **Poradi tasku v planu**: cost basis use case (s testy) → fee plumbing → wizard ViewModel rewrite → UI single mod → ladder mode.
- **TDD**: cost basis algoritmus ma dost edge cases, vyplati se napsat unit testy. UI a presety testovat manualne.
- **Lokalizace**: nove stringy v cs + en soucasne, v jednom kroku.
- **Manual E2E test**: zacleneni do existujiciho Task 33 (Coinmate manual sandbox) a Task 34 (Binance) z `2026-04-23-dca-sell-extension.md`.
