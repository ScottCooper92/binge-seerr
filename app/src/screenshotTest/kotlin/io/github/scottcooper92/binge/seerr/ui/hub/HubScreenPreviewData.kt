package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant

/**
 * Sample data for the hub's frames.
 *
 * Every poster and avatar is null on purpose: an image would be fetched at render time and a frame
 * has no network, so a URL here would bake whatever the loader falls back to into the baseline.
 */
internal val previewServer =
    HubServer(
        baseUrl = "https://seerr.example",
        title = "Family",
        variant = SeerrVariant.Seerr,
        versionLabel = "3.4.0",
        updateAvailable = true,
        commitsBehind = 12,
    )

internal val adminPermissions =
    SeerrPermissions(
        isAdmin = true,
        canRequest = true,
        canManageRequests = true,
        canViewRequests = true,
        canManageBlocklist = true,
        canCreateIssues = true,
        canManageIssues = true,
        canViewIssues = true,
        canManageUsers = true,
        canManageSettings = true,
        canViewBlocklist = true,
    )

internal val adminOverview =
    HubOverview(
        loaded = true,
        userLoad = HubUserLoad.Loaded,
        account = HubAccount(id = 1, name = "Scott", isAdmin = true, avatarUrl = null),
        quota = HubQuota(movie = HubQuotaBucket(limit = 10, remaining = 7, days = 7), tv = null),
        permissions = adminPermissions,
        movieRequestCount = 8,
        tvRequestCount = 4,
        pendingRequestCount = 2,
        openIssueCount = 1,
        userCount = 5,
        blocklistCount = 9,
        hasIssues = true,
        hasBlocklist = true,
    )

/** A plain requester: one manage row, no quota, and none of the counts the server would refuse them. */
internal val requesterOverview =
    HubOverview(
        loaded = true,
        userLoad = HubUserLoad.Loaded,
        account = HubAccount(id = 2, name = "Ana", isAdmin = false, avatarUrl = null),
        permissions = SeerrPermissions(canRequest = true),
    )

internal val previewDownloads =
    listOf(
        HubDownload(
            requestId = 7,
            title = "Fight Club",
            posterUrl = null,
            fraction = 0.75f,
            totalBytes = 2_000_000_000,
            etaMinutes = 12,
        ),
        HubDownload(
            requestId = 8,
            title = "Heat",
            posterUrl = null,
            fraction = 0.2f,
            totalBytes = 4_500_000_000,
            etaMinutes = 48,
        ),
    )

internal fun previewHub(
    health: ConnectionHealth = ConnectionHealth.Healthy,
    overview: HubOverview = adminOverview,
    downloading: List<HubDownload> = previewDownloads,
): HubUiState.Ready = HubUiState.Ready(server = previewServer, health = health, overview = overview, downloading = downloading)

internal val previewHubActions =
    HubActions(
        onOpenSection = {},
        onOpenAccount = {},
        onRetry = {},
        onReconnect = {},
        onDisconnect = {},
    )
