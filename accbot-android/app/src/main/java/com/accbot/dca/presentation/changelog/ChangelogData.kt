package com.accbot.dca.presentation.changelog

// Generated from changelog.json by scripts/generate-changelog.sh
// Do not edit manually — run the generator script after updating changelog.json

object ChangelogData {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionCode = 28000,
            version = "2.8.0",
            titles = mapOf(
                "cs" to "Prodej kryptoměn a zisk portfolia",
                "en" to "Sell Your Crypto & Portfolio P&L",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Prodávejte krypto přímo z aplikace - jednorázový prodej za vaši cílovou cenu, nebo žebříček prodejních příkazů",
                    "Přehled otevřených prodejních příkazů s možností je kdykoliv zrušit",
                    "Portfolio nově zobrazuje realizovaný a celkový zisk/ztrátu",
                    "Filtrování historie transakcí podle plánu",
                    "Přehlednější graf portfolia",
                    "Spolehlivější naplánované i ruční nákupy",
                    "Různé opravy chyb a vylepšení stability",
                ),
                "en" to listOf(
                    "Sell crypto right from the app - a single sell at your target price, or a ladder of sell orders",
                    "Track open sell orders and cancel them anytime",
                    "Portfolio now shows realized and total profit/loss",
                    "Filter transaction history by plan",
                    "Cleaner portfolio chart",
                    "More reliable scheduled and manual purchases",
                    "Various bug fixes and stability improvements",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 27000,
            version = "2.7.0",
            titles = mapOf(
                "cs" to "Spolehlivější nákupy a lepší UX",
                "en" to "Reliable Purchases & Better UX",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Aplikace nyní upozorní, pokud opustíte nastavení plánu bez uložení",
                    "Zmeškané nákupy po výpadku připojení nebo restartu telefonu se automaticky detekují a nabídne se jejich doběhnutí",
                    "Při selhání nákupu kvůli chybějícímu internetu dostanete notifikaci — s tlačítkem pro okamžité spuštění přímo na úvodní obrazovce",
                    "Notifikace o nákupu ukazují zpoždění, pokud proběhl později než bylo naplánováno",
                    "Binance: vylepšené zobrazení částek a informace o velikosti lotu pro každé krypto",
                    "Obrazovka exportu zálohy má nyní tlačítko Hotovo pro zavření",
                    "Různé opravy chyb a vylepšení stability",
                ),
                "en" to listOf(
                    "App now warns you before leaving unsaved changes in plan settings",
                    "Missed purchases are detected and can be recovered after going offline or rebooting",
                    "You'll get notified when a purchase fails due to no internet — with a retry button right on the dashboard",
                    "Purchase notifications show the delay when a buy happened later than scheduled",
                    "Binance: improved amount display and lot size info for each crypto",
                    "Backup export screen now has a Done button to close it",
                    "Various bug fixes and stability improvements",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 26100,
            version = "2.6.1",
            titles = mapOf(
                "cs" to "DRY refaktoring a Binance USDC",
                "en" to "DRY Refactor & Binance USDC",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Sdílený CredentialFormDelegate — méně duplicitního kódu napříč 4 ViewModely",
                    "Sdílený dialog výsledku API importu na 3 obrazovkách",
                    "Jednotný AccBotTopAppBar na 11 obrazovkách",
                    "Binance: přechod z USDT na USDC, minimální objednávka snížena na 5",
                    "Rychlé částky: 5, 10, 25, 50, 100 (dříve 25–500)",
                    "Výchozí částka DCA plánu nastavena na minimum burzy",
                    "Oprava zobrazení minimální částky — bez zbytečných nul",
                    "Extrakce KuCoin signed-request helperu a ROI výpočtu",
                ),
                "en" to listOf(
                    "Shared CredentialFormDelegate — less duplicate code across 4 ViewModels",
                    "Shared API import result dialog across 3 screens",
                    "Unified AccBotTopAppBar across 11 screens",
                    "Binance: switch from USDT to USDC, min order lowered to 5",
                    "Quick amounts: 5, 10, 25, 50, 100 (was 25–500)",
                    "Default DCA plan amount set to exchange minimum",
                    "Fix min order size display — strip trailing zeros",
                    "Extract KuCoin signed-request helper and ROI calculation",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 26000,
            version = "2.6.0",
            titles = mapOf(
                "cs" to "Výkon a chytré obnovování",
                "en" to "Performance & Smart Refresh",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Chytré obnovování — Dashboard a Portfolio načítají data jen když jsou zastaralá (5 min)",
                    "SQL filtrování v historii transakcí — rychlejší s velkým množstvím dat",
                    "Debounce vyhledávání (300ms) — plynulejší psaní v historii",
                    "Cachování Fear & Greed indexu (1h TTL) — méně API volání",
                    "Real-time cena v grafu portfolia — dnešní bod se aktualizuje okamžitě",
                    "Optimalizované pořadí načítání tržních dat — warm-up cache před dotazy na ceny",
                    "Nový DB index na transakcích pro rychlejší filtrované dotazy",
                    "Úklid repositáře — archivace neaktivních .NET, Docker a legacy projektů",
                ),
                "en" to listOf(
                    "Smart refresh — Dashboard and Portfolio only reload when data is stale (5 min)",
                    "SQL-level filtering in transaction history — faster with large datasets",
                    "Search debounce (300ms) — smoother typing in history search",
                    "Fear & Greed index caching (1h TTL) — fewer API calls",
                    "Real-time price in portfolio chart — today's data point updates immediately",
                    "Market data fetch order optimized — cache warm-up before price lookups",
                    "New DB index on transactions for faster filtered queries",
                    "Repository cleanup — archived inactive .NET, Docker and legacy projects",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 25200,
            version = "2.5.2",
            titles = mapOf(
                "cs" to "Oprava grafu a branding",
                "en" to "Chart Fix & Branding",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Oprava pádu grafu portfolia s jedinou transakcí",
                    "Použití ceny transakce jako zálohy, když chybí denní cenová data",
                    "#OwnYourDCA branding v Nastavení",
                ),
                "en" to listOf(
                    "Fix portfolio chart crash with a single transaction",
                    "Use transaction execution price as fallback when daily price data is missing",
                    "#OwnYourDCA branding in Settings",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 25100,
            version = "2.5.1",
            titles = mapOf(
                "cs" to "Own your DCA — Branding a opravy",
                "en" to "Own your DCA — Branding & Bugfixes",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Nový slogan: DCA patří vám, ne burze ani žádné třetí straně",
                    "Aktualizované texty na landing page a uvítací obrazovce",
                    "Drobné opravy: reset časovače, minimum Coinmate EUR, auto-aktivace Market Pulse",
                ),
                "en" to listOf(
                    "New tagline: Own your DCA — your keys, your data, your rules",
                    "Updated landing page and welcome screen messaging",
                    "Minor fixes: timer reset, Coinmate EUR minimum, Market Pulse auto-activation",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 25000,
            version = "2.5.0",
            titles = mapOf(
                "cs" to "Sjednocené UX burz a vylepšení onboardingu",
                "en" to "Unified Exchange UX & Onboarding Improvements",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Experimentální burzy — vyzkoušejte nové burzy a požádejte o chybějící",
                    "Informační list Market Pulse — vysvětlení Fear & Greed a vzdálenosti od ATH",
                    "Onboarding obrazovka oprávnění — přehledné nastavení notifikací a baterie",
                    "Sjednocené UI výběru burzy na všech obrazovkách",
                    "Coinmate paste-only zadávání credentials s API URL dle jazyka",
                    "Plná přesnost satoshi pro částky pod 1 jednotku kryptoměny",
                    "Rychlé částky filtrované dle minimální velikosti objednávky",
                    "Vylepšené instrukce při vytváření plánu a scrollovatelné kroky",
                ),
                "en" to listOf(
                    "Experimental exchanges — try new exchanges and request missing ones",
                    "Market Pulse info sheet — learn what Fear & Greed and ATH distance mean",
                    "Onboarding Permissions screen — guided notification and battery setup",
                    "Unified exchange selection UI across all screens",
                    "Coinmate paste-only credential flow with locale-aware API URL",
                    "Full satoshi precision for sub-1 crypto amounts",
                    "Quick amounts filtered by exchange minimum order size",
                    "Improved plan creation instructions and scrollable steps",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 24200,
            version = "2.4.2",
            titles = mapOf(
                "cs" to "Notifikace reagující na změnu jazyka",
                "en" to "Locale-Aware Notifications",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Notifikace se okamžitě přerenderují při přepnutí jazyka",
                    "Strukturované šablony notifikací (templateArgs) pro jazykově nezávislé ukládání",
                    "Podpora zálohování a obnovy notifikací",
                ),
                "en" to listOf(
                    "Notifications re-render instantly when switching language",
                    "Structured notification templates (templateArgs) for locale-independent storage",
                    "Notification backup & restore support",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 24100,
            version = "2.4.1",
            titles = mapOf(
                "cs" to "Opravy stability a CI",
                "en" to "Stability & CI Fixes",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Oprava R8 obfuskace narušující parsování API odpovědí v release buildech",
                    "Chytré cachování ATH dat — méně zbytečných síťových volání",
                    "Oprava CI pipeline — odstranění závislosti na PAT pro release workflow",
                ),
                "en" to listOf(
                    "Fix R8 obfuscation breaking API JSON parsing in release builds",
                    "Smart ATH caching — reduces redundant network calls",
                    "CI pipeline fix — remove PAT dependency for release workflow",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 24000,
            version = "2.4.0",
            titles = mapOf(
                "cs" to "Market Pulse, adaptivní grafy a čistší UI",
                "en" to "Market Pulse, Adaptive Charts & Cleaner UI",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Market Pulse karta na Dashboard — Fear & Greed ukazatel + vzdálenost od ATH",
                    "Sbalitelný Market Pulse s přepínačem v Nastavení",
                    "Adaptivní granularita grafů — denní, týdenní nebo měsíční dle rozsahu dat",
                    "Průměrná nákupní cena v grafech portfolia",
                    "Filtr data importu — výběr počátečního data pro API importy",
                    "Přehlednější obrazovka detailu plánu",
                    "Přečtené/nepřečtené notifikace se smazáním swipem",
                    "Přeorganizované Nastavení (méně sekcí, WCAG přístupnost)",
                    "Sjednocená 5-úrovňová klasifikace Fear & Greed",
                    "Vylepšení světlého motivu a opravy barev",
                    "Odstraněn CSV import (nahrazen API importem)",
                ),
                "en" to listOf(
                    "Market Pulse dashboard card — Fear & Greed gauge + ATH distance",
                    "Collapsible Market Pulse with settings toggle",
                    "Adaptive chart aggregation — daily, weekly, or monthly based on data span",
                    "Avg buy price line in portfolio charts",
                    "API import date filter — choose start date for imports",
                    "Consolidated plan details screen (cleaner layout)",
                    "Read/unread notifications with swipe-to-delete",
                    "Reorganized Settings (fewer sections, WCAG accessibility)",
                    "Unified Fear & Greed 5-level classification",
                    "Light theme polish and color fixes",
                    "Removed CSV import (replaced by API import)",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 23000,
            version = "2.3.0",
            titles = mapOf(
                "cs" to "Záloha a obnova",
                "en" to "Backup & Restore",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Šifrovaný export a import zálohy",
                    "Režimy obnovy: sloučení nebo nahrazení",
                    "Podpora importu seed phrase",
                ),
                "en" to listOf(
                    "Encrypted backup export and import",
                    "Merge or replace restore modes",
                    "Seed phrase import support",
                ),
            )
        ),
        ChangelogEntry(
            versionCode = 22100,
            version = "2.2.1",
            titles = mapOf(
                "cs" to "Sledování cílů, vyhledávání a motivy",
                "en" to "Goal Tracking, Search & Themes",
            ),
            features = mapOf(
                "cs" to listOf(
                    "Sledování cílů — nastavení cílové částky kryptoměny pro DCA plán",
                    "Výběr motivu — Tmavý, Světlý nebo Podle systému",
                    "Celkové shrnutí portfolia na Dashboard",
                    "Vyhledávání v historii transakcí",
                    "Informace o kanálu oznámení v Nastavení",
                    "Obrazovka Co je nového po aktualizacích",
                ),
                "en" to listOf(
                    "Goal tracking — set a target crypto amount for your DCA plan",
                    "Theme selection — choose Dark, Light, or System",
                    "Total portfolio summary on Dashboard",
                    "Transaction history search",
                    "Notification channel info in Settings",
                    "What's New screen after updates",
                ),
            )
        ),
    )

    fun getNewEntries(lastSeenVersionCode: Int): List<ChangelogEntry> =
        entries.filter { it.versionCode > lastSeenVersionCode }
}
