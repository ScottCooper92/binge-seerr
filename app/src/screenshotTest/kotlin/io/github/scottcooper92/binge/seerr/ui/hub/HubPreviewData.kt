package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant

/**
 * The hub's sample state, living in the frame source set rather than beside the screen: nothing
 * outside a frame builds a [HubUiState] by hand, so this ships in no APK and is on no other
 * module's classpath.
 *
 * Every poster and avatar is null on purpose. That is what the design system's own catalog samples
 * do, and it is the only image state a renderer with no network can reach twice running.
 */
internal fun previewServer() =
    HubServer(
        baseUrl = "https://seerr.example.org",
        title = "Front Room Seerr",
        variant = SeerrVariant.Seerr,
        versionLabel = "3.1.0",
        updateAvailable = true,
        commitsBehind = 4,
    )

/**
 * An administrator: every manage row visible, every stat answered, and one metered quota beside one
 * unlimited one so both arms of the quota meter render in the canonical frame.
 */
internal fun previewAdminOverview() =
    HubOverview(
        loaded = true,
        userLoad = HubUserLoad.Loaded,
        account = HubAccount(id = 1, name = "Ada Lovelace", isAdmin = true, avatarUrl = null),
        quota = HubQuota(movie = HubQuotaBucket(limit = 10, remaining = 3, days = 7), tv = null),
        permissions =
            SeerrPermissions(
                isAdmin = true,
                canRequest = true,
                canManageRequests = true,
                canViewRequests = true,
                canManageUsers = true,
                canManageSettings = true,
                canManageIssues = true,
                canViewIssues = true,
                canManageBlocklist = true,
                canViewBlocklist = true,
            ),
        movieRequestCount = 128,
        tvRequestCount = 47,
        pendingRequestCount = 6,
        openIssueCount = 2,
        userCount = 9,
        blocklistCount = 3,
        hasIssues = true,
        hasBlocklist = true,
    )

/**
 * A user who may only request. The server has issues and a blocklist, so what collapses the Manage
 * group to its one row is the permission gate rather than the lineage — and an unanswered stat
 * falls back to the placeholder, which nothing else frames.
 */
internal fun previewRestrictedOverview() =
    HubOverview(
        loaded = true,
        userLoad = HubUserLoad.Loaded,
        account = HubAccount(id = 7, name = "Grace", isAdmin = false, avatarUrl = null),
        quota = null,
        permissions = SeerrPermissions(canRequest = true),
        movieRequestCount = 12,
        tvRequestCount = 4,
        pendingRequestCount = 1,
        userCount = null,
        hasIssues = true,
        hasBlocklist = true,
    )

/** Two cards: one with both meta halves, one with neither and no title, which is the fallback copy. */
internal fun previewDownloads() =
    listOf(
        HubDownload(
            requestId = 1,
            title = "The Expanse",
            posterUrl = null,
            fraction = 0.42f,
            totalBytes = 4_600_000_000L,
            etaMinutes = 18,
        ),
        HubDownload(requestId = 2, title = null, posterUrl = null, fraction = 0.08f, totalBytes = null, etaMinutes = null),
    )

internal fun previewActions() =
    HubActions(
        onOpenSection = {},
        onOpenAccount = {},
        onRetry = {},
        onReconnect = {},
        onDisconnect = {},
    )

internal fun previewReady(
    health: ConnectionHealth = ConnectionHealth.Healthy,
    overview: HubOverview = previewAdminOverview(),
    downloading: List<HubDownload> = previewDownloads(),
) = HubUiState.Ready(server = previewServer(), health = health, overview = overview, downloading = downloading)

/**
 * A user the server meters on both types, with movies spent.
 *
 * One exhausted bucket beside a partly-used one puts both halves of the "how many left?" answer in
 * a single frame — including the exhausted copy, which no other fixture reaches.
 */
internal fun previewLimitedOverview() =
    previewRestrictedOverview().copy(
        account = HubAccount(id = 7, name = "Grace Hopper", isAdmin = false, avatarUrl = null),
        quota =
            HubQuota(
                movie = HubQuotaBucket(limit = 5, remaining = 0, days = 7),
                tv = HubQuotaBucket(limit = 8, remaining = 5, days = 7),
            ),
    )

/** A user the server meters on neither type: both buckets absent, which is what unlimited is. */
internal fun previewUnlimitedOverview() =
    previewRestrictedOverview().copy(
        account = HubAccount(id = 8, name = "Katherine Johnson", isAdmin = false, avatarUrl = null),
        quota = HubQuota(movie = null, tv = null),
    )
