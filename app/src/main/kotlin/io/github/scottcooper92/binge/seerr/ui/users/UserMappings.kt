package io.github.scottcooper92.binge.seerr.ui.users

import androidx.annotation.StringRes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.data.UserEntity
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.PermissionGroup
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import java.time.Instant
import java.time.OffsetDateTime

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
    val handle = listOfNotNull(username, jellyfinUsername, plexUsername).firstOrNull { it.isNotBlank() }
    val name =
        listOfNotNull(displayName, handle).firstOrNull { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: return null
    return toUserItem(name, handle)
}

/** As [toUserItem], but a page for one user has nothing to fall back to: the id stands in for a name. */
fun SeerrUserDto.toUserItemOrFallback(): UserItem {
    val handle = listOfNotNull(username, jellyfinUsername, plexUsername).firstOrNull { it.isNotBlank() }
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
        avatarUrl = avatar?.takeIf { it.startsWith("http") },
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

private fun String.toEpochMillisOrNull(): Long? =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
