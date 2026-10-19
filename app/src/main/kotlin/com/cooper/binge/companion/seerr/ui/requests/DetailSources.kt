package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrMediaDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestSummaryDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchDataDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchStatsDto
import com.cooper.binge.companion.seerr.seerr.details
import com.cooper.binge.companion.seerr.seerr.displayString
import com.cooper.binge.companion.seerr.seerr.downloadFraction
import com.cooper.binge.companion.seerr.seerr.etaMinutes
import com.cooper.binge.companion.seerr.seerr.isWebUrl
import com.cooper.binge.companion.seerr.seerr.phoneWebRoot
import com.cooper.binge.companion.seerr.seerr.toAvatarUrl
import com.cooper.binge.companion.seerr.seerr.toEpochMillisOrNull
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.cooper.binge.companion.seerr.ui.mediaServerItem
import java.util.Locale

private fun SeerrRequestSummaryDto.toSummary(): RequestSummary =
    RequestSummary(
        id = id,
        status = status,
        requestedBy = requestedBy?.displayString(),
        requestedByAvatarUrl = requestedBy?.avatar.toAvatarUrl(),
        requestedAtMillis = createdAt?.toEpochMillisOrNull(),
        is4k = is4k,
        seasonNumbers = seasons.map { it.seasonNumber },
    )

private fun SeerrWatchStatsDto.toWatchStats(): WatchStats =
    WatchStats(
        playCount = playCount,
        playCount7Days = playCount7Days,
        playCount30Days = playCount30Days,
        users = users.mapNotNull { user -> listOfNotNull(user.displayName, user.username).firstOrNull { it.isNotBlank() } },
    )

/**
 * The server's answers for one request page, before they are shaped into a [RequestDetail]. Split
 * from the fetch so the page's rules read as rules rather than as one long constructor call.
 */
internal class DetailSources(
    val api: SeerrApi,
    val dto: SeerrRequestDto,
    val details: SeerrMediaDetailsDto?,
    val profile: SeerrServerProfile,
    val user: SeerrUserDto?,
    val permissions: SeerrPermissions,
    val destination: RequestDestination?,
    val watch: SeerrWatchDataDto?,
    val webRoot: String,
) {
    val scope: ModerationScope
        get() = ModerationScope(permissions, currentUserId = user?.id, hasBlocklist = profile.hasBlocklist)

    /** A request the server has not answered yet, which is what makes it editable at all. */
    val pending: Boolean get() = dto.status == null || dto.status == SeerrRequestStatusCode.Pending

    val canEditDestination: Boolean get() = pending && permissions.canRequestAdvanced

    /** With partial requests off the server takes a whole show, so a show's seasons are not the editor's to change. */
    val seasonsEditable: Boolean
        get() = dto.media.mediaType != SEERR_MEDIA_TYPE_TV || profile.settings.partialRequestsEnabled

    /** A movie has no seasons, so its destination is all there is to edit: the editor would otherwise open empty. */
    private val hasSeasonsToEdit: Boolean
        get() = dto.media.mediaType == SEERR_MEDIA_TYPE_TV && seasonsEditable

    suspend fun toDetail(): RequestDetail {
        val hydrated =
            details?.let {
                HydratedTitle(
                    it.displayTitle,
                    it.posterPath?.toTmdbPosterUrl(),
                    it.year,
                    it.backdropPath?.toTmdbBackdropUrl(),
                    it.overview,
                    it.certification(Locale.getDefault().country),
                )
            }
        val item =
            checkNotNull(dto.toRequestItem(api, { _, _, _ -> hydrated }, System.currentTimeMillis())) {
                "Unrenderable media type"
            }
        val own = dto.requestedBy?.id != null && dto.requestedBy.id == user?.id
        return RequestDetail(
            item = item,
            actions = item.actions(scope),
            canEdit = pending && (permissions.canManageRequests || own) && (hasSeasonsToEdit || canEditDestination),
            canEditDestination = canEditDestination,
            backdropUrl = details?.backdropPath?.toTmdbBackdropUrl(),
            overview = details?.overview?.takeIf { it.isNotBlank() },
            modifiedBy = dto.modifiedBy?.displayString(),
            modifiedByAvatarUrl = dto.modifiedBy?.avatar.toAvatarUrl(),
            modifiedById = dto.modifiedBy?.id,
            viewerId = user?.id,
            canManageUsers = permissions.canManageUsers,
            updatedAtMillis = dto.updatedAt?.toEpochMillisOrNull(),
            seasons = seasons(),
            destination = destination,
            downloads = downloads(),
            mediaId = dto.media.id,
            canReportIssue = profile.hasIssues && permissions.canReportIssues && dto.media.id != null,
            webUrl = webRoot + dto.media.mediaType + "/" + dto.media.tmdbId,
            phoneUrl = profile.phoneWebRoot(webRoot) + dto.media.mediaType + "/" + dto.media.tmdbId,
            serverInstanceId = profile.instanceId,
            mediaServerUrl = preferred(dto.media.mediaUrl, dto.media.mediaUrl4k),
            serviceUrl = preferred(dto.media.serviceUrl, dto.media.serviceUrl4k),
            media = dto.mediaRecord(permissions, profile, watch),
            serverName = profile.variant.displayName,
            mediaServerName = profile.mediaServerName(),
            mediaServerItem = profile.mediaServerItem(dto.media.jellyfinMediaId),
            siblings =
                details
                    ?.mediaInfo
                    ?.requests
                    .orEmpty()
                    .filter { it.id != dto.id }
                    .map { it.toSummary() },
        )
    }

    /** The season the request asked for, named from the title's own list where that loaded. */
    private fun seasons(): List<SeasonState> =
        dto.seasons.map { requested ->
            val season = details?.seasons?.firstOrNull { it.seasonNumber == requested.seasonNumber }
            SeasonState(requested.seasonNumber, season?.name, season?.episodeCount, requested.status)
        }

    private fun downloads(): List<DetailDownload> {
        val statuses = if (dto.is4k) dto.media.downloadStatus4k else dto.media.downloadStatus
        val now = System.currentTimeMillis()
        return statuses.map { status ->
            DetailDownload(
                title = status.title,
                fraction = listOf(status).downloadFraction(),
                totalBytes = status.size?.toLong()?.takeIf { it > 0 },
                etaMinutes = listOf(status).etaMinutes(now),
            )
        }
    }

    /** A 4K request prefers the 4K link and falls back to the standard one; anything else takes the standard. */
    private fun preferred(
        standard: String?,
        fourK: String?,
    ): String? = (if (dto.is4k) fourK ?: standard else standard)?.takeIf { it.isWebUrl() }
}

/** The 4K instance is listed only where the server holds one, or the request itself is 4K. */
private fun SeerrRequestDto.mediaRecord(
    permissions: SeerrPermissions,
    profile: SeerrServerProfile,
    watch: SeerrWatchDataDto?,
): MediaRecord? {
    val mediaId = media.id ?: return null
    val has4k = is4k || (media.status4k != null && media.status4k != SeerrMediaStatusCode.Unknown)
    return MediaRecord(
        mediaId = mediaId,
        isTv = media.mediaType != SEERR_MEDIA_TYPE_MOVIE,
        instances =
            listOfNotNull(
                MediaInstance(
                    false,
                    media.status,
                    media.serviceUrl?.takeIf { it.isWebUrl() },
                    media.mediaUrl?.takeIf { it.isWebUrl() },
                    watch?.data?.toWatchStats(),
                ),
                MediaInstance(
                    true,
                    media.status4k,
                    media.serviceUrl4k?.takeIf { it.isWebUrl() },
                    media.mediaUrl4k?.takeIf { it.isWebUrl() },
                    watch?.data4k?.toWatchStats(),
                ).takeIf { has4k },
            ),
        canSetStatus = permissions.canManageRequests,
        canClearData = permissions.canManageRequests,
        canDeleteFiles = permissions.canManageRequests && profile.hasDeleteMediaFiles,
    )
}
