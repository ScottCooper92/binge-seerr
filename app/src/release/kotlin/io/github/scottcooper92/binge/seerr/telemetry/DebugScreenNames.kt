package io.github.scottcooper92.binge.seerr.telemetry

import io.github.scottcooper92.binge.seerr.ui.SeerrRoute

/**
 * Unreachable in a release build: every [SeerrRoute] member that exists there is already named in
 * [SeerrRoute.screenName]'s own `when`, so its `else` branch never actually runs here.
 */
internal fun debugScreenName(route: SeerrRoute): String = error("no screen name for $route")
