package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssue
import io.github.scottcooper92.binge.seerr.ui.users.settings.invalid

/** The section ids of the server's General page, shared by its validator and the composables that tag themselves with them. */
internal object ServerGeneralSections {
    const val APPLICATION = "application"
    const val DISCOVER = "discover"
    const val REQUESTS = "requests"
    const val SERVER = "server"
    const val NEW_USERS = "new_users"
    const val API_KEY = "api_key"
}

/** The field ids a [ServerGeneralSettings] issue can name. */
internal object ServerGeneralFields {
    const val APPLICATION_URL = "application_url"
    const val LOCALE = "locale"
    const val DISCOVER_REGION = "discover_region"
    const val STREAMING_REGION = "streaming_region"
    const val ORIGINAL_LANGUAGE = "original_language"
}

internal const val SERVER_GENERAL_FORM_KEY = "server_general"

/**
 * Everything standing between this draft and a save, in the order the sections read. Every field may
 * be blank, so each issue is a value of the wrong shape; a field whose hint already says what shape it
 * wants shows that hint as its message. It is empty exactly when [ServerGeneralSettings.valid] is
 * true, which `ServerGeneralValidationTest` holds it to.
 */
internal fun ServerGeneralSettings.issues(): List<EditorIssue> =
    listOfNotNull(
        invalid(ServerGeneralSections.APPLICATION, ServerGeneralFields.APPLICATION_URL, R.string.editor_error_web_url)
            .takeIf { !urlValid },
        invalid(ServerGeneralSections.DISCOVER, ServerGeneralFields.LOCALE, R.string.server_settings_locale_hint)
            .takeIf { !localeValid },
        invalid(ServerGeneralSections.DISCOVER, ServerGeneralFields.DISCOVER_REGION, R.string.server_settings_region_hint)
            .takeIf { !discoverRegionValid },
        invalid(ServerGeneralSections.DISCOVER, ServerGeneralFields.STREAMING_REGION, R.string.server_settings_region_hint)
            .takeIf { !streamingRegionValid },
        invalid(ServerGeneralSections.DISCOVER, ServerGeneralFields.ORIGINAL_LANGUAGE, R.string.server_settings_original_language_hint)
            .takeIf { !originalLanguageValid },
    )
