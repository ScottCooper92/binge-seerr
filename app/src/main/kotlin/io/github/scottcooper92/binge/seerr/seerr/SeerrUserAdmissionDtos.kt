package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST user`: a local account. A null [password] asks the server to generate one and email it. */
@Serializable
data class SeerrCreateUserBody(
    @SerialName("email") val email: String,
    @SerialName("username") val username: String,
    @SerialName("password") val password: String?,
)

/** `GET settings/plex/users`: a plex.tv friend or home user not yet on the server; the server filters the known ones. */
@Serializable
data class SeerrPlexUserDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("thumb") val thumb: String? = null,
)

/** `GET settings/jellyfin/users`: every account on the media server, imported or not. */
@Serializable
data class SeerrJellyfinUserDto(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("thumb") val thumb: String? = null,
)

@Serializable
data class SeerrImportPlexBody(
    @SerialName("plexIds") val plexIds: List<String>,
)

@Serializable
data class SeerrImportJellyfinBody(
    @SerialName("jellyfinUserIds") val jellyfinUserIds: List<String>,
)
