package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState

/**
 * What the server holds of one version of a title: standard, or 4K when [fourK] is true. [versionShown] is false on a
 * server with no 4K version, where "HD" would name a choice nobody has.
 */
internal data class VersionStatus(
    val availability: TitleAvailability,
    val fourK: Boolean,
    val versionShown: Boolean = true,
)

/** The page's primary action, in the slot under the stats. */
internal sealed interface TitlePrimary {
    /** Opens the request sheet. [fourKOnly] is true when only the 4K version is still open, so the button says so. */
    data class Request(
        val fourKOnly: Boolean,
    ) : TitlePrimary

    /** Nothing can be requested, so the slot says where the title stands. */
    data class Status(
        val status: VersionStatus,
    ) : TitlePrimary

    data object None : TitlePrimary
}

/** The primary action, and the state of the other version as a short line under the actions, where it has one. */
internal data class TitleActionsModel(
    val primary: TitlePrimary,
    val otherStatuses: List<VersionStatus>,
)

/**
 * The actionable version is the primary. Every state the server holds of a version is also said: as the primary when it is
 * the one thing to say, otherwise as a line beside the button. A series can be partly available and still requestable,
 * so a version that may be requested can have a state too.
 */
internal fun titleActionsModel(
    standard: VersionState,
    fourK: VersionState?,
): TitleActionsModel {
    val statuses =
        listOfNotNull(
            VersionStatus(standard.availability, fourK = false, versionShown = fourK != null),
            fourK?.let { VersionStatus(it.availability, fourK = true) },
        ).filter { it.availability != TitleAvailability.None }
    return when {
        standard.mayRequest || fourK?.mayRequest == true ->
            TitleActionsModel(TitlePrimary.Request(fourKOnly = !standard.mayRequest), statuses)
        statuses.isEmpty() -> TitleActionsModel(TitlePrimary.None, emptyList())
        else -> TitleActionsModel(TitlePrimary.Status(statuses.first()), statuses.drop(1))
    }
}
