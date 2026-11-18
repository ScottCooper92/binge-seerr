package com.cooper.binge.companion.seerr.discover.request

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.RequestPostOutcome
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrQuotaBucketDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrServerDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.arrServer
import com.cooper.binge.companion.seerr.seerr.offeredServers
import com.cooper.binge.companion.seerr.seerr.toOutcome
import com.cooper.binge.companion.seerr.seerr.toPermissions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** One download server a request may go to. 4K is a property of the server, as the server itself keeps it. */
data class RequestServer(
    val id: Int,
    val name: String,
    val is4k: Boolean,
    val isDefault: Boolean,
    val profileId: Int?,
    val rootFolder: String?,
)

data class RequestChoice(
    val id: Int,
    val label: String,
)

/** The chosen server's quality profiles and root folders. */
data class RequestChoices(
    val profiles: List<RequestChoice>,
    val rootFolders: List<String>,
)

/** How much of a user's request quota is left, for the type of title being requested. Absent where the type is not metered. */
data class RequestQuota(
    val limit: Int,
    val remaining: Int,
    val days: Int?,
) {
    val spent: Boolean get() = remaining <= 0
}

/**
 * What the request sheet may offer this user for this kind of title. [servers] is empty unless the user may choose a
 * destination, and lists a 4K server only to a user who may request 4K of this kind.
 */
data class RequestOptions(
    val may4k: Boolean,
    val servers: List<RequestServer>,
    val quota: RequestQuota?,
) {
    val mayChooseDestination: Boolean get() = servers.isNotEmpty()
}

/** Where a request goes when the user picked a destination; a null field is left to the server's default. */
data class RequestDestination(
    val serverId: Int,
    val profileId: Int?,
    val rootFolder: String?,
)

data class RequestSubmission(
    val kind: TitleKind,
    val tmdbId: Int,
    val seasons: List<Int>,
    val is4k: Boolean,
    val destination: RequestDestination?,
)

/** What a submit did. A refusal is not here: it is thrown as the network call throws it. */
enum class RequestResult { Created, NothingToRequest, AlreadyRequested }

/**
 * What the request sheet reads and writes. It knows nothing of Retrofit, so a view model holds this and a test holds a fake.
 */
interface RequestSheetRepository {
    suspend fun options(kind: TitleKind): RequestOptions

    /** The profiles and root folders of [serverId], one of [RequestOptions.servers]. */
    suspend fun choices(
        kind: TitleKind,
        serverId: Int,
    ): RequestChoices

    suspend fun submit(submission: RequestSubmission): RequestResult
}

/** [RequestSheetRepository] over the Seerr API, on the same server rules the exported Service applies. */
class SeerrRequestSheetRepository(
    private val api: suspend () -> SeerrApi,
    private val viewer: suspend () -> SeerrUserDto,
    private val profile: suspend () -> SeerrServerProfile,
    private val io: CoroutineDispatcher,
) : RequestSheetRepository {
    override suspend fun options(kind: TitleKind): RequestOptions =
        withContext(io) {
            val user = viewer()
            val permissions = user.toPermissions()
            val isTv = kind == TitleKind.Series
            val may4k = permissions.mayRequest4k(isTv, profile().settings)
            val api = api()
            val servers =
                if (permissions.canRequestAdvanced) {
                    api.offeredServers(isTv, allow4k = may4k).map { it.toRequestServer() }
                } else {
                    emptyList()
                }
            val quota = attempt { api.userQuota(user.id) }.getOrNull()
            RequestOptions(may4k, servers, (if (isTv) quota?.tv else quota?.movie).toRequestQuota())
        }

    override suspend fun choices(
        kind: TitleKind,
        serverId: Int,
    ): RequestChoices =
        withContext(io) {
            val details = api().arrServer(kind == TitleKind.Series, serverId)
            RequestChoices(
                profiles = details.profiles.map { RequestChoice(it.id, it.name) },
                rootFolders = details.rootFolders.map { it.path },
            )
        }

    override suspend fun submit(submission: RequestSubmission): RequestResult =
        withContext(io) {
            val destination = submission.destination
            val body =
                SeerrRequestBody(
                    mediaType = if (submission.kind == TitleKind.Series) SEERR_MEDIA_TYPE_TV else SEERR_MEDIA_TYPE_MOVIE,
                    mediaId = submission.tmdbId,
                    seasons = submission.seasons.takeIf { it.isNotEmpty() },
                    is4k = submission.is4k,
                    serverId = destination?.serverId,
                    profileId = destination?.profileId,
                    rootFolder = destination?.rootFolder,
                )
            when (api().requestMedia(body).toOutcome()) {
                is RequestPostOutcome.Created -> RequestResult.Created
                RequestPostOutcome.NothingToRequest -> RequestResult.NothingToRequest
                RequestPostOutcome.AlreadyRequested -> RequestResult.AlreadyRequested
            }
        }
}

private fun SeerrServerDto.toRequestServer() = RequestServer(id, name, is4k, isDefault, activeProfileId, activeDirectory)

/** A bucket with no limit is unlimited, which is no quota at all. */
private fun SeerrQuotaBucketDto?.toRequestQuota(): RequestQuota? {
    val limit = this?.limit?.takeIf { it > 0 } ?: return null
    return RequestQuota(limit = limit, remaining = (remaining ?: (limit - (used ?: 0))).coerceIn(0, limit), days = days)
}
