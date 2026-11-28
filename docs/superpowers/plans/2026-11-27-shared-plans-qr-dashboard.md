# Sdílené plány: QR, přehled, grafy a formuláře - implementační plán

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Cíl:** Hotové sdílení plánů na Androidu:
- živé sdílení přes QR kód;
- stažení historie od začátku plánu;
- sdílené plány na přehledu a v grafech;
- dialog, který nejde zavřít omylem;
- očíčko u hesel;
- formuláře, které nezakrývá klávesnice.

**Architektura:**
- **QR obsah** je stejný šifrovaný formát `accbot-plan-share` jako soubor, jen bez obchodů a kompaktně serializovaný. Import tedy jde stávající cestou `ImportSharedPlanUseCase`.
- **Stav sdíleného plánu:** `SharedPlan` dostane `startedAt` (začátek plánu) a `tradesFromExchange` (obchody pocházejí z burzy, ne ze snímku). Podle nich se řídí slučování a nahrazování historie.
- **Přehled a Portfolio** čtou `SharedPlanStore.plans` vedle Room dat a sdílené plány nikdy nesčítají s vlastními.

**Tech stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Gson, ZXing (generování QR), ML Kit + CameraX (`QrScannerDialog`), JUnit4 + Robolectric + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-11-27-shared-plans-qr-dashboard-design.md`. Implementer čte spec i tento plán.

## Globální omezení

- **Rozsah:** jen Android (`accbot-android`). iOS se nemění. Soubory musí zůstat čitelné pro iOS, takže žádná nová povinná pole a `version` zůstává `1`.
- **Nová pole** v JSON jsou volitelná a mají `@SerializedName` (R8 + Gson, viz paměť projektu).
  - Balíček `com.accbot.dca.domain.share` **není** v `app/proguard-rules.pro` (keep má jen `domain.model`, `data.local`, `data.remote`). Task 3 ho přidá.
- **Limit QR obsahu:** `1850` bajtů UTF-8 (dnešní `CreateBackupUseCase.QR_MAX_BYTES`), jako jedna sdílená konstanta.
- **Bezpečnost:**
  - API klíč v QR je vždy uvnitř šifrovaného `data`;
  - klíč sdíleného plánu se používá jen přes `ReadOnlyExchangeClient`;
  - na sdíleném plánu není žádná akce, která něco na burze spustí nebo změní.
- **Vlastní čísla:** sdílené plány se nikdy nezapočítají do vlastních součtů (Dashboard holdings, `PairPage.Aggregate`, ROI, „Needs attention“, zmeškané nákupy, automatizace, průvodce prvním plánem).
- **Prostředí:** sandbox a ostré se nemíchají. Sdílený plán se ukazuje a obnovuje jen v prostředí, kde `SharedPlan.isSandbox == userPreferences.processSandboxMode`.
- **Texty** vždy v `res/values/strings.xml` (en) i `res/values-cs/strings.xml` (cs). Žádné hardcoded texty v UI. V textech nepoužívat dlouhou pomlčku.
- **Zrušení korutin:** `CancellationException` se vždy propaguje (vzor `catch (e: CancellationException) { throw e }` jako ve stávajícím kódu).
- **Testy během úkolu** spouštěj filtrem, celou sadu jen jednou na konci (Task 11).
- **Commity:** commit po každém úkolu, zprávy anglicky ve stylu repa (`feat(share): ...`), s řádkem `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Nic nepushovat.
- **Prostředí pro Gradle:** `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`, spouštět z `accbot-android/`.

## Na co se při review zaměřit

1. **Stejný QR naskenovaný dvakrát, nebo QR po souboru a naopak:** stažená historie nesmí zmizet a nesmí vzniknout mezera ani duplicity. Testy jsou v Tasku 4.
2. **Plán se starším souborem bez `startedAt` a s `createdAt` chybějícím nebo 0:** stahování nesmí začít od roku 1970. Fallback a test jsou v Tasku 3.
3. **Burza vrátí obchod přesně v okamžiku `startedAt`, nebo jiný pár, nebo nulové množství:** obchod v `startedAt` se bere (hranice včetně), ostatní se zahodí. Test je v Tasku 5.
4. **Současná obnova** (otevření appky a detail, nebo pull-to-refresh a detail) nad stejným plánem nesmí ztratit obchody ani je zdvojit. Obnovy jednoho plánu se serializují. Test je v Tasku 6.
5. **QR obsah těsně nad limitem** (dlouhý passphrase nebo název): QR se nezobrazí, soubor jde sdílet dál a appka nespadne. Test je v Tasku 3.

---

### Task 0: Commit specu

**Files:**
- Commit: `docs/superpowers/specs/2026-11-27-shared-plans-qr-dashboard-design.md`, `docs/superpowers/plans/2026-11-27-shared-plans-qr-dashboard.md`

- [ ] **Krok 1: Commit**

```bash
git add docs/superpowers/specs/2026-11-27-shared-plans-qr-dashboard-design.md docs/superpowers/plans/2026-11-27-shared-plans-qr-dashboard.md
git commit -m "docs(share): design and plan for QR live shares and shared plans on dashboard"
```

---

### Task 1: Pole hesla s očíčkem

**Files:**
- Create: `app/src/main/java/com/accbot/dca/presentation/components/PasswordTextField.kt`
- Modify:
  - `presentation/screens/shared/SharePlanDialog.kt` (`SecretField`, ř. ~534);
  - `presentation/screens/shared/SharedPlansScreen.kt` (`SharePasswordDialog`, ř. ~159);
  - `presentation/screens/backup/BackupExportScreen.kt` (ř. ~268, ~276);
  - `presentation/screens/backup/BackupImportScreen.kt` (ř. ~242);
  - `presentation/components/CredentialsInputCard.kt` (ř. ~506, pole bez přepínače).
- Test: `app/src/test/java/com/accbot/dca/presentation/components/PasswordTextFieldTest.kt` (Robolectric + `createComposeRule`).

**Rozhraní:**
- Produces:

```kotlin
@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null
)
```

- [ ] **Krok 1: Failing test** `PasswordTextFieldTest`:
  - `hidden by default and toggles visibility`:
    - render s `value = "tajne123"`;
    - `onNodeWithText("tajne123").assertDoesNotExist()`, protože maskovaný text se zobrazuje jako tečky;
    - klik na `onNodeWithContentDescription("Show password")` → `onNodeWithText("tajne123").assertExists()`;
    - znovu klik na `"Hide password"` → zase neexistuje.
  - Použij `@Config(qualifiers = "en")`, aby sedělo `credentials_show_password` a `credentials_hide_password`.
- [ ] **Krok 2:** `./gradlew :app:testDebugUnitTest --tests "*PasswordTextFieldTest"` → FAIL (třída neexistuje).
- [ ] **Krok 3: Implementace komponenty:**
  - `OutlinedTextField`, `singleLine = true`, `KeyboardType.Password`, `autoCorrectEnabled = false`;
  - stav `var visible by rememberSaveable { mutableStateOf(false) }`;
  - `trailingIcon` je `IconButton` s `Icons.Default.Visibility` / `VisibilityOff` a popisem `R.string.credentials_show_password` / `credentials_hide_password`, stejně jako v `CredentialsInputCard`.
- [ ] **Krok 4: Nahrazení všech výskytů** `PasswordVisualTransformation` z výčtu Files touto komponentou. Tři pole v `CredentialsInputCard`, která přepínač už mají (ř. ~249, ~401, ~469), nech být.
  - Kontrola: `grep -rn "PasswordVisualTransformation" app/src/main/java` → jen `PasswordTextField.kt` a tři výskyty se `showSecret` v `CredentialsInputCard.kt`.
- [ ] **Krok 5:** Test z kroku 2 → PASS.
- [ ] **Krok 6: Commit** `feat(ui): show/hide toggle on every password field`

---

### Task 2: Dialog „Sdílet plán“ nejde zavřít omylem a klávesnice nezakrývá formuláře

**Files:**
- Modify:
  - `presentation/screens/shared/SharePlanDialog.kt` (`AlertDialog` ř. ~272);
  - `app/src/main/AndroidManifest.xml` (activity `.MainActivity`);
  - scrollovatelné formuláře z Tasku 1: `BackupExportScreen`, `BackupImportScreen`, obrazovky s `CredentialsInputCard` (`AddExchangeScreen`, `AddPlanScreen`).

**Rozhraní:** nic nového.

- [ ] **Krok 1: Dialog**
  - `onDismissRequest = {}`;
  - `properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)`.
  - Zavírá se jen tlačítky Zrušit / Sdílet (a později Hotovo z Tasku 7).
  - Systémové Zpět v dialogu nedělá nic. Je to rozhodnutí plánu, odpovídá specu („nesmí tiše zahodit data“).
- [ ] **Krok 2: Klávesnice na celé obrazovce**
  - do manifestu u `MainActivity` přidat `android:windowSoftInputMode="adjustResize"`;
  - na kořenový scrollovatelný `Column` každé obrazovky z výčtu přidat `Modifier.imePadding()` (pořadí: `imePadding()` před `verticalScroll`), pokud ji tam nemá `Scaffold`.
- [ ] **Krok 3: Klávesnice v dialogu**
  - obsahu `Column` v `SharePlanDialog` přidat `Modifier.imePadding()`;
  - každé textové pole v dialogu dostane `Modifier.bringIntoViewRequester(...)` a při `onFocusChanged { if (it.isFocused) scope.launch { requester.bringIntoView() } }` se samo odroluje do viditelné části;
  - když `AlertDialog` s `decorFitsSystemWindows = true` nereaguje na IME, použij `DialogProperties(decorFitsSystemWindows = false)` a ověř vzhled.
- [ ] **Krok 4: Ruční ověření na emulátoru** s malým displejem (např. Pixel 4a, 5.8"):
  - klepnutí mimo dialog ani Zpět dialog nezavře;
  - u sdílení se zapnutým živým sdílením a nejnižším polem (potvrzení hesla) je pole i jeho text nad klávesnicí;
  - stejně u hesla v Záloze a u API secret při přidání burzy.
- [ ] **Krok 5: Commit** `fix(ui): share dialog closes only via its buttons, keyboard no longer hides fields`

---

### Task 3: Kodek - začátek plánu, původ obchodů, QR obsah

**Files:**
- Create: `app/src/main/java/com/accbot/dca/domain/util/QrLimits.kt`
- Modify:
  - `domain/share/SharedPlanModels.kt`;
  - `domain/share/SharedPlanCodec.kt`;
  - `domain/usecase/SharePlanUseCases.kt` (`PlanShareFile`, `CreatePlanShareUseCase`);
  - `domain/usecase/CreateBackupUseCase.kt` (používá `QrLimits`);
  - `app/proguard-rules.pro`: přidat `-keep class com.accbot.dca.domain.share.** { *; }` pod `# Keep domain models`.
- Test: `app/src/test/java/com/accbot/dca/domain/share/SharedPlanCodecTest.kt` (rozšířit).

**Rozhraní:**
- Produces:
  - `object QrLimits { const val MAX_PAYLOAD_BYTES = 1850 }`;
  - `SharedPlanInfoDto.startedAt: Long?` s `@SerializedName("startedAt")`;
  - `SharedPlanLocalDto.tradesFromExchange: Boolean?` s `@SerializedName("tradesFromExchange")`;
  - `SharedPlan.startedAt: Instant` (povinné, vždy dopočtené) a `SharedPlan.tradesFromExchange: Boolean = false`;
  - `SharedPlanCodec.encodeShare(snapshot, access, password, encrypt, compact: Boolean = false): String`. Při `compact = true` se vnitřní i vnější JSON serializuje bez pretty-printingu (druhá instance Gson bez `setPrettyPrinting()`).
  - `SharedPlanCodec.qrSnapshot(snapshot: SharedPlanSnapshotDto): SharedPlanSnapshotDto` = `snapshot.copy(transactions = emptyList(), summary = null)`;
  - `data class PlanShareFile(val fileName: String, val content: String, val qrContent: String? = null)`. `qrContent` je jen u živého sdílení a jen když `qrContent.toByteArray(UTF_8).size <= QrLimits.MAX_PAYLOAD_BYTES`.

**Pravidla:**
- **Odesílatel:** `startedAt` = min(`plan.createdAt`, `executedAt` první započítané transakce plánu).
- **Příjemce, když `startedAt` chybí:**
  - použij min z `createdAt` (jen když > 0) a `executedAt` prvního obchodu;
  - když není ani jedno, použij `sharedAt`.
- **Uložení:** `startedAt` se přenáší v `toDto` (uložená kopie), `tradesFromExchange` jen v `local` (věří se mu jen při `trustLocal`).

- [ ] **Krok 1: Failing testy** v `SharedPlanCodecTest`:
  - `snapshot carries plan start as earlier of creation and first trade`: transakce `tx(...)` s `executedAt` před `t0` (záporný `day`) → `decoded.startedAt == ten executedAt`. Bez takové transakce → `== t0`.
  - `old share without startedAt falls back to first trade, never epoch zero`: dto bez `startedAt` a s `createdAt = null` → `startedAt == první obchod`. Bez obchodů → `== sharedAt`.
  - `qr content is a compact live share without trades and decodes with password`:
    - `CreatePlanShareUseCase` se živým klíčem → `qrContent != null`;
    - `!qrContent.contains("\n")`;
    - `decodeShare(qrContent, "heslo123", decrypt).plan.trades.isEmpty()`;
    - `credentials != null`;
    - `startedAt` je stejné jako ze souboru.
    - Use case testuj s fake `BackupCryptoUseCase` / šifrováním, stejně jako stávající test `live share is encrypted and carries the read-only key`.
  - `qr content is null for snapshot shares and when over the limit`:
    - bez `live` → `qrContent == null`;
    - s `passphrase` o délce 1500 znaků → `qrContent == null`, ale `content` je platný.
  - `tradesFromExchange survives local storage`: `encode(plan.copy(tradesFromExchange = true))` → `decode(...).tradesFromExchange == true`. Z `decodeShare` přijatého souboru je vždy `false`.
- [ ] **Krok 2:** `./gradlew :app:testDebugUnitTest --tests "*SharedPlanCodecTest"` → nové testy FAIL.
- [ ] **Krok 3: Implementace** podle Rozhraní a Pravidel:
  - `CreatePlanShareUseCase` vytvoří `content` (dnešní, s obchody) a pro `live != null` navíc `qrContent = encodeShare(qrSnapshot(snapshot), access, password, encrypt, compact = true)`;
  - stejný `snapshot` znamená stejné `shareId` a `sharedAt`;
  - `CreateBackupUseCase.QR_MAX_BYTES` nahraď `QrLimits.MAX_PAYLOAD_BYTES`.
- [ ] **Krok 4:** Test z kroku 2 → PASS.
- [ ] **Krok 5: Commit** `feat(share): plan start date and compact QR payload for live shares`

---

### Task 4: Opakovaný import stejného plánu

**Files:**
- Modify: `data/local/SharedPlanStore.kt` (`save`), `domain/usecase/SharePlanUseCases.kt` (`ImportSharedPlanUseCase`).
- Test: `SharedPlanCodecTest.kt` (vedle `store replaces older snapshot and ignores outdated one`) nebo nový `SharedPlanStoreTest.kt` se stejným nastavením (`TemporaryFolder`, in-memory `ShareIdRegistry` a `KeyVault`).

**Rozhraní:**
- Consumes: `SharedPlan.tradesFromExchange`, `startedAt` (Task 3).
- Produces: `SharedPlanStore.save(plan, credentials)` s pravidlem níže; signatura se nemění.

**Pravidlo `save`**, když existuje uložený plán se stejným `shareId` a příchozí není starší:
- **Příchozí bez obchodů a s klíčem** (QR obsah):
  - zachovej `existing.trades`, `existing.tradesFromExchange`, `existing.refreshedAt`;
  - převezmi údaje plánu, klíč a `startedAt`;
  - `sharedAt` převezmi jen když `existing.tradesFromExchange`, jinak nech `existing.sharedAt`, aby `merge` neudělal mezeru.
- **Jinak** platí dnešní chování: příchozí nahradí uložený.
- **Nový plán** z QR (žádný existující, bez obchodů, s klíčem) se uloží s `tradesFromExchange = true`.

- [ ] **Krok 1: Failing testy:**
  - `qr re-import keeps downloaded history and updates key`:
    - uložený plán `tradesFromExchange = true` se 3 obchody;
    - save QR verze (0 obchodů, novější `sharedAt`, jiný název, nové credentials);
    - výsledek má 3 obchody, nový název, nový `sharedAt` a `credentials(shareId)` vrací nový klíč.
  - `qr after file snapshot keeps snapshot trades and its sharedAt`:
    - uložený snímek ze souboru (`tradesFromExchange = false`, 2 obchody, `sharedAt = T1`);
    - QR s `sharedAt = T2 > T1`;
    - výsledek má 2 obchody, `sharedAt == T1` a `tradesFromExchange == false`.
  - `file after qr replaces history`: uložený z QR se staženými obchody, příchozí soubor s 5 obchody → 5 obchodů, `tradesFromExchange == false`.
  - `new qr share is marked as exchange-based`.
- [ ] **Krok 2:** Filtrovaný běh → FAIL.
- [ ] **Krok 3:** Implementace pravidla v `SharedPlanStore.save`, uvnitř stávajícího `mutex.withLock`.
- [ ] **Krok 4:** Filtrovaný běh → PASS (i stávající `store replaces older snapshot...`).
- [ ] **Krok 5: Commit** `fix(share): re-importing a QR share keeps the downloaded history`

---

### Task 5: Stažení historie sdíleného plánu

**Files:**
- Modify: `domain/usecase/SharePlanUseCases.kt` (`RefreshSharedPlanUseCase` a nová třída `DownloadSharedPlanHistoryUseCase` ve stejném souboru).
- Test: `app/src/test/java/com/accbot/dca/domain/usecase/SharedPlanHistoryTest.kt`. Čisté funkce z companion objektu a use case s fake `ReadOnlyExchangeClient.Factory` nad `FakeExchangeApi`.

**Rozhraní:**
- Consumes: `startedAt`, `tradesFromExchange` (Task 3), `ImportRange` (`domain/usecase/ImportTradeHistoryUseCase.kt`).
- Produces, v `RefreshSharedPlanUseCase.Companion`:
  - `internal fun toSharedTrades(plan: SharedPlan, fetched: List<HistoricalTrade>): List<SharedPlanTrade>`:
    - dnešní seskupení podle `orderId` a filtry (pár, strana, kladné částky);
    - navíc zahodí obchody s `executedAt < plan.startedAt` (hranice včetně: `== startedAt` se bere).
  - `internal fun merge(plan: SharedPlan, fetched: List<HistoricalTrade>): List<SharedPlanTrade>`:
    - dnešní chování;
    - když `plan.tradesFromExchange`, nepoužije `cutoff` podle `sharedAt` a deduplikuje jen podle `ref`.
  - `internal fun replaceFrom(plan: SharedPlan, from: Instant, fetched: List<HistoricalTrade>): List<SharedPlanTrade>` = obchody plánu s `executedAt < from` + `toSharedTrades(...)` s `executedAt >= from`, seřazené.
- Produces:

```kotlin
class DownloadSharedPlanHistoryUseCase @Inject constructor(
    private val sharedPlanStore: SharedPlanStore,
    private val readOnlyClients: ReadOnlyExchangeClient.Factory
) {
    /** Range semantics per spec; returns the same Result type as a refresh. */
    suspend operator fun invoke(
        shareId: String,
        range: ImportRange,
        onProgress: (ApiImportProgress) -> Unit = {},
        now: Instant = Instant.now()
    ): RefreshSharedPlanUseCase.Result
}
```

**Rozsahy:**
- `SinceLastPurchase`: append přes `merge`, od posledního obchodu (nebo `startedAt`).
- `SinceDate(millis)`: `from = max(Instant.ofEpochMilli(millis), startedAt)`, výsledek přes `replaceFrom`.
- `AllHistory`: `from = startedAt`, `replaceFrom`, tedy všechny obchody nahradí ty z burzy.
- Po `SinceDate` / `AllHistory` je `tradesFromExchange = true`.
- Vždy se nastaví `refreshedAt = now` a `refreshFailed = false`.
- Kontrola `verifyKey()` a výsledky `KeyNotReadOnly` / `Failed` / `NotLive` jsou stejné jako v `RefreshSharedPlanUseCase.invoke`. Vytáhni společnou privátní/internal funkci, kód nekopíruj.
- Stránkování: stávající `fetchSince` s `PAGE_SIZE = 100` a `MAX_PAGES = 50`; `onProgress(ApiImportProgress.Fetching(page, total))` po každé stránce.

- [ ] **Krok 1: Failing testy:**
  - `trades before plan start are dropped, trade at start is kept`;
  - `other pair, unknown side and zero amounts are dropped`;
  - `all history replaces snapshot trades without duplicates`: snímek se 2 obchody (order-ID ref) + burza vrátí tytéž 2 nákupy pod trade-ID → výsledek má 2 obchody, ne 4;
  - `since date keeps older trades and replaces the rest`;
  - `since date before plan start is clamped to start`;
  - `since last purchase on exchange-based plan ignores sharedAt cutoff`: plán `tradesFromExchange = true`, `sharedAt` v budoucnosti vůči novému obchodu → obchod se přidá;
  - `existing refresh behaviour for file snapshots is unchanged`: stávající hranice `sharedAt` / `SNAPSHOT_MARGIN`;
  - `download marks plan exchange-based and clears refreshFailed`;
  - `key that can trade is removed unless fullAccessKey`.
- [ ] **Krok 2:** `./gradlew :app:testDebugUnitTest --tests "*SharedPlanHistoryTest"` → FAIL.
- [ ] **Krok 3:** Implementace podle Rozhraní.
- [ ] **Krok 4:** Filtrovaný běh → PASS.
- [ ] **Krok 5: Commit** `feat(share): download shared plan history from its start, since a date or since the last trade`

---

### Task 6: Automatická obnova (pull-to-refresh, otevření aplikace)

**Files:**
- Create: `app/src/main/java/com/accbot/dca/domain/usecase/RefreshSharedPlansUseCase.kt`
- Modify:
  - `domain/usecase/SharePlanUseCases.kt` (serializace obnovy jednoho plánu);
  - `AccBotApplication.kt` (`ProcessLifecycleOwner` `onStart`, ř. ~94);
  - `presentation/screens/DashboardViewModel.kt` (`refreshAll`, ř. ~638).
- Test: `app/src/test/java/com/accbot/dca/domain/usecase/RefreshSharedPlansUseCaseTest.kt`

**Rozhraní:**
- Produces:

```kotlin
class RefreshSharedPlansUseCase @Inject constructor(
    private val sharedPlanStore: SharedPlanStore,
    private val refreshSharedPlan: RefreshSharedPlanUseCase
) {
    /** Pull-to-refresh: every live plan of the environment. */
    suspend fun refreshAll(isSandbox: Boolean, now: Instant = Instant.now()): Map<String, RefreshSharedPlanUseCase.Result> // shareId -> result
    /** App start: only plans whose scheduled purchase has probably happened. */
    suspend fun refreshDue(isSandbox: Boolean, now: Instant = Instant.now()): Map<String, RefreshSharedPlanUseCase.Result>

    companion object {
        internal fun nextExpectedPurchase(plan: SharedPlan): Instant
        internal fun isDue(plan: SharedPlan, now: Instant): Boolean
    }
}
```

**Pravidla:**
- `nextExpectedPurchase`:
  - základ je `trades.lastOrNull { it.side == BUY }?.executedAt ?: startedAt`;
  - `CUSTOM` + `cronExpression` → `CronUtils.getNextExecution(cron, base)`;
  - jinak `base + frequency.intervalMinutes`;
  - neznámá frekvence nebo neplatný cron → `base + 1 den`.
- `isDue` = `liveUpdates && isEnabled && next <= now && (refreshedAt == null || refreshedAt < next)`.
  - `refreshFailed` obnovu nezakazuje. Jeden pokus proběhne při každém otevření, ne ve smyčce.
- **Serializace:** obnova i stažení jednoho plánu se nesmí překrývat. `RefreshSharedPlanUseCase` a `DownloadSharedPlanHistoryUseCase` sdílí zámek podle `shareId`, např. `Mutex` mapu v `SharedPlanStore` vystavenou jako `suspend fun <T> withPlanLock(shareId: String, block: suspend () -> T): T`. Uvnitř zámku se plán znovu načte ze store, ne z parametru.
- **Hook při startu:** v `AccBotApplication` `onStart` spusť `refreshSharedPlans.refreshDue(userPreferences.processSandboxMode)` ve stávajícím `appScope`. Chyby jen zaloguj (`Log.w`).
- **Pull-to-refresh:** `DashboardViewModel.refreshAll` spustí `refreshAll(isSandbox)` paralelně s ostatními `launch` v `coroutineScope`.

- [ ] **Krok 1: Failing testy:**
  - `daily plan is due a day after last buy and not before`;
  - `custom cron uses next cron time`;
  - `paused plan and plan without key are never due`;
  - `plan refreshed after expected purchase is not due`;
  - `failed refresh tries once per call and stays due`: `FakeExchangeApi` hází chybu → jedno volání `refreshDue` zavolá `tradeHistory` pro plán právě jednou, výsledek je `Failed`, `refreshFailed == true` a `isDue(plan, now)` je dál `true`. Další pokus proběhne až při dalším `onStart`; smyčku nic nespouští.
  - `refreshDue skips other environment`;
  - `concurrent refresh and download do not duplicate trades`: dvě korutiny nad stejným `shareId` s `FakeExchangeApi` vracející 1 nový obchod → výsledek má právě jeden nový obchod.
- [ ] **Krok 2:** Filtrovaný běh → FAIL.
- [ ] **Krok 3:** Implementace podle Rozhraní a Pravidel.
- [ ] **Krok 4:** Filtrovaný běh → PASS, i testy z Tasku 5.
- [ ] **Krok 5: Commit** `feat(share): refresh shared plans on pull-to-refresh and when a purchase is due at app start`

---

### Task 7: QR kód ve výsledku sdílení

**Files:**
- Create: `app/src/main/java/com/accbot/dca/presentation/utils/QrBitmap.kt`
- Modify:
  - `presentation/screens/backup/BackupExportViewModel.kt` (smaž privátní `generateQrBitmap`, použij util);
  - `presentation/screens/shared/SharePlanDialog.kt` (`SharePlanViewModel.UiState`, `createShare`, `SharePlanDialog`);
  - `res/values/strings.xml`, `res/values-cs/strings.xml`.

**Rozhraní:**
- Consumes: `PlanShareFile.qrContent` (Task 3).
- Produces:
  - `fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap?`. Přesunutý kód z `BackupExportViewModel`, ZXing `QRCodeWriter`.
  - `SharePlanViewModel.UiState`:
    - `+ result: ShareResult? = null`, kde `data class ShareResult(val fileUri: Uri, val qrContent: String?)`;
    - `readyUri` zůstává jen pro sdílení bez klíče.

**Chování:**
- **Bez živého sdílení:** stejně jako dnes, Sdílet otevře systémové sdílení souboru a zavře dialog.
- **Živé sdílení:** po úspěchu se dialog nezavře a přepne do výsledku:
  - nadpis `share_plan_qr_title`;
  - QR obrázek ~240 dp, generovaný mimo hlavní vlákno (`produceState` + `Dispatchers.Default`);
  - text `share_plan_qr_hint`;
  - tlačítko „Sdílet soubor“ (`share_plan_share_file`), které otevře dnešní intent s `fileUri`;
  - tlačítko „Hotovo“ (`common_done`), které zavře dialog a zavolá `reset()`.
- **`qrContent == null`:** místo QR se ukáže text `share_plan_qr_too_large` a tlačítko „Sdílet soubor“ zůstane.
- **Ochrana QR:** výsledek s QR obsahuje šifrovaný klíč, proto se po zavření dialogu nikde neuchovává (`reset()`). `FLAG_SECURE` se nepřidává. QR je stejně chráněný heslem jako soubor a obrazovka plánu dnes mezi citlivé trasy v `MainActivity` (ř. ~281) nepatří.

**Texty:**

| Klíč | cs | en |
|---|---|---|
| `share_plan_qr_title` | „Naskenujte ve druhém telefonu“ | „Scan on the other phone“ |
| `share_plan_qr_hint` | „V aplikaci AccBot otevřete Nastavení → Sdílené plány → Naskenovat QR kód. Heslo předejte zvlášť, ne spolu s QR kódem.“ | „In AccBot open Settings → Shared plans → Scan QR code. Give the password separately, not together with the QR code.“ |
| `share_plan_share_file` | „Sdílet soubor“ | „Share file“ |
| `share_plan_qr_too_large` | „QR kód by byl příliš velký. Pošlete plán souborem.“ | „The QR code would be too large. Send the plan as a file.“ |

- [ ] **Krok 1:** Přesun `generateQrBitmap` do utilu. Záloha funguje beze změny: `./gradlew :app:compileDebugKotlin` → OK.
- [ ] **Krok 2:** UiState + `createShare` + výsledkové UI podle Chování.
- [ ] **Krok 3: Ruční ověření na emulátoru:**
  - živé sdílení → QR se zobrazí;
  - „Sdílet soubor“ otevře chooser;
  - „Hotovo“ zavře dialog;
  - sdílení bez klíče se chová jako dřív.
- [ ] **Krok 4: Commit** `feat(share): show a QR code for live shares`

---

### Task 8: Import fotoaparátem a „Stáhnout historii“ v detailu

**Files:**
- Modify:
  - `presentation/screens/shared/SharedPlansScreen.kt`;
  - `presentation/screens/shared/SharedPlansViewModel.kt` (oba view modely);
  - `presentation/screens/shared/SharedPlanDetailScreen.kt`;
  - `presentation/components/ImportConfigDialog.kt`;
  - `strings.xml` (cs, en).
- Test: `app/src/test/java/com/accbot/dca/presentation/screens/shared/SharedPlansViewModelTest.kt`, s fake `ImportSharedPlanUseCase` (otevřít třídu nebo injektovat rozhraní podle toho, co je v repu zvykem, viz `reference_robolectric_encryptedprefs`).

**Rozhraní:**
- Consumes: `DownloadSharedPlanHistoryUseCase` (Task 5), `QrScannerDialog(onDismiss, onScanResult, showTextMode)`.
- Produces:
  - `SharedPlansViewModel.importScanned(content: String)`: stejná cesta jako `importJson(content, password = null)`; obsah větší než `MAX_FILE_BYTES` → chyba `TOO_LARGE`.
  - `ImportConfigDialog` s novými volitelnými parametry; stávající volání se nemění:
    - `minSelectableDateMillis: Long? = null`: spodní hranice pro `SelectableDates`;
    - `allHistoryTitle: String? = null`, `allHistoryHint: String? = null`: přepis textu volby `AllHistory`;
    - `warning: String? = null`: text karty místo multi-plan varování.
  - `SharedPlanDetailViewModel`:
    - `UiState` přidá `historyDialog: Boolean = false`, `historyRange: ImportRange = ImportRange.SinceLastPurchase`, `downloading: Boolean = false`, `downloadResult: ApiImportResultState? = null`;
    - funkce `openHistoryDialog()`, `setHistoryRange(ImportRange)`, `downloadHistory()`, `dismissHistoryDialog()`, `consumeDownloadResult()`.

**Chování:**
- **Seznam sdílených plánů:** `SharedPlansScreen` má vedle „Otevřít soubor“ akci „Naskenovat QR kód“ (`shared_plans_scan_qr`, ikona `QrCodeScanner`), která otevře `QrScannerDialog(showTextMode = false)`. Neplatný obsah zobrazí stávající chybovou zprávu.
- **Detail sdíleného plánu:**
  - akce „Stáhnout historii“ (`shared_plan_download_history`) je v top baru vedle Refresh, jen když `plan.liveUpdates`;
  - dialog `ImportConfigDialog` nabídne:
    - `canImportSinceLastPurchase = plan.trades.isNotEmpty()`, s datem posledního obchodu;
    - `minSelectableDateMillis = plan.startedAt`;
    - `allHistoryTitle = "Od začátku plánu"` (`shared_plan_history_from_start`) s datem `startedAt`;
    - `warning = shared_plan_history_warning`;
  - výsledek ukáže `ApiImportResultDialog`.
- **Plán bez obchodů s klíčem** (typicky hned po QR importu): `load()` neprovede automatickou obnovu. Rovnou otevře dialog historie s `historyRange = ImportRange.AllHistory`.

**Texty:**

| Klíč | cs | en |
|---|---|---|
| `shared_plans_scan_qr` | „Naskenovat QR kód“ | „Scan QR code“ |
| `shared_plan_download_history` | „Stáhnout historii“ | „Download history“ |
| `shared_plan_history_from_start` | „Od začátku plánu“ | „From the plan start“ |
| `shared_plan_history_warning` | „Burza vrací všechny obchody tohoto páru na účtu. Obchody před začátkem plánu se vynechají, ruční nákupy nebo jiné plány stejného páru po začátku se ale od nákupů plánu odlišit nedají.“ | „The exchange returns every trade of this pair on the account. Trades before the plan start are skipped, but manual buys or other plans of the same pair after the start cannot be told apart from the plan’s purchases.“ |

- [ ] **Krok 1: Failing testy** `SharedPlansViewModelTest`:
  - `scanned qr asks for password then opens the plan`;
  - `scanned garbage shows not-a-share error`.

  Detail VM:
  - `plan without trades opens history dialog with from-start range instead of refreshing`;
  - `download success shows result and updates plan`.
- [ ] **Krok 2:** Filtrovaný běh → FAIL.
- [ ] **Krok 3:** Implementace podle Rozhraní a Chování.
- [ ] **Krok 4:** Filtrovaný běh → PASS.
- [ ] **Krok 5: Ruční ověření:**
  - QR ze druhého emulátoru, nebo vyfocený QR z Tasku 7 přes virtuální scénu kamery;
  - zadání hesla otevře detail s dialogem historie;
  - stažení proběhne.
- [ ] **Krok 6: Commit** `feat(share): import shared plans by camera and download their history`

---

### Task 9: Sdílené plány na přehledu

**Files:**
- Create: `app/src/main/java/com/accbot/dca/presentation/screens/SharedPlanCard.kt`
- Modify:
  - `presentation/screens/DashboardViewModel.kt` (`DashboardUiState`, `loadData`, sběr `SharedPlanStore.plans`);
  - `presentation/screens/DashboardScreen.kt` (sekce plánů, kolem ř. ~326 a ~423);
  - `MainActivity.kt` (navigace z karty na `Screen.SharedPlanDetail`, existující route v `Screen.kt`);
  - `strings.xml`.
- Test: `app/src/test/java/com/accbot/dca/presentation/screens/DashboardSharedPlansTest.kt`, čistá funkce mapování.

**Rozhraní:**
- Produces:

```kotlin
@Immutable
data class SharedPlanCardInfo(
    val plan: SharedPlan,
    val currentPrice: BigDecimal?,
    /** Null when amounts are hidden. */
    val performancePercent: BigDecimal?,
    val refreshFailed: Boolean,
    val keyRemoved: Boolean
)

// DashboardUiState
val sharedPlans: List<SharedPlanCardInfo> = emptyList()

// DashboardViewModel.Companion
internal fun sharedPlanCards(
    plans: List<SharedPlan>,
    isSandbox: Boolean,
    prices: Map<String, BigDecimal>   // key "CRYPTO/FIAT"
): List<SharedPlanCardInfo>
```

**Pravidla:**
- `sharedPlanCards` vrací jen plány s `isSandbox` shodným s prostředím, seřazené podle `displayName`.
- `keyRemoved` znamená, že plán `liveUpdates == false`, ale dřív ho měl. Mapuj z výsledku `KeyNotReadOnly` z mapy, kterou vrací `RefreshSharedPlansUseCase.refreshAll` (Task 6), uložené ve VM jako `Set<String>` shareId. Stav se nepersistuje.
- Ceny přes `marketDataService.getCachedPrice(crypto, fiat)`, stejný zdroj jako `SharedPlanDetailViewModel.loadPrice`.
- **Karta:**
  - štítek „Sdílený plán“ (`shared_plan_badge`, ikona `Icons.Default.Share`);
  - název, pár, `exchangeName`, poslední obchod, `summary.heldCrypto` a výkon, když jsou částky;
  - čas poslední aktualizace (`shared_plan_live_text` už existuje) a varování při `refreshFailed` / `keyRemoved`;
  - žádný přepínač, odpočet ani akce;
  - klik otevře detail.
- Sekce plánů vykreslí sdílené karty **pod** vlastními plány.
- `holdings`, „Needs attention“, `missedPurchases`, automatizace a průvodce prvním plánem se nemění a sdílené plány do nich nevstupují.

- [ ] **Krok 1: Failing testy:**
  - `shared cards only for current environment`;
  - `hidden amounts give no performance`;
  - `shared plans never change holdings`: `DashboardUiState.holdings` vypočtené z Room transakcí je stejné s i bez sdílených plánů ve store. Test nad funkcí, která holdings počítá; pokud je privátní, ověř to přes VM s fake store.
- [ ] **Krok 2:** Filtrovaný běh → FAIL.
- [ ] **Krok 3:** Implementace podle Rozhraní a Pravidel.
- [ ] **Krok 4:** Filtrovaný běh → PASS.
- [ ] **Krok 5: Ruční ověření:**
  - karta pod vlastními plány;
  - pull-to-refresh aktualizuje čas;
  - sandbox a ostré se nemíchají.
- [ ] **Krok 6: Commit** `feat(dashboard): show shared plans below own plans`

---

### Task 10: Sdílené plány v grafech (Portfolio)

**Files:**
- Modify:
  - `presentation/screens/portfolio/PortfolioViewModel.kt` (`PairPage`, stavba stránek ř. ~221 a ~293, `pageIdOf` ř. ~191, `loadChartData` ř. ~714, větve otevřených prodejů ř. ~466 a ~505);
  - `presentation/screens/portfolio/PortfolioScreen.kt` (titulky ř. ~131, klíče ř. ~425, štítek);
  - `strings.xml`.
- Create: `app/src/main/java/com/accbot/dca/domain/share/SharedPlanChartMapper.kt`
- Test: `app/src/test/java/com/accbot/dca/domain/share/SharedPlanChartMapperTest.kt` a rozšíření existujícího testu Portfolio, pokud existuje. Jinak čistá funkce stavby stránek.

**Rozhraní:**
- Produces:
  - `data class Shared(val shareId: String, val name: String, val crypto: String, val fiat: String) : PairPage()`, `pageIdOf` = `"shared:$shareId"`;
  - `fun SharedPlan.toChartTransactions(): List<TransactionEntity>`:
    - jen pokud `includesAmounts`, jinak prázdný seznam;
    - `planId = 0`, `connectionId = null` (nebo hodnota, kterou entita povolí);
    - `status = COMPLETED`, `side`, `executedAt`, `price`, částky a `fee` ze `SharedPlanTrade`, `exchange = plan.exchange ?: Exchange.COINMATE` (jen kvůli povinnému poli; graf burzu nepoužívá);
    - nikdy se neukládá do Room.
  - `internal fun buildPages(dbPlans: List<DcaPlanEntity>, shared: List<SharedPlan>, isSandbox: Boolean): List<PairPage>`:
    - dnešní stránky beze změny;
    - na konec přidá `Shared` pro plány aktuálního prostředí s `includesAmounts && trades.isNotEmpty()`.

**Pravidla:**
- Graf `Shared` stránky: `calculateChartDataUseCase.calculate(plan.toChartTransactions(), crypto, fiat, zoom)`.
- Dostupné roky a měsíce se berou ze stejného seznamu.
- `Aggregate` a čáry plánů (`relevantPlans`, `planLinesList`, `cryptoGroupLinesList`) berou jen Room data.
- Na `Shared` stránce nejsou otevřené prodeje ani akce prodeje: větve u ř. ~466 / ~505 se chovají jako u `Aggregate`.
- Titulek je `page.name` se štítkem `shared_plan_badge`.

- [ ] **Krok 1: Failing testy:**
  - `hidden amounts give no chart transactions`;
  - `mapper keeps amounts, prices and order`;
  - `shared pages come last and only for current environment`;
  - `aggregate page is unaffected by shared plans`: pro 2 vlastní plány v CZK + 1 sdílený v CZK → právě jedna `Aggregate("CZK")` a její data se počítají jen z Room.
- [ ] **Krok 2:** Filtrovaný běh → FAIL.
- [ ] **Krok 3:** Implementace, včetně všech `when (page)` (kompilátor je najde).
- [ ] **Krok 4:** Filtrovaný běh → PASS.
- [ ] **Krok 5: Ruční ověření:**
  - stránka sdíleného plánu na konci pageru, zoom rok/měsíc funguje;
  - agregovaná stránka se nezměnila;
  - na sdílené stránce chybí prodej.
- [ ] **Krok 6: Commit** `feat(portfolio): chart pages for shared plans, excluded from totals`

---

### Task 11: Závěrečné ověření

- [ ] **Krok 1:** Celá sada a lint jednou: `./gradlew :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL. Nové lint chyby oprav, baseline nerozšiřuj.
- [ ] **Krok 2:** Release build kvůli R8 a Gson: `./gradlew :app:assembleRelease` (bez keystore se staví nepodepsaný). Na emulátoru nainstaluj release APK a ověř:
  - import QR;
  - uložení a znovunačtení sdíleného plánu (`startedAt`, `tradesFromExchange` přežijí restart appky).
- [ ] **Krok 3: Ruční end-to-end** na dvou emulátorech:
  1. A sdílí živě → QR;
  2. B naskenuje → heslo → detail → stáhnout od začátku;
  3. karta na přehledu B a stránka v grafech;
  4. A znovu ukáže QR → B naskenuje → historie zůstala;
  5. restart B po plánovaném nákupu → obnova proběhne.
- [ ] **Krok 4:** Výsledek (zelený běh na `git rev-parse HEAD`) zapsat do reportu pro review.
