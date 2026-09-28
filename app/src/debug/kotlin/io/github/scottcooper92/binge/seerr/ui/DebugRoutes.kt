package io.github.scottcooper92.binge.seerr.ui

import kotlinx.serialization.Serializable

/** Debug builds only: tools for working on the app, reached from the hub. */
@Serializable
data object DeveloperOptionsRoute : SeerrRoute

/** Debug builds only: the request manage sheet's proposed layout beside the current one. */
@Serializable
data object ManageSheetPrototypeRoute : SeerrRoute
