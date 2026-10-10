package io.github.scottcooper92.binge.seerr.service

import com.binge.companion.contracts.request.v1.ApprovalState
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.DownloadProgress
import com.binge.companion.contracts.request.v1.RequestInfo
import com.binge.companion.contracts.request.v1.RequestStatus
import io.github.scottcooper92.binge.seerr.data.CachedStatus
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.requesterIds
import kotlinx.coroutines.flow.map

/**
 * Seerr's permissions as the contract's capability set — the handshake is derived, never
 * hand-listed — narrowed by the server: a blocklist the lineage lacks, or issues an Overseerr is
 * too old for, are not offered however the user's bits read, since the server answers them 404.
 * A 4K request or a season edit the administrator switched off is refused the same way.
 */
fun SeerrPermissions.toCapabilities(profile: SeerrServerProfile): Set<Capability> =
    buildSet {
        add(Capability.CAPABILITY_OBSERVE_STATUS)
        add(Capability.CAPABILITY_ATTENTION)
        val settings = profile.settings
        if (canRequest4kOn(settings)) add(Capability.CAPABILITY_REQUEST_4K)
        if (canRequestAdvanced) {
            // Both capabilities are declared together during rollout: a host that only knows the
            // older one keeps getting the Activity hand-off, one that knows the new one gets the
            // native picker. Neither is dropped until the cutover (Binge#2882, scope item 7).
            add(Capability.CAPABILITY_ADVANCED_OPTIONS)
            add(Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS)
        }
        if (canManageRequests) addAll(listOf(Capability.CAPABILITY_APPROVE, Capability.CAPABILITY_DECLINE, Capability.CAPABILITY_RETRY))
        // A requester may cancel their own pending request, and a moderator anyone's. Seasons belong only to a
        // series, so the edit is offered only to a user who may request one.
        if (canRequest || canManageRequests) add(Capability.CAPABILITY_CANCEL)
        if ((canRequestSeries || canManageRequests) && settings.partialRequestsEnabled) add(Capability.CAPABILITY_EDIT_SEASONS)
        // Seerr's request list is open to any signed-in user and narrows itself to their own requests,
        // and a batch of statuses is the core GetStatus many times over, so neither needs a permission.
        add(Capability.CAPABILITY_LIST_REQUESTS)
        add(Capability.CAPABILITY_BATCH_STATUS)
        if (canReportIssues && profile.hasIssues) add(Capability.CAPABILITY_REPORT_ISSUE)
        if (canManageBlocklist && profile.hasBlocklist) add(Capability.CAPABILITY_BLOCK)
    }

/** The capabilities that act on one existing request, and so travel on that request's own `allowed_actions`. */
private val REQUEST_SCOPED =
    setOf(
        Capability.CAPABILITY_APPROVE,
        Capability.CAPABILITY_DECLINE,
        Capability.CAPABILITY_RETRY,
        Capability.CAPABILITY_CANCEL,
        Capability.CAPABILITY_EDIT_SEASONS,
    )

/**
 * The server's status with its allowed actions filled in for the user [viewerId]. Each request carries
 * its own set. The 4K state is cleared unless this user is declared `CAPABILITY_REQUEST_4K`, as the
 * contract asks, so it never tells them about a version they cannot request; their `download` leaves
 * the 4K downloads out for the same reason. The title's set holds a
 * report only against something available in a version they can see, and the block
 * capability either way: it offers a block on a title and an unblock on a blocked one. Its
 * request-scoped entries are the union of the requests', so a
 * host that reads only the title-level list is never offered an action that every request refuses.
 */
fun SeerrPermissions.withAllowedActions(
    server: CachedStatus,
    viewerId: Int,
    profile: SeerrServerProfile,
): RequestStatus {
    val declared = toCapabilities(profile)
    val status = server.status
    val requests =
        status.requestsList.map { request ->
            val own = server.requesterIds[request.id] == viewerId
            request
                .toBuilder()
                .clearAllowedActions()
                .addAllAllowedActions(requestActions(request, own, declared))
                .build()
        }
    val requestActions = requests.flatMapTo(mutableSetOf()) { it.allowedActionsList }
    val sees4k = Capability.CAPABILITY_REQUEST_4K in declared
    val reportable = status.isReportable(sees4k)
    val titleActions =
        declared.filter { capability ->
            when (capability) {
                in REQUEST_SCOPED -> capability in requestActions
                Capability.CAPABILITY_REPORT_ISSUE -> reportable
                else -> true
            }
        }
    return status
        .toBuilder()
        .apply { if (!sees4k) leaveOut4k(server.standardDownload) }
        .clearRequests()
        .addAllRequests(requests)
        .clearAllowedActions()
        .addAllAllowedActions(titleActions)
        .build()
}

/** Everything about the 4K version: its state, and its downloads, which leaves [standardDownload] as the download. */
private fun RequestStatus.Builder.leaveOut4k(standardDownload: DownloadProgress?) {
    clearAvailability4K().clearSeasons4K()
    if (standardDownload != null) setDownload(standardDownload) else clearDownload()
}

/**
 * Whether there is something to report an issue against: the standard version available, or partly,
 * or the 4K one where [sees4k]. `ReportIssue` refuses anything else with FAILED_PRECONDITION.
 */
fun RequestStatus.isReportable(sees4k: Boolean): Boolean = availability.isReportable() || (sees4k && availability4K.isReportable())

private fun Availability.isReportable(): Boolean =
    this == Availability.AVAILABILITY_AVAILABLE || this == Availability.AVAILABILITY_PARTIALLY_AVAILABLE

/** One request on its own, as a `ListRequests` entry carries it, with what the viewer may do to it; [own] when they made it. */
fun SeerrPermissions.withAllowedActions(
    request: RequestInfo,
    own: Boolean,
    profile: SeerrServerProfile,
): RequestInfo =
    request
        .toBuilder()
        .clearAllowedActions()
        .addAllAllowedActions(requestActions(request, own, toCapabilities(profile)))
        .build()

/**
 * The checks Seerr makes on one request: approve and decline need a pending request and retry a failed
 * one, each for a moderator. Cancel is a moderator's on any request, or the requester's own while it
 * is pending. An edit is the moderator's or the requester's, only on a pending TV request.
 */
private fun SeerrPermissions.requestActions(
    request: RequestInfo,
    own: Boolean,
    declared: Set<Capability>,
): List<Capability> {
    val pending = request.state.isPending()
    return declared.filter { capability ->
        when (capability) {
            Capability.CAPABILITY_APPROVE, Capability.CAPABILITY_DECLINE -> pending
            Capability.CAPABILITY_RETRY -> request.state.isFailed()
            Capability.CAPABILITY_CANCEL -> canManageRequests || (own && pending)
            Capability.CAPABILITY_EDIT_SEASONS -> request.seasonNumbersCount > 0 && pending && (canManageRequests || own)
            else -> false
        }
    }
}

private fun ApprovalState.isPending() = this == ApprovalState.APPROVAL_STATE_PENDING

private fun ApprovalState.isFailed() = this == ApprovalState.APPROVAL_STATE_FAILED
