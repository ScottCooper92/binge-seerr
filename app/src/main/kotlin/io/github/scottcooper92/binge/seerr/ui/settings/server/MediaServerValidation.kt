package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssue
import io.github.scottcooper92.binge.seerr.ui.users.settings.invalid
import io.github.scottcooper92.binge.seerr.ui.users.settings.missing

/** The section ids of the media-server page, shared by its validator and the composables that tag themselves with them. */
internal object MediaServerSections {
    const val CONNECTION = "connection"
    const val LINKS = "links"
    const val LIBRARIES = "libraries"
    const val SCAN = "scan"
    const val TAUTULLI = "tautulli"
}

/** The field ids a [MediaServerForm] issue can name. */
internal object MediaServerFields {
    const val HOST = "host"
    const val PORT = "port"
    const val EXTERNAL_URL = "external_url"
    const val FORGOT_PASSWORD_URL = "forgot_password_url"
}

internal const val MEDIA_SERVER_FORM_KEY = "media_server"

/**
 * Everything standing between this draft and a save, in the order the sections read: a host and a
 * port in range, and the two links in the shape the server takes. Plex's web app URL may end in a
 * slash; the other links may not, and their message says so. It is empty exactly when
 * [MediaServerForm.valid] is true, which `MediaServerValidationTest` holds it to.
 */
internal fun MediaServerForm.issues(): List<EditorIssue> =
    buildList {
        if (host.isBlank()) add(missing(MediaServerSections.CONNECTION, MediaServerFields.HOST))
        when {
            port.isBlank() -> add(missing(MediaServerSections.CONNECTION, MediaServerFields.PORT))
            !hostAndPortValid("x", port) -> add(invalid(MediaServerSections.CONNECTION, MediaServerFields.PORT, R.string.editor_error_port))
        }
        if (!externalUrlValid) {
            val message = if (kind == MediaServerKind.Plex) R.string.editor_error_web_url else R.string.editor_error_web_url_no_slash
            add(invalid(MediaServerSections.LINKS, MediaServerFields.EXTERNAL_URL, message))
        }
        if (!forgotPasswordUrlValid) {
            add(invalid(MediaServerSections.LINKS, MediaServerFields.FORGOT_PASSWORD_URL, R.string.editor_error_web_url_no_slash))
        }
    }
