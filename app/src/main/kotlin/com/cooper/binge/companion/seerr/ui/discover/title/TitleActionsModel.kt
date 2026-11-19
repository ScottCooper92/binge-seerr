package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.TitleRequest
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode

/** Where a title stands, as the page's one primary button says it: the best of what the server holds of its versions. */
internal enum class TitleState { Request, Requested, Processing, PartiallyAvailable, Available, Blocklisted }

/** What a tap on the primary opens. */
internal sealed interface PrimaryTap {
    /** The request sheet: the options for a new request, or for the version still open. */
    data object RequestSheet : PrimaryTap

    /** The sheet of one request the server holds, with what this viewer may do to it. */
    data class ManageRequest(
        val requestId: Int,
    ) : PrimaryTap

    /** Nothing to open yet, so the button is drawn disabled. */
    data object Nothing : PrimaryTap
}

/**
 * The page's primary action: always drawn, whatever the state, and what it says follows [state]. [fourKOnly] is true in the Request
 * state when only the 4K version is still open, so the button says so.
 */
internal data class TitlePrimary(
    val state: TitleState,
    val fourKOnly: Boolean,
    val tap: PrimaryTap,
)

/** The page's actions under the stats: the primary, which is there in every state. */
internal data class TitleActionsModel(
    val primary: TitlePrimary,
    /** Report is offered once the title is in the library, in whole or in part, where the server takes an issue and the viewer may make one. */
    val canReport: Boolean,
)

/**
 * The primary is a Request while the standard version is still open and untouched, whatever the 4K version holds. Otherwise it follows the best state any version has: available over partly available over processing over requested, then
 * blocklisted, then nothing held, which is a Request. A tap opens the request sheet where a version can still be asked for and
 * nothing is held, or while a partly available title can still have more requested; otherwise the sheet of the request to manage, the newest one that was not declined; with no request on file and a
 * version still open it is the request sheet again (for the 4K version), and otherwise there is nothing to open.
 */
internal fun titleActionsModel(
    standard: VersionState,
    fourK: VersionState?,
    requests: List<TitleRequest>,
    mayReport: Boolean,
): TitleActionsModel {
    val openStandard = standard.mayRequest && standard.availability == TitleAvailability.None
    val state =
        if (openStandard) {
            TitleState.Request
        } else {
            listOfNotNull(standard.availability, fourK?.availability).map { it.toState() }.maxByOrNull { it.rank } ?: TitleState.Request
        }
    val mayRequest = standard.mayRequest || fourK?.mayRequest == true
    val tap = tapFor(state, mayRequest, requests)
    return TitleActionsModel(
        TitlePrimary(
            state,
            fourKOnly =
                state == TitleState.Request && !standard.mayRequest && fourK?.mayRequest == true,
            tap = tap,
        ),
        canReport = mayReport && state.isInLibrary(),
    )
}

/** What a tap on the primary opens, as the KDoc of [titleActionsModel] sets out. */
private fun tapFor(
    state: TitleState,
    mayRequest: Boolean,
    requests: List<TitleRequest>,
): PrimaryTap =
    when {
        state == TitleState.Request -> if (mayRequest) PrimaryTap.RequestSheet else PrimaryTap.Nothing
        state == TitleState.Blocklisted -> PrimaryTap.Nothing
        state == TitleState.PartiallyAvailable && mayRequest -> PrimaryTap.RequestSheet
        else ->
            requests.toManage()?.let { PrimaryTap.ManageRequest(it.id) }
                ?: if (mayRequest) PrimaryTap.RequestSheet else PrimaryTap.Nothing
    }

private fun TitleState.isInLibrary() = this == TitleState.Available || this == TitleState.PartiallyAvailable

private fun TitleAvailability.toState() =
    when (this) {
        TitleAvailability.None -> TitleState.Request
        TitleAvailability.Pending -> TitleState.Requested
        TitleAvailability.Processing -> TitleState.Processing
        TitleAvailability.PartiallyAvailable -> TitleState.PartiallyAvailable
        TitleAvailability.Available -> TitleState.Available
        TitleAvailability.Blocklisted -> TitleState.Blocklisted
    }

/** Higher wins: the state a reader most wants to see when a title's versions differ. */
private val TitleState.rank: Int
    get() =
        when (this) {
            TitleState.Available -> 5
            TitleState.PartiallyAvailable -> 4
            TitleState.Processing -> 3
            TitleState.Requested -> 2
            TitleState.Blocklisted -> 1
            TitleState.Request -> 0
        }

/** The request a tap opens: the newest the server lists that was not declined, else the newest there is. */
internal fun List<TitleRequest>.toManage(): TitleRequest? =
    filter { it.status != SeerrRequestStatusCode.Declined }.maxByOrNull { it.id } ?: maxByOrNull { it.id }
