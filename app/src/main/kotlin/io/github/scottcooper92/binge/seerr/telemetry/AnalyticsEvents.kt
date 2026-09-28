package io.github.scottcooper92.binge.seerr.telemetry

/**
 * The write-path actions this app reports, fired once per committed outcome rather than on every
 * intermediate state change. A variant of an action (a block after a decline, a create vs. an
 * update) rides [PARAM_ACTION] rather than becoming a separate event name.
 */
object AnalyticsEvents {
    const val REQUEST_MODERATED = "request_moderated"
    const val ISSUE_REPORTED = "issue_reported"
    const val ISSUE_MODERATED = "issue_moderated"
    const val ISSUE_COMMENTED = "issue_commented"
    const val BLOCKLIST_CHANGED = "blocklist_changed"
    const val SIGN_IN = "sign_in"
    const val PASSWORD_RESET_REQUESTED = "password_reset_requested"
    const val SERVER_DISCONNECTED = "server_disconnected"
    const val NOTIFICATION_SIGNAL_CHANGED = "notification_signal_changed"
    const val SHAKE_TO_REPORT_CHANGED = "shake_to_report_changed"
    const val DVR_INSTANCE_CHANGED = "dvr_instance_changed"
    const val OVERRIDE_RULE_CHANGED = "override_rule_changed"
    const val NOTIFICATION_AGENT_CHANGED = "notification_agent_changed"

    const val PARAM_ACTION = "action"
    const val PARAM_METHOD = "method"
    const val PARAM_SUCCESS = "success"
    const val PARAM_ENABLED = "enabled"
    const val PARAM_TYPE = "type"
    const val PARAM_AGENT = "agent"
    const val PARAM_SIGNAL = "signal"
}
