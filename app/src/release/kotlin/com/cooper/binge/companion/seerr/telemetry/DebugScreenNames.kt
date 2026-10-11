package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.ui.SeerrRoute

/**
 * Unreachable in a release build: every [SeerrRoute] member that exists there is already named in
 * [SeerrRoute.screenName]'s own `when`, so its `else` branch never actually runs here.
 */
internal fun debugScreenName(route: SeerrRoute): String = error("no screen name for $route")
