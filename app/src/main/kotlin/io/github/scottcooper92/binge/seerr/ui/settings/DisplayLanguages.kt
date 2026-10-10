package io.github.scottcooper92.binge.seerr.ui.settings

import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.attempt
import java.util.Locale

/**
 * The display languages a server's web client can be shown in. The server keeps them as a constant
 * (`availableLocales` in `server/types/languages.ts`) and the API never lists them, so this app carries its own copy,
 * one per lineage, read from each fork's source: Seerr and Jellyseerr ship the same 37, and Overseerr 32 of them.
 */
internal object DisplayLanguages {
    private val JELLYSEERR_LINEAGE =
        listOf(
            "ar",
            "bg",
            "ca",
            "cs",
            "da",
            "de",
            "en",
            "el",
            "es",
            "es-MX",
            "et",
            "fi",
            "fr",
            "hr",
            "he",
            "hi",
            "hu",
            "it",
            "ja",
            "ko",
            "lb",
            "lt",
            "nb-NO",
            "nl",
            "pl",
            "pt-BR",
            "pt-PT",
            "ro",
            "ru",
            "sq",
            "sr",
            "sv",
            "tr",
            "uk",
            "zh-CN",
            "zh-TW",
            "vi",
        )

    private val OVERSEERR = JELLYSEERR_LINEAGE - setOf("es-MX", "et", "lb", "tr", "vi")

    /** The codes [variant] accepts; a fork this app can't place gets the larger list, since a pick it lacks is rare. */
    fun codesFor(variant: SeerrVariant): List<String> = if (variant == SeerrVariant.Overseerr) OVERSEERR else JELLYSEERR_LINEAGE

    /**
     * The choices for a picker over [variant]'s languages, each named in its own language and sorted by that name. A
     * [current] value outside the list (typed in by hand, or from a newer server) comes first under its code, so
     * opening the picker never quietly changes it.
     */
    fun choices(
        variant: SeerrVariant,
        current: String,
    ): List<Pair<String, String>> {
        val known = codesFor(variant).map { it to nativeName(it) }.sortedBy { it.second.lowercase() }
        val kept = current.trim().takeIf { it.isNotEmpty() && known.none { (code) -> code == it } }
        return listOfNotNull(kept?.let { it to it }) + known
    }

    /** "Deutsch", "Español (México)", "日本語": the name a speaker would look for. */
    fun nativeName(code: String): String {
        val locale = Locale.forLanguageTag(code)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}

/** Countries for a region picker, named in the device's language, as the web client names them in its own. */
internal object Regions {
    /**
     * [codes] as choices, less the [RETIRED] ones, with "All regions" (blank, the server's "no filter") first. A [current]
     * value the list lacks stays, under its name, so opening the picker never changes it. A user's region passes [defaultLabel]: there, blank
     * is "the server's region" and [ALL] is no filter, so both lead.
     */
    fun choices(
        codes: List<String>,
        current: String,
        allLabel: String,
        defaultLabel: String? = null,
    ): List<Pair<String, String>> {
        val known =
            codes
                .filter { it.isNotBlank() && it !in RETIRED }
                .distinct()
                .map { it to name(it) }
                .sortedBy { it.second.lowercase() }
        val kept = current.trim().takeIf { it.isNotEmpty() && it != ALL && known.none { (code) -> code == it } }
        val lead = if (defaultLabel == null) listOf("" to allLabel) else listOf("" to defaultLabel, ALL to allLabel)
        return lead + listOfNotNull(kept?.let { it to name(it) }) + known
    }

    /**
     * Codes TMDB's country list still carries for countries that no longer exist (#932): those ISO 3166-1 has withdrawn,
     * and TMDB's own `XC` and `XG`. The picker leaves them out. Android names several after the country that replaced
     * them, so Yugoslavia and Serbia and Montenegro read as a second and third "Serbia", with no flag. A user whose
     * setting is one of them still sees it, as [choices] keeps a current value the list lacks.
     */
    private val RETIRED =
        setOf(
            // Netherlands Antilles
            "AN",
            // Burma
            "BU",
            // Serbia and Montenegro
            "CS",
            // Soviet Union
            "SU",
            // East Timor
            "TP",
            // Czechoslovakia
            "XC",
            // East Germany
            "XG",
            // Yugoslavia
            "YU",
            // Zaire
            "ZR",
        )

    /** A user's "no filter", which both lineages keep as `all` because a blank user region means the server's. */
    const val ALL = "all"

    fun name(code: String): String =
        attempt {
            Locale
                .Builder()
                .setRegion(code)
                .build()
                .displayCountry
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
}
