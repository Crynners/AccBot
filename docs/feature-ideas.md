# AccBot — Nápady na nové funkce

Přehled potenciálních vylepšení na základě kompletní analýzy celého codebase (iOS, Android, .NET backend, Docker, Avalonia wizard).

---

## Quick Wins (malý effort, okamžitá hodnota)

### 1. Streak counter — série úspěšných nákupů
Zobrazit na Dashboardu počet po sobě jdoucích úspěšných DCA exekucí ("Jsi na 47denní sérii!"). Milestone badges za 7, 30, 100, 365, 1000 dní.

**Proč:** DCA je o dlouhodobé disciplíně. Vizuální série motivují ke konzistenci — stejná psychologie jako Duolingo streaky nebo GitHub contribution graph. Udržuje uživatele v aplikaci.

### 2. Sats-per-dollar indikátor
Ukázat, kolik satoshi (nebo crypto jednotek) uživatel dostane za 1 $/1 EUR dnes vs. jeho historický průměr. "Dnes: 1 823 sats/€ — o 12 % více než tvůj průměr!"

**Proč:** Dělá abstraktní čísla hmatatelná. Posiluje pocit, že nakupovat při dip je výhodné. Jednoduchý výpočet z aktuální ceny vs. průměrná nákupní cena.

### 3. Exchange API health indikátor
Zobrazit stav API burzy na Exchange Management — zelená/žlutá/červená. Logovat timestamp posledního úspěšného API callu.

**Proč:** Když nákup selže, uživatel neví, jestli je problém v jeho credentials nebo na straně burzy. Proaktivní status šetří čas při debugování.

### 4. Šablony plánů v onboardingu
Předpřipravené šablony: "Konzervativní BTC Spořič" (týdenní, classic), "Agresivní Dip Buyer" (denní, F&G strategie), "Multi-Coin Diverzifikátor" (více plánů). Setup na jedno kliknutí.

**Proč:** Snižuje rozhodovací paralýzu u nových uživatelů. Zrychluje onboarding — uživatel nemusí rozumět všem parametrům hned.

---

## Strategické funkce (střední effort, vysoký dopad)

### 5. DCA vs. Lump Sum porovnání
"Co kdyby?" karta ukazující, jak DCA strategie performovala vs. investování stejné celkové částky najednou v den prvního nákupu. Zobrazit rozdíl v ROI %.

**Proč:** Validuje uživatelův přístup k DCA během volatilních trhů. Edukační — pomáhá pochopit, kdy DCA vítězí a kdy ne. Potřebuje historickou cenu v den prvního nákupu + aktuální hodnotu portfolia.

### 6. Projekce akumulace
Na základě aktuálního nastavení plánu (částka, frekvence) projektovat dopředu: "Při tomto tempu budeš mít X BTC za 1/5/10 let." Volitelně overlay cenových scénářů (medvědí/neutrální/býčí).

**Proč:** Vizualizace cíle pohání motivaci. Uživatelé chtějí vidět konečný bod své strategie. Čistě matematický výpočet + jednoduchá UI karta.

### 7. Bohatší týdenní/měsíční souhrn
Periodická souhrnná karta (nebo push notifikace) s: celkem investováno tento týden/měsíc, nakoupeno crypto, průměrná cena, nejlepší/nejhorší nákup, změna portfolia.

**Proč:** Uživatelé chtějí periodické check-iny bez nutnosti otevírat appku denně. Typ `weeklySummary` notifikace už existuje jako toggle, ale samotná generace souhrnu by mohla být mnohem bohatší.

### 8. Export pro daňové účely
Export transakcí ve formátech kompatibilních s daňovým software (CoinTracker CSV, Koinly CSV, generický FIFO/LIFO report). Zobrazit realizované/nerealizované zisky.

**Proč:** Daňová compliance je hlavní bolest crypto uživatelů. Aktuální CSV export je generický — formáty specifické pro daňové nástroje přidávají reálnou hodnotu. V ČR relevantní s ohledem na 3leté daňové osvobození.

### 9. Kontrola fiat zůstatku na burze
Kontrolovat fiat zůstatek na burze před exekucí. Pokud je nedostatečný, varovat uživatele předem ("Tvůj EUR zůstatek dojde za ~3 dny").

**Proč:** Funkce "low balance warning" už existuje, ale je založená na odhadech. Skutečná kontrola zůstatku přes API by byla přesnější a spolehlivější.

### 10. Price alerty
Volitelné cenové alerty: "Upozorni mě, když BTC klesne pod X €" nebo "Alert, když BTC je >20 % pod ATH." Mohlo by triggerovat manuální "Buy Now" prompt.

**Proč:** Doplňuje DCA o oportunistické nakupování. Uživatelé už ceny sledují — integrace přímo do appky šetří přepínání mezi apps.

### 11. Návrhy na rebalancování
Pokud má uživatel více crypto plánů, zobrazit rozložení alokace (60 % BTC, 30 % ETH, 10 % SOL) a navrhnout rebalancování, pokud drift překročí práh.

**Proč:** Multi-crypto DCA uživatelé potřebují povědomí o alokaci. Pasivní rebalancování je přirozené rozšíření DCA strategie.

---

## Ambiciózní funkce (velký effort, diferenciující)

### 12. Historická DCA simulace
Před vytvořením plánu simulovat: "Kdyby ses DCAčkoval za 100 €/týden do BTC poslední 2 roky, měl bys X BTC v hodnotě Y € (Z % ROI)." S reálnými historickými daty.

**Proč:** Pomáhá novým uživatelům rozhodnout se o částkách a frekvencích. Silný onboarding nástroj. Potřebuje historická cenová data + simulační engine.

### 13. Home screen widgety (iOS/Android)
Widgety na domovskou obrazovku: hodnota portfolia, odpočet do další exekuce, streak counter, dnešní sats-per-euro.

**Proč:** Pasivní engagement bez otevírání appky. Vysoká hodnota pro denní DCA uživatele. Ale velký scope — platformově specifické implementace.

### 14. Execution log / Debug konzole
Detailní log každého pokusu o DCA exekuci: timestamp, odpověď burzy, order ID, fill cena, poplatky, chyby. Zobrazitelný v Settings > Debug.

**Proč:** Když něco nefunguje, uživatelé potřebují troubleshootovat. Aktuálně se chyby zobrazují na transakcích, ale celý execution flow není viditelný.

### 15. Sdílení DCA journey
"Sdílej svou DCA cestu" — vygenerovat sdílitelnou kartu/obrázek se statistikami (celkem akumulováno, streak, ROI) bez odhalení absolutních částek. Privacy-first sdílení.

**Proč:** Organický růst. Bitcoin komunita ráda sdílí svůj stacking progress. Anonymizované statistiky (jen %) zachovávají soukromí.

---

## Shrnutí priorit

| Priorita | Feature | Effort | Dopad |
|----------|---------|--------|-------|
| 1 | Streak counter | Malý | Vysoký |
| 2 | Sats-per-dollar | Malý | Střední |
| 3 | Exchange health | Malý | Střední |
| 4 | Šablony plánů | Malý | Vysoký |
| 5 | DCA vs Lump Sum | Střední | Vysoký |
| 6 | Projekce akumulace | Střední | Vysoký |
| 7 | Bohatší souhrny | Střední | Střední |
| 8 | Daňový export | Střední | Vysoký |
| 9 | Fiat balance check | Střední | Střední |
| 10 | Price alerty | Střední | Střední |
| 11 | Rebalancování | Střední | Střední |
| 12 | Historická simulace | Velký | Vysoký |
| 13 | Widgety | Velký | Vysoký |
| 14 | Debug konzole | Malý-Střední | Střední |
| 15 | Sdílení journey | Střední | Střední |
