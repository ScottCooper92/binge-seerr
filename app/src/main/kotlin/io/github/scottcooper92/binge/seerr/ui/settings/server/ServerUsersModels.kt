package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultQuotaDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultQuotasDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsUpdateBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer

/**
 * The web client's Users settings: how people sign in, and the request limits everyone gets unless their own are set.
 * A limit of 0 is unlimited, as it is on the server; its window is kept even then, so turning a limit back on restores it.
 */
data class ServerUsersSettings(
    val localLogin: Boolean = true,
    /** Null on Overseerr, which can't turn its media server sign-in off. */
    val mediaServerLogin: Boolean? = null,
    val newMediaServerLogin: Boolean = true,
    val movieLimit: Int = 0,
    val movieDays: Int = DEFAULT_LIMIT_DAYS,
    val tvLimit: Int = 0,
    val tvDays: Int = DEFAULT_LIMIT_DAYS,
) {
    /** The server refuses to turn every way in off. */
    val valid: Boolean get() = localLogin || mediaServerLogin != false
}

/** What the page shows beside the form: the media server, for the sign-in labels, and the default permissions. */
data class ServerUsersExtras(
    val mediaServer: SeerrMediaServer = SeerrMediaServer.Unknown,
    val defaultPermissions: Set<ManageablePermission> = emptySet(),
)

internal fun SeerrMainSettingsDto.toServerUsers(): ServerUsersSettings =
    ServerUsersSettings(
        localLogin = localLogin ?: true,
        mediaServerLogin = mediaServerLogin,
        newMediaServerLogin = newPlexLogin ?: true,
        movieLimit = defaultQuotas?.movie?.quotaLimit?.coerceAtLeast(0) ?: 0,
        movieDays = defaultQuotas?.movie?.quotaDays?.takeIf { it > 0 } ?: DEFAULT_LIMIT_DAYS,
        tvLimit = defaultQuotas?.tv?.quotaLimit?.coerceAtLeast(0) ?: 0,
        tvDays = defaultQuotas?.tv?.quotaDays?.takeIf { it > 0 } ?: DEFAULT_LIMIT_DAYS,
    )

/** Only this page's fields: the server merges a write over what it holds, so General's are left alone. */
internal fun ServerUsersSettings.toBody(): SeerrMainSettingsUpdateBody =
    SeerrMainSettingsUpdateBody(
        localLogin = localLogin,
        mediaServerLogin = mediaServerLogin,
        newPlexLogin = newMediaServerLogin,
        defaultQuotas =
            SeerrDefaultQuotasDto(
                movie = SeerrDefaultQuotaDto(quotaLimit = movieLimit, quotaDays = movieDays),
                tv = SeerrDefaultQuotaDto(quotaLimit = tvLimit, quotaDays = tvDays),
            ),
    )

/** The window the web client starts a limit with. */
internal const val DEFAULT_LIMIT_DAYS = 7

/** The web client's limit and window pickers both run from 1 to 100. */
internal const val MAX_LIMIT_CHOICE = 100
