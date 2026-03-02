package com.accbot.dca.presentation.changelog

// Generated from changelog.json by scripts/generate-changelog.sh
// Do not edit manually — run the generator script after updating changelog.json

object ChangelogData {
    val entries: List<ChangelogEntry> = listOf(
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
