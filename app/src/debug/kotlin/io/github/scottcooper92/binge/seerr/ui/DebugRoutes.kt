package io.github.scottcooper92.binge.seerr.ui

import kotlinx.serialization.Serializable

/** Debug builds only: the request manage sheet's proposed layout beside the current one. */
@Serializable
data object ManageSheetPrototypeRoute : SeerrRoute

/** Debug builds only: the hub's Binge hint in each of its three states. */
@Serializable
data object BingeHintPrototypeRoute : SeerrRoute

/** Debug builds only: the request detail's proposed "This request" and "Other requests" cards. */
@Serializable
data object RequestCardsPrototypeRoute : SeerrRoute
