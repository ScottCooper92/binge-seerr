package com.cooper.binge.companion.seerr.discover.request

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
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
import com.cooper.binge.companion.seerr.seerr.displayString
import com.cooper.binge.companion.seerr.seerr.isAnimeSeries
import com.cooper.binge.companion.seerr.seerr.offeredServers
import com.cooper.binge.companion.seerr.seerr.toOutcome
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.ui.requests.toRequestEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** How many users the "Request for" picker lists: the first of them by name, which a small server holds whole. */
private const val USER_PICKER_PAGE = 100

/** One download server a request may go to. 4K is a property of the server, as the server itself keeps it. */
data class RequestServer(
    val id: Int,
    val name: String,
    val is4k: Boolean,
    val isDefault: Boolean,
    val profileId: Int?,
    val rootFolder: String?,
    /** What a plain request for an anime series gets, where the instance sets them. */
    val animeProfileId: Int? = null,
    val animeRootFolder: String? = null,
) {
    /** The profile a plain request gets: the anime one for an anime series where the instance has one, else the regular one. */
    fun profileFor(anime: Boolean): Int? = if (anime) animeProfileId ?: profileId else profileId

    fun folderFor(anime: Boolean): String? = if (anime) animeRootFolder ?: rootFolder else rootFolder
}

data class RequestChoice(
    val id: Int,
    val label: String,
)

/**
 * The chosen server's quality profiles, root folders and tags. [defaultProfileId] and [defaultTagIds] are what a plain request to it
 * gets, [freeSpace] maps a root folder's path to the bytes the instance reports free where it reported any.
 */
data class RequestChoices(
    val profiles: List<RequestChoice>,
    val rootFolders: List<String>,
    val tags: List<RequestChoice> = emptyList(),
    val defaultProfileId: Int? = null,
    val defaultTagIds: Set<Int> = emptySet(),
    val freeSpace: Map<String, Long> = emptyMap(),
    val animeProfileId: Int? = null,
    val animeTagIds: Set<Int> = emptySet(),
) {
    /** The default profile for an anime series where the instance sets one, else the regular one, as Seerr picks it. */
    fun defaultProfileFor(anime: Boolean): Int? = if (anime) animeProfileId ?: defaultProfileId else defaultProfileId

    /** The default tags: an anime series gets the anime tags alone, never the regular ones (an unset list is none). */
    fun defaultTagsFor(anime: Boolean): Set<Int> = if (anime) animeTagIds else defaultTagIds
}

/** How much of a user's request quota is left, for the type of title being requested. Absent where the type is not metered. */
data class RequestQuota(
    val limit: Int,
    val remaining: Int,
    val days: Int?,
) {
    val spent: Boolean get() = remaining <= 0
}

/** A user a request can be made for. */
data class RequestUser(
    val id: Int,
    val name: String,
)

/**
 * What this user may do with a request: ask for 4K, choose where it goes, and make it for someone else, which the server allows an
 * administrator, or a user with both `MANAGE_USERS` and `MANAGE_REQUESTS`. [viewerId] is the user themselves.
 */
data class RequestAccess(
    val may4k: Boolean,
    val mayChooseDestination: Boolean,
    val mayRequestForOthers: Boolean = false,
    val viewerId: Int = 0,
)

/** Where a request goes when the user picked a destination; a null field is left to the server's default. */
data class RequestDestination(
    val serverId: Int,
    val profileId: Int?,
    val rootFolder: String?,
    /** Null leaves the tags to the server; a list, even an empty one, is the user's choice. */
    val tags: List<Int>? = null,
)

data class RequestSubmission(
    val kind: TitleKind,
    val tmdbId: Int,
    val seasons: List<Int>,
    val is4k: Boolean,
    val destination: RequestDestination?,
    /** The user the request is for; null is the one sending it. */
    val userId: Int? = null,
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

    /** The users a request can be made for, in name order; empty unless [RequestAccess.mayRequestForOthers]. A failed read throws. */
    suspend fun users(): List<RequestUser> = emptyList()

    /** Whether Seerr treats this series as anime, which decides the Sonarr defaults a plain request gets. A failed read throws. */
    suspend fun isAnime(tmdbId: Int): Boolean = false

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
    /** Where a request the server just made is put, so the Requests list shows it with no refresh; null for a caller with no list. */
    private val requests: RequestStore? = null,
    private val titles: suspend (SeerrApi, String, Int) -> HydratedTitle? = { _, _, _ -> null },
) : RequestSheetRepository {
    override suspend fun access(kind: TitleKind): RequestAccess =
        withContext(io) {
            val user = viewer()
            val permissions = user.toPermissions()
            RequestAccess(
                may4k = permissions.mayRequest4k(kind == TitleKind.Series, profile().settings),
                mayChooseDestination = permissions.canRequestAdvanced,
                mayRequestForOthers = permissions.isAdmin || (permissions.canManageUsers && permissions.canManageRequests),
                viewerId = user.id,
            )
        }

    override suspend fun quota(kind: TitleKind): RequestQuota? =
        withContext(io) {
            val quota = api().userQuota(viewer().id)
            (if (kind == TitleKind.Series) quota.tv else quota.movie).toRequestQuota()
        }

    override suspend fun users(): List<RequestUser> =
        withContext(io) {
            if (!access(TitleKind.Movie).mayRequestForOthers) return@withContext emptyList()
            api().users(take = USER_PICKER_PAGE, sort = "displayname").results.map {
                RequestUser(it.id, it.displayString().orEmpty().ifEmpty { it.id.toString() })
            }
        }

    override suspend fun isAnime(tmdbId: Int): Boolean = withContext(io) { api().isAnimeSeries(tmdbId) }

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
            val defaultTags = details.server?.activeTags.orEmpty()
            val animeTags = details.server?.activeAnimeTags.orEmpty()
            RequestChoices(
                profiles = details.profiles.map { RequestChoice(it.id, it.name) },
                rootFolders = details.rootFolders.map { it.path },
                tags = details.tags.map { RequestChoice(it.id, it.label) },
                defaultProfileId = details.server?.activeProfileId,
                defaultTagIds = defaultTags.toSet(),
                freeSpace = details.rootFolders.mapNotNull { folder -> folder.freeSpace?.let { folder.path to it } }.toMap(),
                animeProfileId = details.server?.activeAnimeProfileId,
                animeTagIds = animeTags.toSet(),
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
                    tags = destination?.tags,
                    userId = submission.userId,
                )
            val key = TitleKey(if (submission.kind == TitleKind.Series) TitleType.Tv else TitleType.Movie, submission.tmdbId)
            val undo =
                statuses.applyOptimistic(key) { held ->
                    if (held.statusOf(submission.is4k).isRequestable()) held.with(submission.is4k, SeerrMediaStatusCode.Pending) else held
                }
            val outcome = attempt { api().requestMedia(body).toOutcome() }.onFailure { statuses.rollback(undo) }.getOrThrow()
            when (outcome) {
                is RequestPostOutcome.Created -> RequestResult.Created.also { addToRequests(outcome) }
                RequestPostOutcome.NothingToRequest -> RequestResult.NothingToRequest.also { statuses.rollback(undo) }
                RequestPostOutcome.AlreadyRequested -> RequestResult.AlreadyRequested.also { statuses.rollback(undo) }
            }
        }

    /** Best effort: the request exists either way, and the list catches up on its next refresh if this cannot be written. */
    private suspend fun addToRequests(created: RequestPostOutcome.Created) {
        val store = requests ?: return
        val dto = created.request ?: return
        attempt {
            dto.toRequestEntity(api(), titles, "", 0, System.currentTimeMillis())?.let { store.addCreated(it) }
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

private fun SeerrServerDto.toRequestServer() =
    RequestServer(id, name, is4k, isDefault, activeProfileId, activeDirectory, activeAnimeProfileId, activeAnimeDirectory)

/** A bucket with no limit is unlimited, which is no quota at all. */
private fun SeerrQuotaBucketDto?.toRequestQuota(): RequestQuota? {
    val limit = this?.limit?.takeIf { it > 0 } ?: return null
    return RequestQuota(limit = limit, remaining = (remaining ?: (limit - (used ?: 0))).coerceIn(0, limit), days = days)
}

/** A version with no status, or one the server reads as not requested, is one a request moves to Pending. */
private fun SeerrMediaStatusCode?.isRequestable(): Boolean =
    this == null || this == SeerrMediaStatusCode.Unknown || this == SeerrMediaStatusCode.Deleted
