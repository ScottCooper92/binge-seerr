package com.cooper.binge.companion.seerr.telemetry

import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.SharedAnalyticsEvents

/**
 * One finished sign-in, in the shared library's events: `sign_in` for a success, `sign_in_failed` for a failure. The method is
 * the mode's name, or the shared demo name for Try the demo. Never an address, a host or a user.
 */
internal fun Analytics.reportSignIn(
    mode: String,
    success: Boolean,
) {
    val method = if (mode == "demo") SharedAnalyticsEvents.METHOD_DEMO else mode
    if (success) {
        event(SharedAnalyticsEvents.SIGN_IN, mapOf(SharedAnalyticsEvents.PARAM_METHOD to method))
    } else {
        event(
            SharedAnalyticsEvents.SIGN_IN_FAILED,
            mapOf(
                SharedAnalyticsEvents.PARAM_METHOD to method,
                SharedAnalyticsEvents.PARAM_REASON to AnalyticsEvents.REASON_SIGN_IN_FAILED,
            ),
        )
    }
}
