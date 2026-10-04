package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssue
import io.github.scottcooper92.binge.seerr.ui.users.settings.invalid
import io.github.scottcooper92.binge.seerr.ui.users.settings.missing

/** The section ids of the instance form, shared by its validator and the composables that tag themselves with them. */
internal object DvrSections {
    const val CONNECTION = "connection"
    const val DESTINATION = "destination"
    const val ANIME = "anime"
    const val ADVANCED = "advanced"
}

/** The field ids a [DvrForm] issue can name. */
internal object DvrFields {
    const val NAME = "name"
    const val HOST = "host"
    const val PORT = "port"
    const val API_KEY = "api_key"
    const val PROFILE = "profile"
    const val ROOT_FOLDER = "root_folder"
    const val EXTERNAL_URL = "external_url"
}

internal const val DVR_FORM_KEY = "dvr_instance"

/**
 * Everything standing between this draft and a save, in the order the sections read. It is empty
 * exactly when [DvrForm.valid] is true, which `DvrValidationTest` holds it to.
 *
 * Before a test has answered, a missing destination is one issue on the section rather than two on
 * pickers that are not there: the user's next step is to test, not to pick.
 */
internal fun DvrForm.issues(choicesLoaded: Boolean): List<EditorIssue> =
    buildList {
        if (name.isBlank()) add(missing(DvrSections.CONNECTION, DvrFields.NAME))
        if (host.isBlank()) add(missing(DvrSections.CONNECTION, DvrFields.HOST))
        when {
            port.isBlank() -> add(missing(DvrSections.CONNECTION, DvrFields.PORT))
            !portValid(port) -> add(invalid(DvrSections.CONNECTION, DvrFields.PORT, R.string.editor_error_port))
        }
        if (apiKey.isBlank()) add(missing(DvrSections.CONNECTION, DvrFields.API_KEY))
        addAll(destinationIssues(choicesLoaded))
        if (!externalUrlValid) add(invalid(DvrSections.ADVANCED, DvrFields.EXTERNAL_URL, R.string.editor_error_web_url))
    }

private fun DvrForm.destinationIssues(choicesLoaded: Boolean): List<EditorIssue> {
    val noProfile = profileId == null
    val noFolder = rootFolder.isNullOrBlank()
    return when {
        !noProfile && !noFolder -> emptyList()
        !choicesLoaded -> listOf(missing(DvrSections.DESTINATION, DvrSections.DESTINATION, R.string.server_settings_dvr_untested))
        else ->
            listOfNotNull(
                missing(DvrSections.DESTINATION, DvrFields.PROFILE).takeIf { noProfile },
                missing(DvrSections.DESTINATION, DvrFields.ROOT_FOLDER).takeIf { noFolder },
            )
    }
}
