package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The slice of Seerr's `/api/v1` this companion needs: who we are, what a title's state is, and
 * the writes the REQUEST contract maps onto. A 2xx from `auth/me` confirms both reachability and
 * that the credentials are accepted; the `movie`/`tv` lookups (keyed by TMDB id) carry `mediaInfo`
 * only when the server already tracks the title.
 */
interface SeerrApi {
    @GET("api/v1/auth/me")
    suspend fun authenticatedUser(): SeerrUserDto

    /** Logs in with a Jellyfin/Emby account; the response sets the `connect.sid` session cookie. */
    @POST("api/v1/auth/jellyfin")
    suspend fun logInWithJellyfin(
        @Body body: SeerrJellyfinLoginBody,
    ): SeerrUserDto

    /** Logs in with a local (email + password) account; sets the `connect.sid` cookie. */
    @POST("api/v1/auth/local")
    suspend fun logInWithLocal(
        @Body body: SeerrLocalLoginBody,
    ): SeerrUserDto

    /** Logs in with a plex.tv token from the PIN flow; sets the `connect.sid` cookie. */
    @POST("api/v1/auth/plex")
    suspend fun logInWithPlex(
        @Body body: SeerrPlexLoginBody,
    ): SeerrUserDto

    /** Seerr 3.4+, Jellyfin only: a code the user approves on their Jellyfin, and the secret this app polls with. */
    @POST("api/v1/auth/jellyfin/quickconnect/initiate")
    suspend fun initiateQuickConnect(): SeerrQuickConnectDto

    /** A 404 means the code expired unapproved. */
    @GET("api/v1/auth/jellyfin/quickconnect/check")
    suspend fun checkQuickConnect(
        @Query("secret") secret: String,
    ): SeerrQuickConnectCheckDto

    /** Once approved: signs in as the approving Jellyfin user and sets the `connect.sid` cookie. */
    @POST("api/v1/auth/jellyfin/quickconnect/authenticate")
    suspend fun authenticateQuickConnect(
        @Body body: SeerrQuickConnectSecretBody,
    ): SeerrUserDto

    /** Ends the server-side session behind the `connect.sid` cookie. */
    @POST("api/v1/auth/logout")
    suspend fun logOut()

    /** Unauthenticated: emails a reset link if the address belongs to a local account; answers 200 either way. */
    @POST("api/v1/auth/reset-password")
    suspend fun requestPasswordReset(
        @Body body: SeerrPasswordResetBody,
    )

    /** Unauthenticated: TMDB backdrop paths of this week's trending titles, the sign-in page's artwork. */
    @GET("api/v1/backdrops")
    suspend fun backdrops(): List<String>

    /** Unauthenticated: the server's version, which is how its fork is told apart, and its update state. */
    @GET("api/v1/status")
    suspend fun status(): SeerrStatusDto

    /** Unauthenticated: what the administrator turned on, as the sign-in page reads it. */
    @GET("api/v1/settings/public")
    suspend fun publicSettings(): SeerrPublicSettings

    @GET("api/v1/movie/{tmdbId}")
    suspend fun movieDetails(
        @Path("tmdbId") tmdbId: Int,
    ): SeerrMediaDetailsDto

    @GET("api/v1/tv/{tmdbId}")
    suspend fun tvDetails(
        @Path("tmdbId") tmdbId: Int,
    ): SeerrMediaDetailsDto

    /**
     * The raw [Response], because the STATUS CODE carries meaning the body cannot: a 202 means the
     * server took the call and created nothing, since every season was already covered. It is a
     * 2xx, so nothing throws, and every field of [SeerrRequestResultDto] is optional, so the 202's
     * `{"message": ...}` body parses cleanly into an empty result.
     */
    @POST("api/v1/request")
    suspend fun requestMedia(
        @Body body: SeerrRequestBody,
    ): Response<SeerrRequestResultDto>

    /** The configured download servers of one kind; Seerr keeps 4K on separate instances. */
    @GET("api/v1/service/radarr")
    suspend fun radarrServers(): List<SeerrServerDto>

    @GET("api/v1/service/sonarr")
    suspend fun sonarrServers(): List<SeerrServerDto>

    /** One server's quality profiles and root folders — the advanced-options picker's choices. */
    @GET("api/v1/service/radarr/{serverId}")
    suspend fun radarrServer(
        @Path("serverId") serverId: Int,
    ): SeerrServerDetailsDto

    @GET("api/v1/service/sonarr/{serverId}")
    suspend fun sonarrServer(
        @Path("serverId") serverId: Int,
    ): SeerrServerDetailsDto

    /** A page of requests; [filter] is one of Seerr's own (`all`, `pending`, `processing`, `available`, `failed`). */
    @GET("api/v1/request")
    suspend fun requests(
        @Query("take") take: Int,
        @Query("skip") skip: Int = 0,
        @Query("filter") filter: String = "all",
        @Query("sort") sort: String = "added",
        @Query("requestedBy") requestedBy: Int? = null,
    ): SeerrRequestsPageDto

    @GET("api/v1/request/{requestId}")
    suspend fun request(
        @Path("requestId") requestId: Int,
    ): SeerrRequestDto

    @GET("api/v1/request/count")
    suspend fun requestCount(): SeerrRequestCountDto

    /** Only where [SeerrServerProfile.hasCounts]: Overseerr grew it at 1.30. */
    @GET("api/v1/issue/count")
    suspend fun issueCount(): SeerrIssueCountDto

    /** A page of issues; [filter] is `all`, `open` or `resolved`, [sort] `added` or `modified`. */
    @GET("api/v1/issue")
    suspend fun issues(
        @Query("take") take: Int,
        @Query("skip") skip: Int = 0,
        @Query("filter") filter: String = "all",
        @Query("sort") sort: String = "added",
        @Query("requestedBy") requestedBy: Int? = null,
    ): SeerrIssuePageDto

    /** A user's own quota, or any user's with `MANAGE_USERS`. */
    @GET("api/v1/user/{userId}/quota")
    suspend fun userQuota(
        @Path("userId") userId: Int,
    ): SeerrQuotaDto

    /** `MANAGE_USERS` only; read with `take=1` for the total on its `pageInfo`. */
    @GET("api/v1/user")
    suspend fun userCountProbe(
        @Query("take") take: Int = 1,
    ): SeerrCountProbeDto

    /** A page of users; [sort] is `created`, `updated`, `requests` or `displayname`. `MANAGE_USERS`. */
    @GET("api/v1/user")
    suspend fun users(
        @Query("take") take: Int,
        @Query("skip") skip: Int = 0,
        @Query("sort") sort: String = "created",
    ): SeerrUserPageDto

    /** Replaces the permission bitmask of every user in [body], the web client's bulk edit. */
    @PUT("api/v1/user")
    suspend fun bulkUpdateUsers(
        @Body body: SeerrBulkUsersBody,
    ): List<SeerrUserDto>

    /** [path] is [SeerrServerProfile.blocklistPath]; needs `VIEW_BLOCKLIST` or `MANAGE_BLOCKLIST`. */
    @GET("api/v1/{path}")
    suspend fun blocklistCountProbe(
        @Path("path") path: String,
        @Query("take") take: Int = 1,
    ): SeerrCountProbeDto

    /** The admin-only settings reads; `ADMIN`, or `MANAGE_SETTINGS` on the Jellyseerr lineage. */
    @GET("api/v1/settings/main")
    suspend fun mainSettings(): SeerrMainSettingsDto

    @GET("api/v1/settings/about")
    suspend fun about(): SeerrAboutDto

    @GET("api/v1/settings/jobs")
    suspend fun jobs(): List<SeerrJobDto>

    @GET("api/v1/settings/radarr")
    suspend fun radarrSettings(): List<SeerrServiceSettingsDto>

    @GET("api/v1/settings/sonarr")
    suspend fun sonarrSettings(): List<SeerrServiceSettingsDto>

    @GET("api/v1/settings/notifications/email")
    suspend fun emailAgent(): SeerrNotificationAgentDto

    @GET("api/v1/settings/notifications/discord")
    suspend fun discordAgent(): SeerrNotificationAgentDto

    /** Re-targets a request: the seasons of a show, and the destination for one not yet sent to the client. */
    @PUT("api/v1/request/{requestId}")
    suspend fun editRequest(
        @Path("requestId") requestId: Int,
        @Body body: SeerrEditRequestBody,
    ): SeerrRequestDto

    @DELETE("api/v1/request/{requestId}")
    suspend fun deleteRequest(
        @Path("requestId") requestId: Int,
    )

    @POST("api/v1/request/{requestId}/approve")
    suspend fun approveRequest(
        @Path("requestId") requestId: Int,
    )

    @POST("api/v1/request/{requestId}/decline")
    suspend fun declineRequest(
        @Path("requestId") requestId: Int,
    )

    @POST("api/v1/request/{requestId}/retry")
    suspend fun retryRequest(
        @Path("requestId") requestId: Int,
    )

    /** Marks the media record; [status] is one of [SeerrMediaStatusChoice]'s paths, per instance. */
    @POST("api/v1/media/{mediaId}/{status}")
    suspend fun setMediaStatus(
        @Path("mediaId") mediaId: Int,
        @Path("status") status: String,
        @Query("is4k") is4k: Boolean,
    )

    /** Clears the server's record of a title, and every request for it with it. */
    @DELETE("api/v1/media/{mediaId}")
    suspend fun deleteMedia(
        @Path("mediaId") mediaId: Int,
    )

    /** Deletes the downloaded files from Radarr or Sonarr; Jellyseerr 1.5 and later. */
    @DELETE("api/v1/media/{mediaId}/file")
    suspend fun deleteMediaFiles(
        @Path("mediaId") mediaId: Int,
        @Query("is4k") is4k: Boolean,
    )

    /** Tautulli play counts; the server answers 404 where Tautulli is not configured. */
    @GET("api/v1/media/{mediaId}/watch_data")
    suspend fun watchData(
        @Path("mediaId") mediaId: Int,
    ): SeerrWatchDataDto

    @GET("api/v1/issue/{issueId}")
    suspend fun issue(
        @Path("issueId") issueId: Int,
    ): SeerrIssueDto

    /** [status] is `open` or `resolved`: the server's own route shape. */
    @POST("api/v1/issue/{issueId}/{status}")
    suspend fun setIssueStatus(
        @Path("issueId") issueId: Int,
        @Path("status") status: String,
    )

    @DELETE("api/v1/issue/{issueId}")
    suspend fun deleteIssue(
        @Path("issueId") issueId: Int,
    )

    /** Answers with the updated issue, whose newest comment is the one just posted. */
    @POST("api/v1/issue/{issueId}/comment")
    suspend fun commentOnIssue(
        @Path("issueId") issueId: Int,
        @Body body: SeerrIssueCommentBody,
    ): SeerrIssueDto

    @PUT("api/v1/issueComment/{commentId}")
    suspend fun editIssueComment(
        @Path("commentId") commentId: Int,
        @Body body: SeerrIssueCommentBody,
    )

    @DELETE("api/v1/issueComment/{commentId}")
    suspend fun deleteIssueComment(
        @Path("commentId") commentId: Int,
    )

    @POST("api/v1/issue")
    suspend fun createIssue(
        @Body body: SeerrCreateIssueBody,
    )

    /** [path] is [SeerrServerProfile.blocklistPath]: `blacklist` on Jellyseerr 2.x, `blocklist` from Seerr 3.0. */
    @POST("api/v1/{path}")
    suspend fun addToBlocklist(
        @Path("path") path: String,
        @Body body: SeerrAddToBlocklistBody,
    )
}

/**
 * Seerr's `MediaStatus` enum, a plain int on the wire. Typed so it cannot be confused with
 * [SeerrRequestStatusCode], which shares the same raw range.
 */
@JvmInline
@Serializable
value class SeerrMediaStatusCode(
    val raw: Int,
) {
    companion object {
        val Unknown = SeerrMediaStatusCode(1)
        val Pending = SeerrMediaStatusCode(2)
        val Processing = SeerrMediaStatusCode(3)
        val PartiallyAvailable = SeerrMediaStatusCode(4)
        val Available = SeerrMediaStatusCode(5)
        val Blocklisted = SeerrMediaStatusCode(6)
    }
}

/**
 * Seerr's `MediaRequestStatus` enum. It runs past Declined: an auto-approved request that finishes
 * downloading lands on Completed (5), and a fulfilment that errors lands on Failed (4).
 */
@JvmInline
@Serializable
value class SeerrRequestStatusCode(
    val raw: Int,
) {
    companion object {
        val Pending = SeerrRequestStatusCode(1)
        val Approved = SeerrRequestStatusCode(2)
        val Declined = SeerrRequestStatusCode(3)
        val Failed = SeerrRequestStatusCode(4)
        val Completed = SeerrRequestStatusCode(5)
    }
}

/** Seerr's `IssueType` enum: 1 video, 2 audio, 3 subtitles, 4 other. */
@JvmInline
@Serializable
value class SeerrIssueTypeCode(
    val raw: Int,
) {
    companion object {
        val Video = SeerrIssueTypeCode(1)
        val Audio = SeerrIssueTypeCode(2)
        val Subtitles = SeerrIssueTypeCode(3)
        val Other = SeerrIssueTypeCode(4)
    }
}

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

@Serializable
data class SeerrJellyfinLoginBody(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
)

@Serializable
data class SeerrLocalLoginBody(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
)

@Serializable
data class SeerrPlexLoginBody(
    @SerialName("authToken") val authToken: String,
)

@Serializable
data class SeerrQuickConnectDto(
    @SerialName("code") val code: String,
    @SerialName("secret") val secret: String,
)

@Serializable
data class SeerrQuickConnectCheckDto(
    @SerialName("authenticated") val authenticated: Boolean = false,
)

@Serializable
data class SeerrQuickConnectSecretBody(
    @SerialName("secret") val secret: String,
)

@Serializable
data class SeerrPasswordResetBody(
    @SerialName("email") val email: String,
)

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
    @SerialName("jellyfinExternalHost") val jellyfinExternalHost: String? = null,
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
)

/**
 * A media request. The advanced-option overrides are omitted when absent, so a plain request posts
 * exactly as one and the server applies its per-instance defaults.
 */
@Serializable
data class SeerrRequestBody(
    @SerialName("mediaType") val mediaType: String,
    @SerialName("mediaId") val mediaId: Int,
    @SerialName("seasons") val seasons: List<Int>? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
)

/** `PUT request/{id}`: only the fields sent change; the seasons list is a show's whole new set. */
@Serializable
data class SeerrEditRequestBody(
    @SerialName("mediaType") val mediaType: String,
    @SerialName("seasons") val seasons: List<Int>? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
    @SerialName("tags") val tags: List<Int>? = null,
)

/** A Radarr or Sonarr instance as Seerr lists it; the `active*` fields are what a plain request gets. */
@Serializable
data class SeerrServerDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
)

@Serializable
data class SeerrServerDetailsDto(
    /** The instance itself, as far as its name; every field optional so an older shape still parses. */
    @SerialName("server") val server: SeerrServerRefDto? = null,
    @SerialName("profiles") val profiles: List<SeerrProfileDto> = emptyList(),
    @SerialName("rootFolders") val rootFolders: List<SeerrRootFolderDto> = emptyList(),
    @SerialName("tags") val tags: List<SeerrTagDto> = emptyList(),
)

@Serializable
data class SeerrServerRefDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class SeerrProfileDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
)

@Serializable
data class SeerrRootFolderDto(
    @SerialName("id") val id: Int,
    @SerialName("path") val path: String,
)

@Serializable
data class SeerrRequestResultDto(
    @SerialName("id") val id: Int? = null,
)

/**
 * Body for `POST issue`. [mediaId] is the server's INTERNAL media id (a title's `mediaInfo.id`),
 * not the TMDB id — the server can only attach an issue to a title it already tracks.
 */
@Serializable
data class SeerrIssueCommentBody(
    @SerialName("message") val message: String,
)

@Serializable
data class SeerrCreateIssueBody(
    @SerialName("mediaId") val mediaId: Int,
    @SerialName("issueType") val issueType: SeerrIssueTypeCode,
    @SerialName("message") val message: String,
)

@Serializable
data class SeerrAddToBlocklistBody(
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("title") val title: String,
)

/**
 * A title lookup; [mediaInfo] is absent when the title is not known to the server. A movie carries
 * [title] and [releaseDate], a show [name] and [firstAirDate]; [posterPath] is a TMDB path.
 */
@Serializable
data class SeerrMediaDetailsDto(
    @SerialName("mediaInfo") val mediaInfo: SeerrMediaInfoDto? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    /** A show's seasons as TMDB lists them; specials are season 0. */
    @SerialName("seasons") val seasons: List<SeerrSeasonDto> = emptyList(),
) {
    val displayTitle: String? get() = title ?: name

    val year: String? get() = (releaseDate ?: firstAirDate)?.take(YEAR_LENGTH)?.takeIf { it.length == YEAR_LENGTH }
}

/** A `yyyy-mm-dd` date's year; a serializable class cannot hide this in a private companion. */
private const val YEAR_LENGTH = 4

@Serializable
data class SeerrMediaInfoDto(
    /** The server's internal media id — the issue endpoint keys on this, not the TMDB id. */
    @SerialName("id") val id: Int? = null,
    @SerialName("status") val status: SeerrMediaStatusCode? = null,
    @SerialName("seasons") val seasons: List<SeerrSeasonStatusDto> = emptyList(),
    @SerialName("downloadStatus") val downloadStatus: List<SeerrDownloadStatusDto> = emptyList(),
    @SerialName("requests") val requests: List<SeerrRequestSummaryDto> = emptyList(),
    /** Link to the title in the media server's web UI; [mediaUrl] is general, the others server-specific. */
    @SerialName("mediaUrl") val mediaUrl: String? = null,
    @SerialName("jellyfinMediaUrl") val jellyfinMediaUrl: String? = null,
    @SerialName("plexUrl") val plexUrl: String? = null,
)

@Serializable
data class SeerrRequestSummaryDto(
    @SerialName("id") val id: Int,
    @SerialName("status") val status: SeerrRequestStatusCode? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("requestedBy") val requestedBy: SeerrRequestUserDto? = null,
    /** The seasons this request covers; empty for movie requests. */
    @SerialName("seasons") val seasons: List<SeerrSeasonStatusDto> = emptyList(),
)

/** Whichever of these the server filled in names the requester. */
@Serializable
data class SeerrRequestUserDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    /** The author's bitmask, present on a comment's user; what an Admin tag on a comment reads. */
    @SerialName("permissions") val permissions: Int? = null,
)

@Serializable
data class SeerrSeasonDto(
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("episodeCount") val episodeCount: Int = 0,
    @SerialName("airDate") val airDate: String? = null,
)

@Serializable
data class SeerrSeasonStatusDto(
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("status") val status: SeerrMediaStatusCode? = null,
)

/** A single in-flight download from the *arr backend. Sizes are bytes, read as Double to tolerate either form. */
@Serializable
data class SeerrDownloadStatusDto(
    @SerialName("title") val title: String? = null,
    @SerialName("size") val size: Double? = null,
    @SerialName("sizeLeft") val sizeLeft: Double? = null,
    /** ISO timestamp the backend expects the download to finish. */
    @SerialName("estimatedCompletionTime") val estimatedCompletionTime: String? = null,
    /** Remaining time as the *arr queue reports it: `hh:mm:ss`, or `d.hh:mm:ss` beyond a day. */
    @SerialName("timeLeft") val timeLeft: String? = null,
    /** The *arr queue status, e.g. `downloading`, `queued`, `delay`, `paused`; absent on older servers. */
    @SerialName("status") val status: String? = null,
)
