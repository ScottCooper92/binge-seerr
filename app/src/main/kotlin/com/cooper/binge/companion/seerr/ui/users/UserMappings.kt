package com.cooper.binge.companion.seerr.ui.users

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.RequestPage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.data.UserEntity
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.seerr.PermissionGroup
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.toAvatarUrl
import com.cooper.binge.companion.seerr.seerr.toEpochMillisOrNull

private const val USER_TYPE_PLEX = 1
private const val USER_TYPE_LOCAL = 2
private const val USER_TYPE_JELLYFIN = 3
private const val USER_TYPE_EMBY = 4

@StringRes
internal fun UserSort.labelRes(): Int =
    when (this) {
        UserSort.Created -> R.string.users_sort_created
        UserSort.Updated -> R.string.users_sort_updated
        UserSort.DisplayName -> R.string.users_sort_name
        UserSort.Requests -> R.string.users_sort_requests
    }

@StringRes
internal fun UserOrigin.labelRes(): Int =
    when (this) {
        UserOrigin.Plex -> R.string.user_origin_plex
        UserOrigin.Jellyfin -> R.string.user_origin_jellyfin
        UserOrigin.Emby -> R.string.user_origin_emby
        UserOrigin.Local -> R.string.user_origin_local
    }

/**
 * A mark for each kind of account, with no brand glyphs to hand: Plex's play, Jellyfin's triangle, Emby's hexagon,
 * and the person for a local account.
 */
internal fun UserOrigin.icon(): ImageVector =
    when (this) {
        UserOrigin.Plex -> Icons.Filled.PlayCircle
        UserOrigin.Jellyfin -> Icons.Filled.ChangeHistory
        UserOrigin.Emby -> Icons.Filled.Hexagon
        UserOrigin.Local -> Icons.Filled.Person
    }

@StringRes
internal fun PermissionGroup.labelRes(): Int =
    when (this) {
        PermissionGroup.Administration -> R.string.permission_group_administration
        PermissionGroup.Requests -> R.string.permission_group_requests
        PermissionGroup.Issues -> R.string.permission_group_issues
        PermissionGroup.Blocklist -> R.string.permission_group_blocklist
    }

@StringRes
internal fun ManageablePermission.labelRes(): Int =
    when (this) {
        ManageablePermission.Admin -> R.string.permission_admin
        ManageablePermission.ManageSettings -> R.string.permission_manage_settings
        ManageablePermission.ManageUsers -> R.string.permission_manage_users
        ManageablePermission.ManageRequests -> R.string.permission_manage_requests
        ManageablePermission.ViewRequests -> R.string.permission_view_requests
        ManageablePermission.Request -> R.string.permission_request
        ManageablePermission.Request4k -> R.string.permission_request_4k
        ManageablePermission.RequestAdvanced -> R.string.permission_request_advanced
        ManageablePermission.AutoApprove -> R.string.permission_auto_approve
        ManageablePermission.AutoApprove4k -> R.string.permission_auto_approve_4k
        ManageablePermission.ManageIssues -> R.string.permission_manage_issues
        ManageablePermission.ViewIssues -> R.string.permission_view_issues
        ManageablePermission.CreateIssues -> R.string.permission_create_issues
        ManageablePermission.ManageBlocklist -> R.string.permission_manage_blocklist
        ManageablePermission.ViewBlocklist -> R.string.permission_view_blocklist
        ManageablePermission.ViewRecent -> R.string.permission_view_recent
        ManageablePermission.ViewWatchlists -> R.string.permission_view_watchlists
        ManageablePermission.RequestMovies -> R.string.permission_request_movies
        ManageablePermission.RequestSeries -> R.string.permission_request_series
        ManageablePermission.AutoApproveMovies -> R.string.permission_auto_approve_movies
        ManageablePermission.AutoApproveSeries -> R.string.permission_auto_approve_series
        ManageablePermission.AutoRequest -> R.string.permission_auto_request
        ManageablePermission.AutoRequestMovies -> R.string.permission_auto_request_movies
        ManageablePermission.AutoRequestSeries -> R.string.permission_auto_request_series
        ManageablePermission.Request4kMovies -> R.string.permission_request_4k_movies
        ManageablePermission.Request4kSeries -> R.string.permission_request_4k_series
        ManageablePermission.AutoApprove4kMovies -> R.string.permission_auto_approve_4k_movies
        ManageablePermission.AutoApprove4kSeries -> R.string.permission_auto_approve_4k_series
    }

/**
 * Each permission's glyph. A root says what it grants; a movie or series child takes the app's movie and TV glyphs, so
 * the tree reads by media type under its parent. 4K is the app's high-quality glyph, as on the 4K server toggles.
 */
internal fun ManageablePermission.icon(): ImageVector =
    when (this) {
        ManageablePermission.Admin -> Icons.Filled.AdminPanelSettings
        ManageablePermission.ManageSettings -> Icons.Filled.Settings
        ManageablePermission.ManageUsers -> Icons.Filled.ManageAccounts
        ManageablePermission.ManageRequests -> Icons.AutoMirrored.Filled.PlaylistAddCheck
        ManageablePermission.RequestAdvanced -> Icons.Filled.Tune
        ManageablePermission.ViewRequests -> Icons.Filled.Inbox
        ManageablePermission.ViewRecent -> Icons.Filled.LibraryAdd
        ManageablePermission.ViewWatchlists -> Icons.Filled.Bookmark
        ManageablePermission.Request -> Icons.Filled.RequestPage
        ManageablePermission.AutoApprove -> Icons.Filled.DoneAll
        ManageablePermission.AutoRequest -> Icons.Filled.BookmarkAdded
        ManageablePermission.Request4k -> Icons.Filled.HighQuality
        ManageablePermission.AutoApprove4k -> Icons.Filled.Verified
        ManageablePermission.RequestMovies,
        ManageablePermission.AutoApproveMovies,
        ManageablePermission.AutoRequestMovies,
        ManageablePermission.Request4kMovies,
        ManageablePermission.AutoApprove4kMovies,
        -> Icons.Filled.Movie
        ManageablePermission.RequestSeries,
        ManageablePermission.AutoApproveSeries,
        ManageablePermission.AutoRequestSeries,
        ManageablePermission.Request4kSeries,
        ManageablePermission.AutoApprove4kSeries,
        -> Icons.Filled.Tv
        ManageablePermission.ManageIssues -> Icons.Filled.ReportProblem
        ManageablePermission.CreateIssues -> Icons.Filled.Report
        ManageablePermission.ViewIssues -> Icons.Filled.Visibility
        ManageablePermission.ManageBlocklist -> Icons.Filled.Block
        ManageablePermission.ViewBlocklist -> Icons.Filled.VisibilityOff
    }

/** An unknown `userType` reads as local, the only kind without a server behind it. */
internal fun Int?.toUserOrigin(): UserOrigin =
    when (this) {
        USER_TYPE_PLEX -> UserOrigin.Plex
        USER_TYPE_JELLYFIN -> UserOrigin.Jellyfin
        USER_TYPE_EMBY -> UserOrigin.Emby
        USER_TYPE_LOCAL -> UserOrigin.Local
        else -> UserOrigin.Local
    }

/** Null for a user with no name to show; the row would be blank. */
fun SeerrUserDto.toUserItem(): UserItem? {
    val handle = listOfNotNull(jellyfinUsername, plexUsername, username).firstOrNull { it.isNotBlank() }
    val name =
        listOfNotNull(displayName, handle).firstOrNull { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: return null
    return toUserItem(name, handle)
}

/** As [toUserItem], but a page for one user has nothing to fall back to: the id stands in for a name. */
fun SeerrUserDto.toUserItemOrFallback(): UserItem {
    val handle = listOfNotNull(jellyfinUsername, plexUsername, username).firstOrNull { it.isNotBlank() }
    val name =
        listOfNotNull(displayName, handle).firstOrNull { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "#$id"
    return toUserItem(name, handle)
}

private fun SeerrUserDto.toUserItem(
    name: String,
    handle: String?,
): UserItem =
    UserItem(
        id = id,
        name = name,
        email = email?.takeIf { it.isNotBlank() },
        handle = handle,
        avatarUrl = avatar.toAvatarUrl(),
        origin = userType.toUserOrigin(),
        permissions = permissions ?: 0,
        requestCount = requestCount ?: 0,
        createdAtMillis = createdAt?.toEpochMillisOrNull(),
    )

fun UserItem.toEntity(
    listKey: String,
    orderIndex: Int,
): UserEntity =
    UserEntity(
        listKey = listKey,
        id = id,
        name = name,
        email = email,
        handle = handle,
        avatarUrl = avatarUrl,
        origin = origin.name,
        permissions = permissions,
        requestCount = requestCount,
        createdAtMillis = createdAtMillis,
        orderIndex = orderIndex,
    )

fun UserEntity.toUserItem(): UserItem =
    UserItem(
        id = id,
        name = name,
        email = email,
        handle = handle,
        avatarUrl = avatarUrl,
        origin = UserOrigin.valueOf(origin),
        permissions = permissions,
        requestCount = requestCount,
        createdAtMillis = createdAtMillis,
    )
