package com.cooper.binge.companion.seerr.ui.search

import java.text.Normalizer

/** Lower case, accents dropped: "Configuración" and "configuracion" are the same word to someone typing on a phone. */
internal fun String.searchForm(): String =
    Normalizer
        .normalize(lowercase(), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

/** What the user may be offered most at once: past this a longer query is the better help. */
private const val MAX_RESULTS = 30

private const val SCORE_LABEL_STARTS = 4
private const val SCORE_WORD_STARTS = 3
private const val SCORE_OTHER = 1

/**
 * The entries [query] finds among those the viewer may open ([available]), best first. Every word of the query must match: the
 * label counts most, and a word of it must start with the query's word, so "port" does not find "reports"; then the section and the
 * keywords. [text] resolves a string resource in the current language, so the search follows the app's locale with nothing of its own to
 * translate.
 */
internal fun searchSettings(
    query: String,
    entries: List<SettingEntry>,
    available: (SettingEntry) -> Boolean,
    text: (Int) -> String,
): List<SettingEntry> {
    val words = query.searchForm().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return entries
        .filter(available)
        .mapIndexedNotNull { index, entry ->
            val label = text(entry.label).searchForm()
            val other = listOfNotNull(entry.section, entry.keywords).joinToString(" ") { text(it).searchForm() }
            val total = words.sumOf { word -> wordScore(word, label, other).also { if (it == 0) return@mapIndexedNotNull null } }
            Scored(entry, total, index)
        }.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.index })
        .take(MAX_RESULTS)
        .map { it.entry }
}

private class Scored(
    val entry: SettingEntry,
    val score: Int,
    val index: Int,
)

private fun wordScore(
    word: String,
    label: String,
    other: String,
): Int =
    when {
        label.startsWith(word) -> SCORE_LABEL_STARTS
        label.split(' ').any { it.startsWith(word) } -> SCORE_WORD_STARTS
        other.split(' ').any { it.startsWith(word) } -> SCORE_OTHER
        else -> 0
    }
