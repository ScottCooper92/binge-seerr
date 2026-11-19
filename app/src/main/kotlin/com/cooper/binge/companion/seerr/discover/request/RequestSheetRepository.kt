package com.cooper.binge.companion.seerr.discover.request

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.RequestPostOutcome
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
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

/** What this user may do with a request: ask for 4K, and choose where it goes. */
data class RequestAccess(
    val may4k: Boolean,
    val mayChooseDestination: Boolean,
)

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
enum class RequestResult { Created, NothingToRequest, AlreadyRequested, PartiallyCreated }

/**
 * What sending several requests did: how many the server made, how many it already had, and the first refusal, which is
 * null when every one went through. A later request is still sent after an earlier one is refused.
 */
data class RequestAllResult(
    val created: Int,
    val alreadyThere: Int,
    val failure: Throwable?,
)

/**
 * What the request sheet reads and writes. It knows nothing of Retrofit, so a view model holds this and a test holds a fake.
 * Each read stands alone, so one failing cannot take the others with it.
 */
interface RequestSheetRepository {
    suspend fun access(kind: TitleKind): RequestAccess

    /** The quota for this kind of title; null where it is not metered. A failed read throws. */
    suspend fun quota(kind: TitleKind): RequestQuota?

    /** The servers this user may choose between, empty unless they may choose a destination. A failed read throws. */
    suspend fun servers(kind: TitleKind): List<RequestServer>

    /** The profiles and root folders of [serverId], one of [servers]. */
    suspend fun choices(
        kind: TitleKind,
        serverId: Int,
    ): RequestChoices

    suspend fun submit(submission: RequestSubmission): RequestResult

    /** One request per submission, in order. A refusal is reported in the result, not thrown. */
    suspend fun submitAll(submissions: List<RequestSubmission>): RequestAllResult
}

/** [RequestSheetRepository] over the Seerr API, on the same server rules the exported Service applies. */
class SeerrRequestSheetRepository(
    private val api: suspend () -> SeerrApi,
    private val viewer: suspend () -> SeerrUserDto,
    private val profile: suspend () -> SeerrServerProfile,
    private val io: CoroutineDispatcher,
    private val statuses: TitleStatusStore = TitleStatusStore(),
) : RequestSheetRepository {
    override suspend fun access(kind: TitleKind): RequestAccess =
        withContext(io) {
            val permissions = viewer().toPermissions()
            RequestAccess(
                may4k = permissions.mayRequest4k(kind == TitleKind.Series, profile().settings),
                mayChooseDestination = permissions.canRequestAdvanced,
            )
        }

    override suspend fun quota(kind: TitleKind): RequestQuota? =
        withContext(io) {
            val quota = api().userQuota(viewer().id)
            (if (kind == TitleKind.Series) quota.tv else quota.movie).toRequestQuota()
        }

    override suspend fun servers(kind: TitleKind): List<RequestServer> =
        withContext(io) {
            val access = access(kind)
            if (!access.mayChooseDestination) return@withContext emptyList()
            api().offeredServers(kind == TitleKind.Series, allow4k = access.may4k).map { it.toRequestServer() }
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
            val key = TitleKey(if (submission.kind == TitleKind.Series) TitleType.Tv else TitleType.Movie, submission.tmdbId)
            val undo =
                statuses.applyOptimistic(key) { held ->
                    if (held.statusOf(submission.is4k).isRequestable()) held.with(submission.is4k, SeerrMediaStatusCode.Pending) else held
                }
            val outcome = attempt { api().requestMedia(body).toOutcome() }.onFailure { statuses.rollback(undo) }.getOrThrow()
            when (outcome) {
                is RequestPostOutcome.Created -> RequestResult.Created
                RequestPostOutcome.NothingToRequest -> RequestResult.NothingToRequest.also { statuses.rollback(undo) }
                RequestPostOutcome.AlreadyRequested -> RequestResult.AlreadyRequested.also { statuses.rollback(undo) }
            }
        }

    override suspend fun submitAll(submissions: List<RequestSubmission>): RequestAllResult {
        var created = 0
        var already = 0
        var failure: Throwable? = null
        for (submission in submissions) {
            attempt { submit(submission) }.fold(
                onSuccess = { if (it == RequestResult.Created) created++ else already++ },
                onFailure = { if (failure == null) failure = it },
            )
        }
        return RequestAllResult(created, already, failure)
    }
}

private fun SeerrServerDto.toRequestServer() = RequestServer(id, name, is4k, isDefault, activeProfileId, activeDirectory)

/** A bucket with no limit is unlimited, which is no quota at all. */
private fun SeerrQuotaBucketDto?.toRequestQuota(): RequestQuota? {
    val limit = this?.limit?.takeIf { it > 0 } ?: return null
    return RequestQuota(limit = limit, remaining = (remaining ?: (limit - (used ?: 0))).coerceIn(0, limit), days = days)
}

/** A version with no status, or one the server reads as not requested, is one a request moves to Pending. */
private fun SeerrMediaStatusCode?.isRequestable(): Boolean =
    this == null || this == SeerrMediaStatusCode.Unknown || this == SeerrMediaStatusCode.Deleted
