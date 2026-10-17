package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A user as `auth/me`, the user list and `user/{id}` serve one: the login names per account
 * kind, the bitmask, and the [userType] that says which server the account is on.
 */
@Serializable
data class SeerrUserDto(
    @SerialName("id") val id: Int,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("plexUsername") val plexUsername: String? = null,
    @SerialName("jellyfinUsername") val jellyfinUsername: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("requestCount") val requestCount: Int? = null,
    /** The user's permission bitmask, which is what the handshake's capability set is decoded from. */
    @SerialName("permissions") val permissions: Int? = null,
    /** Seerr's `UserType`: 1 Plex, 2 local, 3 Jellyfin, 4 Emby. */
    @SerialName("userType") val userType: Int? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    /** The linked accounts, where the server has them: a plex.tv id, a Jellyfin or Emby user id. */
    @SerialName("plexId") val plexId: Int? = null,
    @SerialName("jellyfinUserId") val jellyfinUserId: String? = null,
)

@Serializable
data class SeerrUserPageDto(
    @SerialName("pageInfo") val pageInfo: SeerrPageInfoDto = SeerrPageInfoDto(),
    @SerialName("results") val results: List<SeerrUserDto> = emptyList(),
)

/** `PUT user`: one bitmask written to every listed user. */
@Serializable
data class SeerrBulkUsersBody(
    @SerialName("ids") val ids: List<Int>,
    @SerialName("permissions") val permissions: Int,
)

/** `user/{id}/watch_data`: Tautulli's plays for one user, and what they watched last. */
@Serializable
data class SeerrUserWatchDataDto(
    @SerialName("playCount") val playCount: Int? = null,
    @SerialName("recentlyWatched") val recentlyWatched: List<SeerrWatchedMediaDto> = emptyList(),
)

@Serializable
data class SeerrWatchedMediaDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
)

/** `user/{id}/watchlist`: the media server's watchlist, paged by page number rather than `skip`. */
@Serializable
data class SeerrWatchlistPageDto(
    @SerialName("page") val page: Int = 1,
    @SerialName("totalPages") val totalPages: Int = 1,
    @SerialName("results") val results: List<SeerrWatchlistItemDto> = emptyList(),
)

@Serializable
data class SeerrWatchlistItemDto(
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("title") val title: String? = null,
)
