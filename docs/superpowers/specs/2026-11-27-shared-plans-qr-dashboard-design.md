# Sdílené plány: QR kód, přehled a grafy, drobnosti formulářů

Datum: 2026-11-27
Platforma: Android (`accbot-android`), větev `claude/accbot-main-2026-11-27`

## Proč

Sdílení plánů (Honza, 25.-26.11.2026) funguje, ale v praxi se nepohodlně používá:
- **Dialog se zavírá snadno.** Dialog „Sdílet plán“ se zavře klepnutím mimo, takže se snadno ztratí vyplněný klíč a heslo.
- **Heslo nejde zkontrolovat.** U hesla není vidět, co uživatel napsal.
- **Formulář schovává klávesnice.** Při psaní zakryje klávesnice spodek formuláře.
- **Předávání je pracné.** Sdílí se soubor JSON, který se musí poslat a pak otevřít. Mezi dvěma telefony vedle sebe je rychlejší QR kód.
- **Sdílené plány jsou mimo.** Přijaté plány jsou schované v Nastavení → Sdílené plány. Uživatel je chce mít na přehledu mezi svými plány a v grafech.

Úspěch znamená:
- Dva telefony vedle sebe předají živé sdílení naskenováním QR kódu a zadáním hesla.
- Příjemce si stáhne historii nákupů z burzy.
- Sdílený plán pak vidí na přehledu i v grafech, jasně označený jako sdílený.

## Cíle

1. **Dialog „Sdílet plán“ jde zavřít jen tlačítkem Zrušit nebo Sdílet.**
   - Nezavře ho klepnutí mimo dialog.
   - Nezavře ho systémové Zpět. Zpět může dělat totéž co Zrušit, ale jen dokud se nic nevytváří. Viz „Rozhodnutí builderovi“.
2. **Očíčko (zobrazit/skrýt) u všech polí s heslem nebo tajemstvím v appce.**
   - Výchozí stav je skryto.
   - Týká se těchto polí:
     - `SharePlanDialog.SecretField`;
     - `SharedPlansScreen.SharePasswordDialog`;
     - `BackupExportScreen` (heslo a potvrzení hesla);
     - `BackupImportScreen`;
     - `CredentialsInputCard`, 4 výskyty `PasswordVisualTransformation`. Tam, kde už přepínač je, se jen sjednotí vzhled.
3. **Klávesnice nezakrývá obsah.**
   - Ve formulářích s posuvným obsahem se obsah posune nad klávesnici a pole, do kterého se píše, zůstane vidět.
   - Týká se hlavně `SharePlanDialog` (AlertDialog s `verticalScroll`).
   - Týká se i obrazovek z bodu 2 a obrazovek přidání burzy/plánu, pokud mají stejný problém.
   - Appka používá `enableEdgeToEdge()` a v manifestu nemá `windowSoftInputMode`. `imePadding` je zatím jen v `SellWizardBottomSheet`.
4. **Živé sdílení přes QR kód.**
   - Po vytvoření živého sdílení (sdílení s klíčem pro čtení) appka zobrazí QR kód.
   - Druhá appka ho naskenuje fotoaparátem, zeptá se na heslo a plán uloží.
5. **Stažení historie sdíleného plánu z burzy** stejnými volbami jako u vlastních plánů: od posledního nákupu, od data, celá historie.
6. **Sdílené plány na přehledu a v grafech**, bez přepínače zapnout/vypnout a se štítkem „Sdílený plán“.
7. **Import sdíleného plánu fotoaparátem.**
   - V Nastavení → Sdílené plány přibude vedle „Otevřít soubor“ volba „Naskenovat QR kód“.

## Mimo rozsah

- **iOS.** Formát QR obsahu je stejný JSON jako soubor (viz níže), takže iOS dál umí soubor. Skenování a zobrazení QR na iOS je samostatný úkol.
- **QR pro sdílení bez klíče** (statický snímek s historií). Historie se do QR nevejde, takový plán se dál sdílí jen souborem.
- **Víc QR kódů za sebou** a komprese historie do QR.
- **Změna šifrování a hesla** u sdíleného souboru.

## Co nesmí nastat (anti-cíle)

- **Sdílený plán nesmí ovlivnit vlastní čísla.** Nesmí se započítat do celkových součtů portfolia, do „Držíš“, do ROI ani do souhrnů na přehledu. Nejsou to uživatelovy peníze.
- **Na sdíleném plánu nesmí být žádná akce, která něco spustí nebo změní na burze.** To znamená žádný přepínač zapnout/vypnout, „Spustit teď“, prodej ani výběr. Klíč se dál používá jen přes `ReadOnlyExchangeClient`.
- **API klíč v QR kódu nesmí být čitelný bez hesla.** QR obsah je vždy zašifrovaný stejně jako soubor živého sdílení.
- **Stažení historie nesmí vytvořit duplicitní obchody.**
- **Existující soubory sdílení musí jít importovat dál**, z Androidu i z iOS.

## Jak to dnes funguje

- **Sdílení** dělá `CreatePlanShareUseCase` v `domain/usecase/SharePlanUseCases.kt`. Vytvoří `PlanShareFile(fileName, content)`:
  - `content` je JSON `SharedPlanCodec.encodeShare(...)`;
  - u živého sdílení je celý snímek (včetně `transactions` a `access` s klíčem) zašifrovaný heslem do pole `data` přes `BackupCryptoUseCase`;
  - v otevřené části zbývá jen `format`, `version` a `encrypted`.
- **Import** dělá `ImportSharedPlanUseCase(json, password)`. Dekóduje, u klíče ověří přes `ReadOnlyExchangeClient.verifyKey()`, že nejde obchodovat, a uloží do `SharedPlanStore` (soubory + klíč zvlášť).
- **Obnova** dělá `RefreshSharedPlanUseCase`. Stáhne obchody od posledního známého obchodu, nejdřív však od `sharedAt`. Metoda `merge` záměrně ignoruje vše do `sharedAt`, protože to patří snímku: snímek má reference podle order ID, ale Binance a Coinbase vrací historii podle trade ID, takže se nedají spárovat.
- **Burza vrací historii po účtech a párech.** Do sdíleného plánu se tak dostanou i ruční nákupy nebo jiné plány stejného páru na tom účtu. To už je známé a přijaté omezení.
- **QR infrastruktura už existuje:**
  - generování QR přes ZXing je v `BackupExportViewModel.generateQrBitmap`, s limitem `CreateBackupUseCase.QR_MAX_BYTES = 1850`;
  - skenování je `presentation/components/QrScanner.kt` (`QrScannerDialog`, ML Kit + CameraX);
  - oprávnění `CAMERA` už v manifestu je.
- **Import historie u vlastních plánů:**
  - dialog je `presentation/components/ImportConfigDialog.kt`, výběr rozsahu přes `ImportRange`: `SinceLastPurchase`, `SinceDate`, celá historie;
  - logika je v `ImportTradeHistoryUseCase`.
- **Přehled:** `DashboardScreen` / `DashboardViewModel` ukazuje `activePlans: List<DcaPlanWithBalance>` přes `DcaPlanCard`.
- **Grafy:** `PortfolioScreen` / `PortfolioViewModel` mají stránky `PairPage.Aggregate(fiat)` a `PairPage.Plan(planId, ...)`. Data grafu počítá `CalculateChartDataUseCase` z transakcí v Room.
- **Sdílené plány nejsou v Room.** Jsou v `SharedPlanStore`, s `StateFlow<List<SharedPlan>?>`.

## Zvolený přístup a alternativy

Uživatel vybral **QR jen pro živé sdílení**: v QR je klíč pro čtení a údaje o plánu, bez historie. Historii si příjemce stáhne z burzy.

Zvažované alternativy:
- **QR s historií, když se vejde, jinak soubor.**
  - Výhoda: jednoduché.
  - Nevýhoda: u denních nákupů QR za pár měsíců přestane stačit, takže by se použil jen výjimečně.
- **Několik QR kódů za sebou.**
  - Výhoda: funguje pro jakoukoli velikost.
  - Nevýhoda: výrazně víc práce na obou stranách a pomalejší skenování.

Uživatel to výslovně odmítl („stačí živé sdílení“).

## Návrh

### QR obsah

- **Formát:** QR obsahuje přesně tentýž formát jako soubor živého sdílení. Je to JSON z `SharedPlanCodec.encodeShare`, zašifrovaný heslem, jen s prázdným seznamem `transactions`.
  - Import tedy jde stejnou cestou (`ImportSharedPlanUseCase`) a obsah QR uložený do souboru jde otevřít i na iOS.
- **API:** `CreatePlanShareUseCase` dostane možnost vynechat transakce (např. parametr `includeTransactions`, nebo samostatná funkce pro QR).
  - Souhrn (`summary.avgBuyPrice`) se v QR vynechá také, protože bez transakcí nedává smysl.
  - Příjemce vidí plán bez obchodů, dokud nestáhne historii.
- **Začátek plánu:** QR i soubor nesou datum začátku plánu (`plan.startedAt`, epoch ms).
  - Je to dřívější z `createdAt` plánu a data první transakce plánu. Když si uživatel do plánu importoval starší historii, patří i ta k plánu.
  - Od tohoto data příjemce stahuje historii a nic staršího nestáhne. Starší obchody stejného páru na účtu (jiné plány, ruční nákupy před začátkem) se tak do sdíleného plánu nedostanou.
  - Pole je volitelné. Starší soubory ho nemají a pak se použije dřívější z `createdAt` a prvního obchodu ve snímku. iOS neznámé pole ignoruje.
- **Velikost:** odhad je pod 1 kB: údaje o plánu + klíč + secret, AES-GCM a base64.
  - Limit převzít z `QR_MAX_BYTES` (1850), nejlépe jako sdílenou konstantu.
  - Když by se výjimečně nevešel (dlouhý passphrase, název), QR se nezobrazí a zůstane jen soubor s vysvětlením, stejně jako u zálohy.
- **Generování** QR bitmapy vytáhnout z `BackupExportViewModel` do sdílené utility a použít na obou místech.

### Dialog „Sdílet plán“

- **Zavírání:**
  - `onDismissRequest` nedělá nic;
  - `DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)`;
  - zavírá se jen tlačítky.
  - Zpět buď neudělá nic, nebo se chová jako Zrušit. Pokud má Zrušit zahodit vyplněná data, nesmí to jít jedním omylem: Zpět dialog nezavře.
- **Po stisku Sdílet u živého sdílení** se dialog nezavře, ale přepne do stavu výsledku:
  - velký QR kód;
  - text „Naskenujte ve druhé appce AccBot: Nastavení → Sdílené plány → Naskenovat QR kód. Heslo předejte zvlášť.“;
  - tlačítko „Sdílet soubor“, které otevře dnešní systémové sdílení souboru s historií;
  - tlačítko „Hotovo“.
- **U sdílení bez klíče** zůstává dnešní chování: Sdílet rovnou otevře sdílení souboru.
- **Klávesnice:** obsah dialogu se při otevřené klávesnici posune tak, aby aktivní pole bylo vidět.
  - Typicky `imePadding()` na obsahu plus `Modifier.bringIntoViewRequester`, nebo `DialogProperties(decorFitsSystemWindows = false)` s `imePadding`.
  - Ověřit na zařízení s malým displejem.

### Import přes fotoaparát

- V `SharedPlansScreen` přibude akce „Naskenovat QR kód“ (stejný styl jako „Naskenovat QR“ v `BackupImportScreen`). Otevře `QrScannerDialog`.
- Po naskenování:
  - pokud obsah není platné sdílení (`SharedPlanException`), zobrazí se chyba a skener jde otevřít znovu;
  - jinak se otevře stávající `SharePasswordDialog`, už s očíčkem, a pokračuje se stejně jako u souboru.
- Po úspěšném importu QR (plán bez obchodů) se rovnou otevře detail sdíleného plánu s nabídkou „Stáhnout historii“.
  - Inspirace je `66003a6`: po vytvoření připojení se rovnou otevře import.

### Stažení historie u sdíleného plánu

- V detailu sdíleného plánu (`SharedPlanDetailScreen`) přibude akce „Stáhnout historii“, jen u plánů s klíčem (`liveUpdates`). Otevře `ImportConfigDialog`.
- Význam rozsahů u sdíleného plánu:
  - **Od posledního nákupu** (výchozí, pokud plán nějaký obchod má): jako dnešní obnova (`RefreshSharedPlanUseCase`), obchody se připojí.
  - **Od data:** obchody od zvoleného data se nahradí tím, co vrátí burza. Starší obchody zůstanou.
    - Datum nejde zvolit dřív, než plán začal: `selectableDates` v `ImportConfigDialog` dostane spodní hranici.
  - **Od začátku plánu** (místo „Celá historie“; výchozí u plánu bez obchodů, např. z QR): všechny obchody plánu se nahradí tím, co vrátí burza od začátku plánu (`startedAt`). Nic staršího se nestahuje.
- **Proč nahrazovat, a ne slučovat:** snímek a burza obchody identifikují různě (order ID vs trade ID), takže sloučení by vytvořilo duplicity. Nahrazením v rozsahu duplicity nevzniknou.
  - Slučování po `ref` zůstává jen pro připojování nových obchodů.
- Dialog upozorní, že burza vrací všechny obchody páru na účtu. Obchody před začátkem plánu se vynechají, ale ruční nákupy nebo jiné plány stejného páru **po** začátku se od nákupů plánu odlišit nedají.
- **Průběh a výsledek** jako u vlastních plánů (`ApiImportProgress`, `ApiImportResultDialog`): počet stažených obchodů, chyba burzy.
- **Ostatní pravidla obnovy platí dál:** limit stránek (`MAX_PAGES`, `PAGE_SIZE`), kontrola `verifyKey()` (klíč, který začal umět obchodovat, se odstraní, pokud nejde o `fullAccessKey`) a zrušení přes `CancellationException`.

### Opakovaný import stejného plánu

- Stejný `shareId` může přijít znovu. Odesílatel ukáže nový QR, nebo příjemce otevře „Sdílet soubor“ po QR.
- **QR obsah (bez obchodů):** uložené obchody zůstanou a aktualizuje se jen klíč a údaje plánu (název, částka, frekvence, `startedAt`).
  - `sharedAt` se kvůli `merge` nesmí posunout tak, aby vznikla mezera: obchody mezi posledním uloženým obchodem a novým `sharedAt` by se už nikdy nestáhly.
  - Plán, jehož obchody pocházejí z burzy (po „Stáhnout historii“ nebo z QR), si to pamatuje, např. příznakem v `SharedPlanLocalDto`. U takového plánu `merge` nepoužije hranici `sharedAt` a deduplikuje jen podle `ref`.
- **Soubor s obchody:** chová se jako dnes. Novější snímek nahradí uložený plán.

### Aktualizace z burzy

Sdílené plány s klíčem (`liveUpdates`) se aktualizují přes `RefreshSharedPlanUseCase`:
- **při otevření detailu** a ruční obnově, jako dnes;
- **při stažení dolů (pull-to-refresh) na přehledu**, všechny sdílené plány aktuálního prostředí;
- **při otevření aplikace** (start nebo návrat do popředí), ale jen u plánů, kde je plánovaný nákup už v minulosti a pravděpodobně proběhl.
  - Příští plánovaný nákup se spočítá z `frequency` / `cronExpression` od posledního známého obchodu, nebo od `startedAt`, když obchod není.
  - Když je tento čas v minulosti a plán se od té doby neaktualizoval (`refreshedAt` je před ním), proběhne obnova.
  - Plán s vypnutým odesílatelovým plánem (`isEnabled = false`) se při otevření aplikace neobnovuje.
  - Pokud se obnova nepovede (`refreshFailed`), další pokus až při dalším otevření aplikace, ne ve smyčce.
- Na pozadí (worker) se neobnovuje.
- Výsledek obnovy z přehledu se ukáže na kartě:
  - čas poslední aktualizace;
  - varování při chybě;
  - varování, když byl klíč odebrán, protože začal umět obchodovat (`KeyNotReadOnly`).
  - Žádné vyskakovací okno.

### Přehled (Dashboard)

- **Kde:** sdílené plány se zobrazí v sekci DCA plánů, **pod** vlastními plány. Vlastní plány zůstanou nahoře, aby se nepletly.
- **Karta:** varianta karty plánu (stejný vzhled jako `DcaPlanCard`), ale:
  - je na ní štítek „Sdílený plán“ (ikona sdílení + text);
  - nemá přepínač zapnout/vypnout ani odpočet do dalšího nákupu (sdílený plán nic nespouští);
  - ukazuje název, pár, burzu, poslední nákup, nakoupené množství a výkon proti aktuální ceně, když jsou částky k dispozici (`SharedPlanSummary`);
  - klepnutí otevře `SharedPlanDetailScreen`.
- **Prázdný stav:** sdílený plán se nepočítá jako „vlastní plán“ pro průvodce prvním plánem, upozornění „Needs attention“, zmeškané nákupy ani automatizaci.
- **Aktuální cena:** stejný zdroj jako dnes v detailu sdíleného plánu.
- **Prostředí:** sdílený plán ze sandboxu se na přehledu ukazuje jen v sandboxu, ostrý jen v ostrém (podle `SharedPlan.isSandbox`), stejně jako vlastní plány.

### Grafy (Portfolio)

- Každý sdílený plán s obchody, kde jsou známé částky (`includesAmounts`), dostane vlastní stránku v pageru Portfolia. Stránky budou na konci, za vlastními plány, se štítkem „Sdílený plán“.
- Graf sdíleného plánu ukazuje stejné řady jako graf vlastního plánu (investováno, hodnota), počítané z `SharedPlan.trades`.
- **Sdílené plány se nezapočítávají** do agregované stránky (`PairPage.Aggregate`) ani do celkových součtů a čar jiných plánů.
- Na stránce sdíleného plánu nejsou otevřené prodeje ani akce prodeje.
- Sdílený plán bez částek (snímek se skrytými částkami) v grafech není a na přehledu ukáže jen průměrnou cenu a výkon.

## Rozhodnutí, která udělal Claude (uživatel je může změnit)

- Sdílené plány nejsou součástí celkových součtů a mají vlastní stránku v grafech.
- Na přehledu jsou pod vlastními plány, ne promíchané.
- Jen Android. iOS dál umí soubor a QR na iOS je zvláštní úkol.
- „Od data“ a „Celá historie“ u sdíleného plánu nahrazují obchody v rozsahu, nesčítají je.
- Po importu z QR se rovnou otevře detail s nabídkou stažení historie.
- QR obsah je stejný formát jako soubor, takže není potřeba nový formát ani nová verze. Přibude jen volitelné pole `plan.startedAt`.
- Začátek plánu je dřívější z data vytvoření plánu a jeho první transakce. Na přání uživatele se nic staršího nestahuje.
- Opakovaný import z QR zachová staženou historii a aktualizuje jen klíč a údaje plánu.
- Obnova při otevření aplikace se přeskočí u plánu, který má odesílatel vypnutý. Po chybě se zkouší až při dalším otevření.

## Rozhodnutí builderovi

- Přesná technika posunu obsahu nad klávesnici (imePadding / bringIntoView / decorFitsSystemWindows) a seznam obrazovek, kde se projeví. Ověřit na emulátoru s malým displejem.
- Zda systémové Zpět v dialogu „Sdílet plán“ nedělá nic, nebo se chová jako Zrušit. Nesmí ale tiše zahodit vyplněná data.
- Jak přesně v kódu oddělit Room plány a sdílené plány ve view modelech: nová sealed položka seznamu, nebo samostatný seznam v `DashboardUiState`.
- Zda graf sdíleného plánu půjde přes `CalculateChartDataUseCase` (převodem `SharedPlanTrade` na vstup use case) nebo přes vlastní malý výpočet.
- Texty (cs + en v `strings.xml`) a ikony.

## Testy

- **Unit:**
  - `CreatePlanShareUseCase` bez transakcí;
  - round-trip QR obsahu přes `ImportSharedPlanUseCase`;
  - kontrola velikosti proti limitu;
  - nahrazování obchodů v rozsahu u stažení historie, bez duplicit;
  - připojování od posledního nákupu;
  - vynechání obchodů před `startedAt`, včetně fallbacku u souborů bez tohoto pole;
  - opakovaný import QR zachová obchody a nevznikne mezera v `merge`;
  - rozhodnutí „obnovit při otevření aplikace“ (příští nákup v minulosti, vypnutý plán, `refreshedAt` po plánovaném nákupu);
  - to, že agregovaná data portfolia ani přehled nezahrnují sdílené plány.
- **Ručně na emulátoru:**
  - dialog nejde zavřít klepnutím mimo;
  - očíčko u všech polí;
  - klávesnice nezakrývá aktivní pole;
  - QR → naskenovat (druhý emulátor nebo vyfocený QR) → heslo → detail → stáhnout historii → karta na přehledu a stránka v grafech.
