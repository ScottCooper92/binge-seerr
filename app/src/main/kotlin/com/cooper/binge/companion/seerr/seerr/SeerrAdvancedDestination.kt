package com.cooper.binge.companion.seerr.seerr

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
 * do — the same "preselect, fall back to first" rule [com.cooper.binge.companion.seerr.ui.DestinationChoices.withChoices]
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
 * The servers this user may send this media's shape to. A 4K server is a 4K request, so it is
 * offered only when [allow4k] is true: the caller may request 4K of this media type (see
 * `SeerrPermissions.mayRequest4k`), which is narrower than holding CAPABILITY_REQUEST_4K. A server left out
 * here is one this integration did not offer, and naming it later is INVALID_ARGUMENT, as the
 * contract says for any `server_id` it did not offer.
 */
private suspend fun SeerrApi.offeredServers(
    isTv: Boolean,
    allow4k: Boolean,
): List<SeerrServerDto> = arrServers(isTv).filter { allow4k || !it.is4k }

/**
 * The full advanced-request destination for a fresh picker: every server this media's shape may go
 * to, and the profile/root-folder choices of the preselected one, the server a plain `SubmitRequest`
 * (never 4K) would have used. Empty when this shape has no server this user may use.
 *
 * A shape whose every instance is 4K-only still lists them to a user who may request 4K, since the
 * servers axis is the point of this rpc, but with nothing preselected.
 */
suspend fun SeerrApi.advancedRequestOptions(
    isTv: Boolean,
    allow4k: Boolean,
): DestinationChoices {
    val servers = offeredServers(isTv, allow4k)
    if (servers.isEmpty()) return DestinationChoices.getDefaultInstance()
    val server = servers.forRequest(is4k = false).preferred()
    val destination = server?.let { destinationChoicesFor(isTv, it) } ?: DestinationChoices.getDefaultInstance()
    return destination
        .toBuilder()
        .addAllServers(servers.map { it.toServerChoice() })
        .setSelectedServerId(server?.id?.toString().orEmpty())
        .build()
}

/** Re-resolves the profile/root-folder axes after the host's picker moves to [serverId]. */
suspend fun SeerrApi.destinationOptions(
    isTv: Boolean,
    serverId: String,
    allow4k: Boolean,
): DestinationChoices {
    val server =
        offeredServers(isTv, allow4k).firstOrNull { it.id.toString() == serverId }
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
 * The destination a [com.cooper.binge.companion.seerr.service.SeerrRequestService.submitAdvancedRequest]
 * submits with. An empty axis falls back to what [advancedRequestOptions] would have preselected, so an
 * untouched submit is a plain request in every way but its path.
 *
 * Whether a well-formed profile or root folder is one the server offers is left to Seerr, which refuses it.
 * The details fetch runs only when an axis is left empty, so the common submit carrying both pays for neither.
 *
 * @param serverId An unrecognised id is a bad argument, not a fall-through to the default: it names a choice
 *   the picker offered, so failing loudly tells the host its picker state is stale. A 4K server named by a
 *   user who may not request 4K is the same, since it was never offered.
 * @param profileId One that is not a number can name no profile, so it is refused rather than dropped for
 *   the server's default.
 */
suspend fun SeerrApi.resolveAdvancedDestination(
    isTv: Boolean,
    serverId: String,
    profileId: String,
    rootFolderId: String,
    allow4k: Boolean,
): ResolvedAdvancedDestination {
    val servers = offeredServers(isTv, allow4k)
    val server =
        if (serverId.isEmpty()) {
            servers.forRequest(is4k = false).preferred()
                ?: throw StatusException(Status.FAILED_PRECONDITION.withDescription("no destination server configured"))
        } else {
            servers.firstOrNull { it.id.toString() == serverId }
                ?: throw StatusException(Status.INVALID_ARGUMENT.withDescription("unknown server_id $serverId"))
        }
    val explicitProfile = explicitProfileId(profileId)
    val destination = if (profileId.isEmpty() || rootFolderId.isEmpty()) destinationChoicesFor(isTv, server) else null
    return ResolvedAdvancedDestination(
        server = server,
        profileId = explicitProfile ?: destination?.selectedProfileId?.toIntOrNull(),
        rootFolder = rootFolderId.ifEmpty { null } ?: destination?.selectedRootFolderId?.ifEmpty { null },
    )
}

/** Null for an empty profile id, which falls back to the default; a present one that is not a number names no profile. */
private fun explicitProfileId(raw: String): Int? =
    raw.ifEmpty { null }?.let {
        it.toIntOrNull() ?: throw StatusException(Status.INVALID_ARGUMENT.withDescription("profile_id must be a number, not $it"))
    }
