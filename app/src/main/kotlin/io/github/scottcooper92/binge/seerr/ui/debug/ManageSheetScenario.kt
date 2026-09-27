package io.github.scottcooper92.binge.seerr.ui.debug

import androidx.annotation.StringRes
import io.github.scottcooper92.binge.seerr.R

/** The sheet's distinct shapes, each one of the branches [RequestActionsPrototypeContent] draws. */
internal enum class ManageSheetScenario(
    @param:StringRes val labelRes: Int,
) {
    /** An admin looking at someone's pending request: the full sheet, decision and media alike. */
    PendingModerator(R.string.proto_scenario_pending),

    /** The user's own pending request, with no moderation rights: edit and remove only. */
    PendingOwn(R.string.proto_scenario_own),

    /** A request whose fulfilment failed: retry leads. */
    Failed(R.string.proto_scenario_failed),

    /** Settled and available in both Standard and 4K: the media half leads, with files to delete. */
    Available(R.string.proto_scenario_available),
}
