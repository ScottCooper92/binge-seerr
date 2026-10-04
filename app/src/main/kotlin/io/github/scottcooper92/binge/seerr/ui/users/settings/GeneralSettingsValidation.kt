package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.R

/** The section ids of the general page, shared by its validator and the composables that tag themselves with them. */
internal object GeneralSections {
    const val PROFILE = "profile"
    const val DISCOVER = "discover"
    const val QUOTAS = "quotas"
}

/** The field ids a [GeneralSettings] issue can name. */
internal object GeneralFields {
    const val EMAIL = "email"
    const val DISCORD_ID = "discord_id"
    const val LOCALE = "locale"
    const val REGION = "region"
    const val ORIGINAL_LANGUAGE = "original_language"
    const val MOVIE_QUOTA_LIMIT = "movie_quota_limit"
    const val MOVIE_QUOTA_DAYS = "movie_quota_days"
    const val TV_QUOTA_LIMIT = "tv_quota_limit"
    const val TV_QUOTA_DAYS = "tv_quota_days"
}

internal const val GENERAL_FORM_KEY = "user_general"

/**
 * Everything standing between this draft and a save, in the order the sections read. Every field on
 * the page may be blank, so each issue is a value of the wrong shape. A field whose hint already says
 * what shape it wants shows that hint as its message. It is empty exactly when [GeneralSettings.valid]
 * is true, which `GeneralSettingsValidationTest` holds it to.
 */
internal fun GeneralSettings.issues(): List<EditorIssue> =
    buildList {
        if (!emailValid) add(invalid(GeneralSections.PROFILE, GeneralFields.EMAIL, R.string.user_settings_email_invalid))
        if (!discordIdValid) add(invalid(GeneralSections.PROFILE, GeneralFields.DISCORD_ID, R.string.user_settings_discord_id_hint))
        if (!localeValid) add(invalid(GeneralSections.DISCOVER, GeneralFields.LOCALE, R.string.user_settings_locale_hint))
        if (!regionValid) add(invalid(GeneralSections.DISCOVER, GeneralFields.REGION, R.string.user_settings_region_hint))
        if (!originalLanguageValid) {
            add(invalid(GeneralSections.DISCOVER, GeneralFields.ORIGINAL_LANGUAGE, R.string.server_settings_original_language_hint))
        }
        listOf(
            GeneralFields.MOVIE_QUOTA_LIMIT to movieQuotaLimit,
            GeneralFields.MOVIE_QUOTA_DAYS to movieQuotaDays,
            GeneralFields.TV_QUOTA_LIMIT to tvQuotaLimit,
            GeneralFields.TV_QUOTA_DAYS to tvQuotaDays,
        ).filterNot { (_, value) -> quotaValueValid(value) }
            .forEach { (field, _) -> add(invalid(GeneralSections.QUOTAS, field, R.string.editor_error_whole_number)) }
    }

/** A quota field is a whole number or blank; anything else is not a change the server would take. */
internal fun quotaValueValid(value: String): Boolean = value.isBlank() || (value.toIntOrNull() ?: -1) >= 0
