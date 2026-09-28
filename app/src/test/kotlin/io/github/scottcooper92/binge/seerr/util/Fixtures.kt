package io.github.scottcooper92.binge.seerr.util

import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs

/** Records every [event] call in order, so a test can say which action fired and with what params. */
class RecordingAnalytics : Analytics {
    val screens = mutableListOf<String>()
    val events = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun screen(name: String) {
        screens += name
    }

    override fun event(
        name: String,
        properties: Map<String, Any>,
    ) {
        events += name to properties
    }
}

/** Records every breadcrumb call, so a test can say a crash-context note was left for an action. */
class RecordingCrashBreadcrumbs : CrashBreadcrumbs {
    val logs = mutableListOf<String>()
    val keys = mutableListOf<Pair<String, String>>()

    override fun log(message: String) {
        logs += message
    }

    override fun key(
        name: String,
        value: String,
    ) {
        keys += name to value
    }
}
