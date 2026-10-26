package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.ui.BingeHintPrototypeRoute
import com.cooper.binge.companion.seerr.ui.FlagOverridesRoute
import com.cooper.binge.companion.seerr.ui.ManageSheetPrototypeRoute
import com.cooper.binge.companion.seerr.ui.RequestCardsPrototypeRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute

/** [SeerrRoute.screenName]'s debug-only branches — the two routes that exist only in `src/debug`. */
internal fun debugScreenName(route: SeerrRoute): String =
    when (route) {
        BingeHintPrototypeRoute -> "binge_hint_prototype"
        FlagOverridesRoute -> "flag_overrides"
        ManageSheetPrototypeRoute -> "manage_sheet_prototype"
        RequestCardsPrototypeRoute -> "request_cards_prototype"
        else -> error("no screen name for $route")
    }
