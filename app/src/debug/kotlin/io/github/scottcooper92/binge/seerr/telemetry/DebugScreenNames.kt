package io.github.scottcooper92.binge.seerr.telemetry

import io.github.scottcooper92.binge.seerr.ui.DeveloperOptionsRoute
import io.github.scottcooper92.binge.seerr.ui.ManageSheetPrototypeRoute
import io.github.scottcooper92.binge.seerr.ui.RequestCardsPrototypeRoute
import io.github.scottcooper92.binge.seerr.ui.SeerrRoute

/** [SeerrRoute.screenName]'s debug-only branches — the two routes that exist only in `src/debug`. */
internal fun debugScreenName(route: SeerrRoute): String =
    when (route) {
        DeveloperOptionsRoute -> "developer_options"
        ManageSheetPrototypeRoute -> "manage_sheet_prototype"
        RequestCardsPrototypeRoute -> "request_cards_prototype"
        else -> error("no screen name for $route")
    }
