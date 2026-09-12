package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrQuotaBucketDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrQuotaDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.etaMinutes
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.seerr.toTmdbPosterUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

private const val FILTER_PROCESSING = "processing"
private const val ACTIVE_DOWNLOADS_PAGE = 20
private const val MEDIA_TYPE_MOVIE = "movie"

/**
 * The hub's reads over the saved connection. [load] is the once-per-connect overview: `auth/me`
 * judged, then the quota and the counts each best-effort, gated on the profile and the user's
 * permissions so a restricted user never fires a call the server would refuse. [activeDownloads]
 * is the poll's, hydrating each row's title from its own lookup.
 */
class HubOverviewLoader
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) {
        suspend fun server(): HubServer {
            val profile = connection.refreshProfile()
            val settings = profile.settings
            return HubServer(
                baseUrl = connection.current().baseUrl,
                title = settings.applicationTitle?.takeIf { it.isNotBlank() } ?: profile.variant.displayName,
                variant = profile.variant,
                versionLabel = profile.version?.label,
                updateAvailable = profile.updateAvailable || profile.commitsBehind > 0,
                commitsBehind = profile.commitsBehind,
            )
        }

        suspend fun load(): HubOverview {
            val api = runCatching { connection.api() }.getOrElse { return HubOverview(loaded = true, userLoad = HubUserLoad.Failed) }
            val profile = connection.profile()
            val user = runCatching { api.authenticatedUser() }
            val userDto =
                user.getOrNull()
                    ?: return HubOverview(
                        loaded = true,
                        userLoad = user.exceptionOrNull().toUserLoad(),
                        hasIssues = profile.hasIssues,
                        hasBlocklist = profile.hasBlocklist,
                    )
            val permissions = userDto.toPermissions()
            return coroutineScope {
                val quota =
                    async { runCatching { api.userQuota(userDto.id).toHubQuota() }.getOrNull() }
                val requests = async { runCatching { api.requestCount() }.getOrNull() }
                val issues =
                    async {
                        if (profile.hasIssues &&
                            profile.hasCounts &&
                            permissions.canSeeIssues
                        ) {
                            runCatching { api.issueCount().open }.getOrNull()
                        } else {
                            null
                        }
                    }
                val users =
                    async { if (permissions.canManageUsers) runCatching { api.userCountProbe().pageInfo.results }.getOrNull() else null }
                val blocklist =
                    async {
                        if (profile.hasBlocklist && permissions.canViewBlocklist) {
                            runCatching { api.blocklistCountProbe(profile.blocklistPath).pageInfo.results }.getOrNull()
                        } else {
                            null
                        }
                    }
                HubOverview(
                    loaded = true,
                    userLoad = HubUserLoad.Loaded,
                    account = userDto.toAccount(permissions.isAdmin),
                    quota = quota.await(),
                    permissions = permissions,
                    movieRequestCount = requests.await()?.movie,
                    tvRequestCount = requests.await()?.tv,
                    pendingRequestCount = requests.await()?.pending,
                    openIssueCount = issues.await(),
                    userCount = users.await(),
                    blocklistCount = blocklist.await(),
                    hasIssues = profile.hasIssues,
                    hasBlocklist = profile.hasBlocklist,
                )
            }
        }

        /** The pending request count alone, for the badge's refresh on becoming visible. */
        suspend fun pendingRequestCount(): Int? = runCatching { connection.api().requestCount().pending }.getOrNull()

        /** The first page of processing requests that are actually transferring, newest first, titled. */
        suspend fun activeDownloads(): Result<List<HubDownload>> =
            runCatching {
                val api = connection.api()
                val page = api.requests(take = ACTIVE_DOWNLOADS_PAGE, filter = FILTER_PROCESSING)
                coroutineScope {
                    page.results
                        .map { request -> async { request.toDownload(api) } }
                        .map { it.await() }
                        .filterNotNull()
                }
            }

        private suspend fun SeerrRequestDto.toDownload(api: SeerrApi): HubDownload? {
            val statuses =
                (if (is4k) media.downloadStatus4k else media.downloadStatus).ifEmpty {
                    media.downloadStatus +
                        media.downloadStatus4k
                }
            if (statuses.isEmpty()) return null
            val totalSize = statuses.sumOf { it.size ?: 0.0 }
            val totalLeft = statuses.sumOf { it.sizeLeft ?: 0.0 }
            val details =
                runCatching {
                    if (media.mediaType == MEDIA_TYPE_MOVIE) api.movieDetails(media.tmdbId) else api.tvDetails(media.tmdbId)
                }.getOrNull()
            return HubDownload(
                requestId = id,
                title = details?.displayTitle ?: statuses.firstOrNull()?.title,
                posterUrl = details?.posterPath?.toTmdbPosterUrl(),
                fraction = if (totalSize > 0.0) ((totalSize - totalLeft) / totalSize).toFloat().coerceIn(0f, 1f) else 0f,
                totalBytes = totalSize.toLong().takeIf { it > 0 },
                etaMinutes = statuses.etaMinutes(System.currentTimeMillis()),
            )
        }
    }

private fun Throwable?.toUserLoad(): HubUserLoad =
    when (this?.toSeerrError()) {
        SeerrError.Unauthorized, SeerrError.Forbidden -> HubUserLoad.Rejected
        else -> HubUserLoad.Failed
    }

private fun SeerrUserDto.toAccount(isAdmin: Boolean): HubAccount =
    HubAccount(
        id = id,
        name = listOfNotNull(displayName, username, email?.substringBefore('@')).firstOrNull { it.isNotBlank() } ?: "#$id",
        isAdmin = isAdmin,
        avatarUrl = avatar?.takeIf { it.startsWith("http") },
    )

internal fun SeerrQuotaDto.toHubQuota(): HubQuota = HubQuota(movie.toBucket(), tv.toBucket())

/** A bucket with no limit is unlimited, which is no bucket at all. */
private fun SeerrQuotaBucketDto?.toBucket(): HubQuotaBucket? {
    val limit = this?.limit?.takeIf { it > 0 } ?: return null
    return HubQuotaBucket(limit = limit, remaining = (remaining ?: (limit - (used ?: 0))).coerceIn(0, limit), days = days)
}
