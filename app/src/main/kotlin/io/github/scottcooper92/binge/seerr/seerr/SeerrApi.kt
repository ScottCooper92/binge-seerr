package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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

    /** One user's full profile: a user's own, or any with `MANAGE_USERS`. */
    @GET("api/v1/user/{userId}")
    suspend fun user(
        @Path("userId") userId: Int,
    ): SeerrUserDto

    @GET("api/v1/user/{userId}/requests")
    suspend fun userRequests(
        @Path("userId") userId: Int,
        @Query("take") take: Int,
        @Query("skip") skip: Int = 0,
    ): SeerrRequestsPageDto

    /** Tautulli's plays; the server answers 404 where Tautulli is not configured. `ADMIN`. */
    @GET("api/v1/user/{userId}/watch_data")
    suspend fun userWatchData(
        @Path("userId") userId: Int,
    ): SeerrUserWatchDataDto

    /** The media server's watchlist for the user: their own, or `WATCHLIST_VIEW` for another's. */
    @GET("api/v1/user/{userId}/watchlist")
    suspend fun userWatchlist(
        @Path("userId") userId: Int,
        @Query("page") page: Int = 1,
    ): SeerrWatchlistPageDto

    @DELETE("api/v1/user/{userId}")
    suspend fun deleteUser(
        @Path("userId") userId: Int,
    )

    @GET("api/v1/user/{userId}/settings/main")
    suspend fun userMainSettings(
        @Path("userId") userId: Int,
    ): SeerrUserMainSettingsDto

    @POST("api/v1/user/{userId}/settings/main")
    suspend fun updateUserMainSettings(
        @Path("userId") userId: Int,
        @Body body: SeerrUserMainSettingsDto,
    ): SeerrUserMainSettingsDto

    @GET("api/v1/user/{userId}/settings/password")
    suspend fun userPasswordInfo(
        @Path("userId") userId: Int,
    ): SeerrUserPasswordInfoDto

    @POST("api/v1/user/{userId}/settings/password")
    suspend fun updateUserPassword(
        @Path("userId") userId: Int,
        @Body body: SeerrUserPasswordBody,
    )

    @GET("api/v1/user/{userId}/settings/notifications")
    suspend fun userNotificationSettings(
        @Path("userId") userId: Int,
    ): SeerrUserNotificationSettingsDto

    @POST("api/v1/user/{userId}/settings/notifications")
    suspend fun updateUserNotificationSettings(
        @Path("userId") userId: Int,
        @Body body: SeerrUserNotificationSettingsDto,
    ): SeerrUserNotificationSettingsDto

    @GET("api/v1/user/{userId}/settings/permissions")
    suspend fun userPermissions(
        @Path("userId") userId: Int,
    ): SeerrUserPermissionsDto

    @POST("api/v1/user/{userId}/settings/permissions")
    suspend fun updateUserPermissions(
        @Path("userId") userId: Int,
        @Body body: SeerrUserPermissionsBody,
    ): SeerrUserPermissionsDto

    /** Jellyseerr 2.4+: links a plex.tv account, by the token the PIN flow minted. */
    @POST("api/v1/user/{userId}/settings/linked-accounts/plex")
    suspend fun linkPlexAccount(
        @Path("userId") userId: Int,
        @Body body: SeerrLinkPlexBody,
    )

    @DELETE("api/v1/user/{userId}/settings/linked-accounts/plex")
    suspend fun unlinkPlexAccount(
        @Path("userId") userId: Int,
    )

    /** Jellyseerr 2.4+: links a Jellyfin or Emby account by its credentials. */
    @POST("api/v1/user/{userId}/settings/linked-accounts/jellyfin")
    suspend fun linkJellyfinAccount(
        @Path("userId") userId: Int,
        @Body body: SeerrLinkJellyfinBody,
    )

    /** Seerr 3.4+: links the Jellyfin account that approved a Quick Connect code. */
    @POST("api/v1/user/{userId}/settings/linked-accounts/jellyfin/quickconnect")
    suspend fun linkJellyfinQuickConnect(
        @Path("userId") userId: Int,
        @Body body: SeerrLinkQuickConnectBody,
    )

    @DELETE("api/v1/user/{userId}/settings/linked-accounts/jellyfin")
    suspend fun unlinkJellyfinAccount(
        @Path("userId") userId: Int,
    )

    /** Creates a local account with the server's default permissions. 409 when the email is taken. `MANAGE_USERS`. */
    @POST("api/v1/user")
    suspend fun createUser(
        @Body body: SeerrCreateUserBody,
    ): SeerrUserDto

    /** The media server's accounts to offer for import. `MANAGE_USERS`. */
    @GET("api/v1/settings/plex/users")
    suspend fun plexUsers(): List<SeerrPlexUserDto>

    @GET("api/v1/settings/jellyfin/users")
    suspend fun jellyfinUsers(): List<SeerrJellyfinUserDto>

    /**
     * Imports the listed accounts; one already known is refreshed rather than duplicated. The
     * Jellyseerr lineage answers `{createdUsers, refreshedUsers}`, Overseerr the created list alone.
     */
    @POST("api/v1/user/import-from-plex")
    suspend fun importFromPlex(
        @Body body: SeerrImportPlexBody,
    ): JsonElement

    @POST("api/v1/user/import-from-jellyfin")
    suspend fun importFromJellyfin(
        @Body body: SeerrImportJellyfinBody,
    ): List<SeerrUserDto>

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

    /** Merges the body over the server's main settings and answers with the whole record. */
    @POST("api/v1/settings/main")
    suspend fun updateMainSettings(
        @Body body: SeerrMainSettingsUpdateBody,
    ): SeerrMainSettingsDto

    /** Replaces the server's API key; the old one stops working at once. Answers with the whole record. */
    @POST("api/v1/settings/main/regenerate")
    suspend fun regenerateApiKey(): SeerrMainSettingsDto

    @GET("api/v1/settings/plex")
    suspend fun plexSettings(): SeerrPlexSettingsDto

    /** Reaches the Plex server with the body before saving it; a server that does not answer is a 500. */
    @POST("api/v1/settings/plex")
    suspend fun updatePlexSettings(
        @Body body: SeerrPlexSettingsBody,
    ): SeerrPlexSettingsDto

    /** The admin's own Plex servers from plex.tv, each connection already tested by the server. */
    @GET("api/v1/settings/plex/devices/servers")
    suspend fun plexServers(): List<SeerrPlexDeviceDto>

    @GET("api/v1/settings/jellyfin")
    suspend fun jellyfinSettings(): SeerrJellyfinSettingsDto

    @POST("api/v1/settings/jellyfin")
    suspend fun updateJellyfinSettings(
        @Body body: SeerrJellyfinSettingsBody,
    ): SeerrJellyfinSettingsDto

    /**
     * The libraries of [server] (`plex` or `jellyfin`). On a released server [enable] is how they are
     * turned on — the comma-joined ids of every enabled one — and [sync] re-reads them from the
     * media server first; `develop` moved both to [setLibraryEnabled] and [syncLibraries].
     */
    @GET("api/v1/settings/{server}/library")
    suspend fun mediaLibraries(
        @Path("server") server: String,
        @Query("enable") enable: String? = null,
        @Query("sync") sync: Boolean? = null,
    ): List<SeerrLibraryDto>

    /** `develop` only; a released server answers 404 and [mediaLibraries] with `enable` is the way. */
    @PUT("api/v1/settings/{server}/library/{libraryId}")
    suspend fun setLibraryEnabled(
        @Path("server") server: String,
        @Path("libraryId") libraryId: String,
        @Body body: SeerrLibraryEnabledBody,
    ): SeerrLibraryDto

    /** `develop` only; a released server answers 404 and [mediaLibraries] with `sync` is the way. */
    @POST("api/v1/settings/{server}/library/sync")
    suspend fun syncLibraries(
        @Path("server") server: String,
    ): List<SeerrLibraryDto>

    @GET("api/v1/settings/{server}/sync")
    suspend fun scanStatus(
        @Path("server") server: String,
    ): SeerrScanStatusDto

    @POST("api/v1/settings/{server}/sync")
    suspend fun scan(
        @Path("server") server: String,
        @Body body: SeerrScanCommandBody,
    ): SeerrScanStatusDto

    @GET("api/v1/settings/tautulli")
    suspend fun tautulliSettings(): SeerrTautulliSettingsDto

    /** Reaches Tautulli with the body before saving it; one below 2.9 or unreachable is a 500. */
    @POST("api/v1/settings/tautulli")
    suspend fun updateTautulliSettings(
        @Body body: SeerrTautulliSettingsDto,
    ): SeerrTautulliSettingsDto

    @GET("api/v1/settings/about")
    suspend fun about(): SeerrAboutDto

    @GET("api/v1/settings/jobs")
    suspend fun jobs(): List<SeerrJobDto>

    /** Starts the job now; the answer is the job with `running` set. */
    @POST("api/v1/settings/jobs/{jobId}/run")
    suspend fun runJob(
        @Path("jobId") jobId: String,
    ): SeerrJobDto

    @POST("api/v1/settings/jobs/{jobId}/cancel")
    suspend fun cancelJob(
        @Path("jobId") jobId: String,
    ): SeerrJobDto

    /** Changes when the job runs, Overseerr 1.27 and the Jellyseerr lineage from 1.1. */
    @POST("api/v1/settings/jobs/{jobId}/schedule")
    suspend fun scheduleJob(
        @Path("jobId") jobId: String,
        @Body body: SeerrJobScheduleBody,
    ): SeerrJobDto

    @GET("api/v1/settings/cache")
    suspend fun caches(): SeerrCacheDto

    /** The server log, newest first; [filter] is the lowest level shown, [search] a substring of the message or label. */
    @GET("api/v1/settings/logs")
    suspend fun logs(
        @Query("take") take: Int,
        @Query("skip") skip: Int,
        @Query("filter") filter: String,
        @Query("search") search: String? = null,
    ): JsonElement

    @POST("api/v1/settings/cache/{cacheId}/flush")
    suspend fun flushCache(
        @Path("cacheId") cacheId: String,
    ): Response<Unit>

    /** Drops one DNS cache entry by hostname, Seerr 3.0+. */
    @POST("api/v1/settings/cache/dns/{dnsEntry}/flush")
    suspend fun flushDnsEntry(
        @Path("dnsEntry") dnsEntry: String,
    ): Response<Unit>

    @GET("api/v1/settings/radarr")
    suspend fun radarrSettings(): List<SeerrServiceSettingsDto>

    @GET("api/v1/settings/sonarr")
    suspend fun sonarrSettings(): List<SeerrServiceSettingsDto>

    /** Reaches an instance and answers with what it offers; [service] is `radarr` or `sonarr`. */
    @POST("api/v1/settings/{service}/test")
    suspend fun testDvr(
        @Path("service") service: String,
        @Body body: SeerrDvrTestBody,
    ): SeerrDvrTestResultDto

    /** Adds an instance; the server assigns the id and, for a default, clears the previous default of the same 4K kind. */
    @POST("api/v1/settings/{service}")
    suspend fun createDvr(
        @Path("service") service: String,
        @Body body: SeerrServiceSettingsDto,
    ): SeerrServiceSettingsDto

    @PUT("api/v1/settings/{service}/{id}")
    suspend fun updateDvr(
        @Path("service") service: String,
        @Path("id") id: Int,
        @Body body: SeerrServiceSettingsDto,
    ): SeerrServiceSettingsDto

    @DELETE("api/v1/settings/{service}/{id}")
    suspend fun deleteDvr(
        @Path("service") service: String,
        @Path("id") id: Int,
    ): SeerrServiceSettingsDto

    /** The override rules, Jellyseerr 2.2+. */
    @GET("api/v1/overrideRule")
    suspend fun overrideRules(): List<SeerrOverrideRuleDto>

    @POST("api/v1/overrideRule")
    suspend fun createOverrideRule(
        @Body body: SeerrOverrideRuleDto,
    ): SeerrOverrideRuleDto

    @PUT("api/v1/overrideRule/{ruleId}")
    suspend fun updateOverrideRule(
        @Path("ruleId") ruleId: Int,
        @Body body: SeerrOverrideRuleDto,
    ): SeerrOverrideRuleDto

    @DELETE("api/v1/overrideRule/{ruleId}")
    suspend fun deleteOverrideRule(
        @Path("ruleId") ruleId: Int,
    ): SeerrOverrideRuleDto

    /** The network settings, Jellyseerr 2.4+. */
    @GET("api/v1/settings/network")
    suspend fun networkSettings(): SeerrNetworkSettingsDto

    @POST("api/v1/settings/network")
    suspend fun updateNetworkSettings(
        @Body body: SeerrNetworkSettingsDto,
    ): SeerrNetworkSettingsDto

    /** The metadata providers, Seerr 3.0+. */
    @GET("api/v1/settings/metadatas")
    suspend fun metadataSettings(): SeerrMetadataSettingsDto

    @PUT("api/v1/settings/metadatas")
    suspend fun updateMetadataSettings(
        @Body body: SeerrMetadataSettingsDto,
    ): SeerrMetadataSettingsDto

    /** Reaches the chosen providers; a failure is a status code, success a message. */
    @POST("api/v1/settings/metadatas/test")
    suspend fun testMetadataProviders(
        @Body body: SeerrMetadataTestBody,
    ): SeerrMessageDto

    /** The discover sliders in the order the web client shows them, Overseerr 1.32 and the Jellyseerr lineage from 1.4. */
    @GET("api/v1/settings/discover")
    suspend fun discoverSliders(): List<SeerrDiscoverSliderDto>

    /** Replaces the whole list: the order, and each slider's enabled state. */
    @POST("api/v1/settings/discover")
    suspend fun updateDiscoverSliders(
        @Body body: List<SeerrDiscoverSliderDto>,
    ): List<SeerrDiscoverSliderDto>

    @POST("api/v1/settings/discover/add")
    suspend fun addDiscoverSlider(
        @Body body: SeerrDiscoverSliderBody,
    ): SeerrDiscoverSliderDto

    @PUT("api/v1/settings/discover/{sliderId}")
    suspend fun updateDiscoverSlider(
        @Path("sliderId") sliderId: Int,
        @Body body: SeerrDiscoverSliderBody,
    ): SeerrDiscoverSliderDto

    @DELETE("api/v1/settings/discover/{sliderId}")
    suspend fun deleteDiscoverSlider(
        @Path("sliderId") sliderId: Int,
    ): SeerrDiscoverSliderDto

    /** Puts the built-in sliders back in their default order and drops the custom ones. */
    @GET("api/v1/settings/discover/reset")
    suspend fun resetDiscoverSliders(): Response<Unit>

    /** One notification agent's settings; [agent] is the server's segment (`email`, `discord`, …). */
    @GET("api/v1/settings/notifications/{agent}")
    suspend fun notificationAgent(
        @Path("agent") agent: String,
    ): SeerrNotificationAgentDto

    @POST("api/v1/settings/notifications/{agent}")
    suspend fun updateNotificationAgent(
        @Path("agent") agent: String,
        @Body body: SeerrNotificationAgentDto,
    ): SeerrNotificationAgentDto

    /** Sends a test notification through the settings in [body], saved or not; a failure is a status code. */
    @POST("api/v1/settings/notifications/{agent}/test")
    suspend fun testNotificationAgent(
        @Path("agent") agent: String,
        @Body body: SeerrNotificationAgentDto,
    ): Response<Unit>

    /** The sounds a Pushover application offers, Overseerr 1.34 and Jellyseerr 1.8. */
    @GET("api/v1/settings/notifications/pushover/sounds")
    suspend fun pushoverSounds(
        @Query("token") token: String,
    ): List<SeerrPushoverSoundDto>

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

    /**
     * The blocklist, newest first; `VIEW_BLOCKLIST` or `MANAGE_BLOCKLIST`. [filter] is `manual` or
     * `blocklistedTags` on Seerr 3.x; a server without it ignores the parameter and lists everything.
     */
    @GET("api/v1/{path}")
    suspend fun blocklist(
        @Path("path") path: String,
        @Query("take") take: Int,
        @Query("skip") skip: Int = 0,
        @Query("filter") filter: String? = null,
        @Query("search") search: String? = null,
    ): SeerrBlocklistPageDto

    /** Keyed by TMDB id, not the entry's own; `MANAGE_BLOCKLIST`. */
    @DELETE("api/v1/{path}/{tmdbId}")
    suspend fun removeFromBlocklist(
        @Path("path") path: String,
        @Path("tmdbId") tmdbId: Int,
    )

    /** Seerr 3.2+: blocks or unblocks every movie of a TMDB collection at once; `MANAGE_BLOCKLIST`. */
    @POST("api/v1/blocklist/collection/{collectionId}")
    suspend fun blockCollection(
        @Path("collectionId") collectionId: Int,
    )

    @DELETE("api/v1/blocklist/collection/{collectionId}")
    suspend fun unblockCollection(
        @Path("collectionId") collectionId: Int,
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
