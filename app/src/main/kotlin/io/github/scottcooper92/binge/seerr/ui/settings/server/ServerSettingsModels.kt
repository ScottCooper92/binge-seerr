package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsUpdateBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrPublicSettings
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl

/** The server's own settings pages, for a user who manages settings. */
enum class ServerSettingsPage { General, DefaultPermissions, MediaServer, Tautulli, Services }

/**
 * The main settings form. A null field is one this server's lineage does not have, and the form
 * leaves it out: Overseerr has one region and the proxy switches; the Jellyseerr lineage split the
 * region in two and added the streaming, hide-requested, special-episode and YouTube ones.
 */
data class ServerGeneralSettings(
    val applicationTitle: String = "",
    val applicationUrl: String = "",
    val locale: String = "",
    val discoverRegion: String = "",
    val streamingRegion: String? = null,
    val originalLanguage: String = "",
    val hideAvailable: Boolean = false,
    val hideRequested: Boolean? = null,
    val partialRequests: Boolean = true,
    val specialEpisodes: Boolean? = null,
    val versionCheck: Boolean? = null,
    val cacheImages: Boolean = false,
    val youtubeUrl: String? = null,
    val trustProxy: Boolean? = null,
    val csrfProtection: Boolean? = null,
) {
    /** A blank URL clears it; anything else has to be a web address the server can serve links from. */
    val urlValid: Boolean get() = applicationUrl.isBlank() || applicationUrl.trim().isWebUrl()
}

/** The server's API key beside the form: shown only on request, and replaceable. */
data class ApiKeyState(
    val key: String = "",
    val revealed: Boolean = false,
    val regenerating: Boolean = false,
)

/** What `settings/public` tells a visitor before they sign in — read-only beside the form. */
data class VisitorView(
    val localLogin: Boolean,
    val mediaServerLogin: Boolean,
    val movie4k: Boolean,
    val series4k: Boolean,
    val partialRequests: Boolean,
    val hideAvailable: Boolean,
)

/** What the general page shows beside its form; both wait on the same load. */
data class ServerGeneralExtras(
    val apiKey: ApiKeyState = ApiKeyState(),
    val visitor: VisitorView? = null,
)

private val SeerrVariant.isJellyseerrLineage: Boolean
    get() = this == SeerrVariant.Jellyseerr || this == SeerrVariant.Seerr

internal fun SeerrMainSettingsDto.toServerGeneral(variant: SeerrVariant): ServerGeneralSettings {
    val lineage = variant.isJellyseerrLineage
    val overseerr = variant == SeerrVariant.Overseerr
    return ServerGeneralSettings(
        applicationTitle = applicationTitle.orEmpty(),
        applicationUrl = applicationUrl.orEmpty(),
        locale = locale.orEmpty(),
        discoverRegion = (discoverRegion ?: region).orEmpty(),
        streamingRegion = streamingRegion.orEmpty().takeIf { lineage },
        originalLanguage = originalLanguage.orEmpty(),
        hideAvailable = hideAvailable ?: false,
        hideRequested = (hideRequested ?: false).takeIf { lineage },
        partialRequests = partialRequestsEnabled ?: true,
        specialEpisodes = (enableSpecialEpisodes ?: false).takeIf { lineage },
        versionCheck = versionCheck,
        cacheImages = cacheImages ?: false,
        youtubeUrl = youtubeUrl.orEmpty().takeIf { lineage },
        trustProxy = (trustProxy ?: false).takeIf { overseerr },
        csrfProtection = (csrfProtection ?: false).takeIf { overseerr },
    )
}

/**
 * Only what the form holds; a field the lineage lacks stays null and is left out. The region goes
 * under both names, since Jellyseerr 1.x still read `region` before the split.
 */
internal fun ServerGeneralSettings.toBody(): SeerrMainSettingsUpdateBody =
    SeerrMainSettingsUpdateBody(
        applicationTitle = applicationTitle.trim(),
        applicationUrl = applicationUrl.trim().trimEnd('/'),
        locale = locale.trim(),
        region = discoverRegion.trim(),
        discoverRegion = discoverRegion.trim().takeIf { streamingRegion != null },
        streamingRegion = streamingRegion?.trim(),
        originalLanguage = originalLanguage.trim(),
        hideAvailable = hideAvailable,
        hideRequested = hideRequested,
        partialRequestsEnabled = partialRequests,
        enableSpecialEpisodes = specialEpisodes,
        versionCheck = versionCheck,
        cacheImages = cacheImages,
        youtubeUrl = youtubeUrl?.trim(),
        trustProxy = trustProxy,
        csrfProtection = csrfProtection,
    )

internal fun SeerrPublicSettings.toVisitorView(): VisitorView =
    VisitorView(
        localLogin = localLogin,
        mediaServerLogin = mediaServerLogin,
        movie4k = movie4kEnabled,
        series4k = series4kEnabled,
        partialRequests = partialRequestsEnabled,
        hideAvailable = hideAvailable,
    )
