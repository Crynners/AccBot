package com.accbot.dca.presentation.changelog

// Generated from changelog.json by scripts/generate-changelog.sh
// Do not edit manually — run the generator script after updating changelog.json

object ChangelogData {
    val entries: List<ChangelogEntry> = listOf(
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
                    "What’s New screen after updates",
                ),
            )
        ),
    )

    fun getNewEntries(lastSeenVersionCode: Int): List<ChangelogEntry> =
        entries.filter { it.versionCode > lastSeenVersionCode }
}
