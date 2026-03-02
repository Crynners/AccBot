package com.accbot.dca.presentation.changelog

data class ChangelogEntry(
    val versionCode: Int,
    val version: String,
    val titles: Map<String, String>,
    val features: Map<String, List<String>>
) {
    fun title(locale: String): String =
        titles[locale] ?: titles["en"] ?: ""

    fun features(locale: String): List<String> =
        features[locale] ?: features["en"] ?: emptyList()
}
