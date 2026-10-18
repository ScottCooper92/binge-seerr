package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeerrStatusDto(
    @SerialName("version") val version: String? = null,
    @SerialName("commitTag") val commitTag: String? = null,
    @SerialName("updateAvailable") val updateAvailable: Boolean = false,
    @SerialName("commitsBehind") val commitsBehind: Int = 0,
)

/**
 * `GET settings/public`: the configuration the server shows before sign-in. Every field defaults,
 * because Overseerr lacks the Jellyseerr lineage's (`mediaServerType`, `mediaServerLogin`,
 * `hideRequested`) and a missing one must read as "not this server's", never as a parse failure.
 */
@Serializable
data class SeerrPublicSettings(
    @SerialName("applicationTitle") val applicationTitle: String? = null,
    @SerialName("applicationUrl") val applicationUrl: String? = null,
    @SerialName("localLogin") val localLogin: Boolean = true,
    @SerialName("mediaServerLogin") val mediaServerLogin: Boolean = true,
    @SerialName("mediaServerType") val mediaServerType: Int? = null,
    @SerialName("jellyfinServerName") val jellyfinServerName: String? = null,
    @SerialName("movie4kEnabled") val movie4kEnabled: Boolean = false,
    @SerialName("series4kEnabled") val series4kEnabled: Boolean = false,
    @SerialName("partialRequestsEnabled") val partialRequestsEnabled: Boolean = true,
    @SerialName("enableSpecialEpisodes") val enableSpecialEpisodes: Boolean = false,
    @SerialName("hideAvailable") val hideAvailable: Boolean = false,
    @SerialName("hideRequested") val hideRequested: Boolean = false,
    @SerialName("emailEnabled") val emailEnabled: Boolean = false,
    @SerialName("newPlexLogin") val newPlexLogin: Boolean = true,
    @SerialName("versionCheck") val versionCheck: Boolean = true,
    @SerialName("locale") val locale: String? = null,
    /** The server's Discover filters, which a user's blank one falls back to. Overseerr has the one `region`. */
    @SerialName("region") val region: String? = null,
    @SerialName("discoverRegion") val discoverRegion: String? = null,
    @SerialName("streamingRegion") val streamingRegion: String? = null,
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    /**
     * Despite the name, not Plex's: the server fills it from the `clientId` it generated once and keeps in its settings, so
     * it names this instance whatever address it is reached by. Overseerr 1.x does not send it.
     */
    @SerialName("plexClientIdentifier") val instanceId: String? = null,
)
