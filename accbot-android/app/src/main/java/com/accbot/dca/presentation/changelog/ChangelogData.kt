package com.accbot.dca.presentation.changelog

// Generated from changelog.json by scripts/generate-changelog.sh
// Do not edit manually — run the generator script after updating changelog.json

object ChangelogData {
    val entries: List<ChangelogEntry> = listOf(
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
