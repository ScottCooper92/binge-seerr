package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.ui.SeerrRoute

/**
 * Not reached in a release build while every [SeerrRoute] member is named in [SeerrRoute.screenName]'s own `when`, which
 * `ScreenNamesTest` checks. If one is missed, a screen reported under a generic name is a smaller failure than the app
 * stopping, so this says so rather than throwing as the debug variant does.
 */
internal fun debugScreenName(route: SeerrRoute): String = UNNAMED_SCREEN

private const val UNNAMED_SCREEN = "unnamed_screen"
