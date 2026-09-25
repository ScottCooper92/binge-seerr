package io.github.scottcooper92.binge.seerr.seerr

import com.binge.companion.contracts.request.v1.Choice
import com.binge.companion.contracts.request.v1.DestinationChoices
import com.binge.companion.contracts.request.v1.ServerChoice
import io.grpc.Status
import io.grpc.StatusException

/** One server as the contract's picker entry: 4K is a server property, not a separate flag. */
fun SeerrServerDto.toServerChoice(): ServerChoice =
    ServerChoice
        .newBuilder()
        .setId(id.toString())
        .setLabel(name)
        .setIs4K(is4k)
        .build()

fun SeerrProfileDto.toChoice(): Choice =
    Choice
        .newBuilder()
        .setId(id.toString())
        .setLabel(name)
        .build()

/** A root folder's own id plays no part on the wire: the path is what Seerr's request body takes back. */
fun SeerrRootFolderDto.toChoice(): Choice =
    Choice
        .newBuilder()
        .setId(path)
        .setLabel(path)
        .build()

/**
 * The chosen server's profile/root-folder axes as the contract shape, with Seerr's own `active*`
 * fields resolved to the choice they still match — or the server's first, where they no longer
 * do — the same "preselect, fall back to first" rule [io.github.scottcooper92.binge.seerr.ui.DestinationChoices.withChoices]
 * applies for the in-app hand-off.
 */
suspend fun SeerrApi.destinationChoicesFor(
    isTv: Boolean,
    server: SeerrServerDto,
): DestinationChoices {
    val details = arrServer(isTv, server.id)
    val profileId = details.profiles.firstOrNull { it.id == server.activeProfileId }?.id ?: details.profiles.firstOrNull()?.id
    val rootFolder = details.rootFolders.firstOrNull { it.path == server.activeDirectory }?.path ?: details.rootFolders.firstOrNull()?.path
    return DestinationChoices
        .newBuilder()
        .addAllProfiles(details.profiles.map { it.toChoice() })
        .setSelectedProfileId(profileId?.toString().orEmpty())
        .addAllRootFolders(details.rootFolders.map { it.toChoice() })
        .setSelectedRootFolderId(rootFolder.orEmpty())
        .build()
}

/**
 * The full advanced-request destination for a fresh picker: every server this media's shape may go
 * to, and the preselected one's profile/root-folder choices — the same server a plain
 * `SubmitRequest` (never 4K) would have used. Empty when this shape has no server configured at
 * all; the host reads that as nothing to override.
 */
suspend fun SeerrApi.advancedRequestOptions(isTv: Boolean): DestinationChoices {
    val servers = arrServers(isTv)
    val server = servers.forRequest(is4k = false).preferred() ?: return DestinationChoices.getDefaultInstance()
    return destinationChoicesFor(isTv, server)
        .toBuilder()
        .addAllServers(servers.map { it.toServerChoice() })
        .setSelectedServerId(server.id.toString())
        .build()
}

/** Re-resolves the profile/root-folder axes after the host's picker moves to [serverId]. */
suspend fun SeerrApi.destinationOptions(
    isTv: Boolean,
    serverId: String,
): DestinationChoices {
    val server =
        arrServers(isTv).firstOrNull { it.id.toString() == serverId }
            ?: throw StatusException(Status.INVALID_ARGUMENT.withDescription("unknown server_id $serverId"))
    return destinationChoicesFor(isTv, server)
}

/** What one axis resolves to when a submit leaves it at the integration's own default. */
data class ResolvedAdvancedDestination(
    val server: SeerrServerDto,
    val profileId: Int?,
    val rootFolder: String?,
)

/**
 * The destination a [io.github.scottcooper92.binge.seerr.service.SeerrRequestService.submitAdvancedRequest]
 * submits with: an empty axis falls back to the same default [advancedRequestOptions] would have
 * preselected, so a submit with every field untouched is a plain request in every way but its
 * path. An explicit but unrecognised server id is a bad argument, not a silent fall-through to the
 * default — unlike an empty one, it names a choice the picker offered, so a submit against it
 * failing loudly is what tells the host its own picker state is stale.
 *
 * The profile/root-folder details fetch only runs when an axis is actually left empty: a submit
 * that already carries both is the common case, and it pays for none of the two extra round trips
 * this rpc's doc otherwise calls out as the cost of moving the picker out of the companion's own
 * process.
 */
suspend fun SeerrApi.resolveAdvancedDestination(
    isTv: Boolean,
    serverId: String,
    profileId: String,
    rootFolderId: String,
): ResolvedAdvancedDestination {
    val servers = arrServers(isTv)
    val server =
        if (serverId.isEmpty()) {
            servers.forRequest(is4k = false).preferred()
                ?: throw StatusException(Status.FAILED_PRECONDITION.withDescription("no destination server configured"))
        } else {
            servers.firstOrNull { it.id.toString() == serverId }
                ?: throw StatusException(Status.INVALID_ARGUMENT.withDescription("unknown server_id $serverId"))
        }
    val destination = if (profileId.isEmpty() || rootFolderId.isEmpty()) destinationChoicesFor(isTv, server) else null
    return ResolvedAdvancedDestination(
        server = server,
        profileId = profileId.toIntOrNull() ?: destination?.selectedProfileId?.toIntOrNull(),
        rootFolder = rootFolderId.ifEmpty { null } ?: destination?.selectedRootFolderId?.ifEmpty { null },
    )
}
