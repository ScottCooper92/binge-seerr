package io.github.scottcooper92.binge.seerr.ui

private val LOCALE_SHAPE = Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*")
private val REGION_SHAPE = Regex("[A-Za-z]{2}")
private val LANGUAGE_SHAPE = Regex("[A-Za-z]{2,3}")
private val LANGUAGE_LIST_SEPARATOR = Regex("[|,]")

/**
 * The shape rules for the language and region fields on the server and user general pages. Shape only:
 * whether the server supports a code stays its call. A blank value is never checked here; blank means
 * "use the default" and each form allows it itself.
 */
internal object LanguageCodeShapes {
    /** A BCP 47 style locale: `en`, `fr-FR`, `zh-Hant`. */
    fun isLocale(value: String): Boolean = LOCALE_SHAPE.matches(value.trim())

    /** An ISO 3166 country code: exactly two letters. */
    fun isRegion(value: String): Boolean = REGION_SHAPE.matches(value.trim())

    /** One or more language codes, joined by `|` or `,` (TMDB's "any of"): `en`, `en|fr`. */
    fun isOriginalLanguage(value: String): Boolean = value.trim().split(LANGUAGE_LIST_SEPARATOR).all { LANGUAGE_SHAPE.matches(it) }
}
