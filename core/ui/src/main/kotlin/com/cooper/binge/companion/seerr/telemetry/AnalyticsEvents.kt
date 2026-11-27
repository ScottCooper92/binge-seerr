package com.cooper.binge.companion.seerr.telemetry

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
    const val PASSWORD_RESET_REQUESTED = "password_reset_requested"
    const val NOTIFICATION_SIGNAL_CHANGED = "notification_signal_changed"
    const val DISCOVERY_CHANGED = "discovery_changed"
    const val SHAKE_TO_REPORT_CHANGED = "shake_to_report_changed"
    const val DVR_INSTANCE_CHANGED = "dvr_instance_changed"
    const val OVERRIDE_RULE_CHANGED = "override_rule_changed"
    const val NOTIFICATION_AGENT_CHANGED = "notification_agent_changed"

    /** A request the sheet sent and the server answered: `type` the kind, `action` title, season, collection or episode. */
    const val REQUEST_SUBMITTED = "request_submitted"

    /** Watch was tapped and opened a title: `action` is `app` where a media server's app took it, `web` where its web page did. */
    const val WATCH_OPENED = "watch_opened"

    /** A watchlist entry was added or removed: `action`, and `type` the kind. */
    const val WATCHLIST_CHANGED = "watchlist_changed"

    /** A user was created, deleted, imported, or changed: `action`, and `origin` where the account came from. */
    const val USER_CHANGED = "user_changed"

    /** A user's media server account was linked or unlinked: `action`, and `origin` the server it belongs to. */
    const val ACCOUNT_LINK_CHANGED = "account_link_changed"

    /** The media server's connection, libraries or scan changed: `action`, and `origin` the server kind. */
    const val MEDIA_SERVER_CHANGED = "media_server_changed"

    /** A server settings page saved a change: `action`, with `type` naming the page. */
    const val SERVER_SETTINGS_CHANGED = "server_settings_changed"

    /** A scheduled job was run, cancelled or rescheduled: `action`. */
    const val JOB_CHANGED = "job_changed"

    /** A cache was flushed: `type` names the cache. */
    const val CACHE_FLUSHED = "cache_flushed"

    /** A discover slider was created, updated, deleted or reordered: `action`. */
    const val DISCOVER_SLIDER_CHANGED = "discover_slider_changed"

    const val PARAM_ORIGIN = "origin"

    /** A server operation failed in a way a server version could explain; see `operationFailed`. */
    const val REQUEST_OPERATION_FAILED = "request_operation_failed"

    const val PARAM_ACTION = "action"
    const val PARAM_SUCCESS = "success"
    const val PARAM_ENABLED = "enabled"
    const val PARAM_TYPE = "type"
    const val PARAM_AGENT = "agent"
    const val PARAM_SIGNAL = "signal"
    const val PARAM_OPERATION = "operation"
    const val PARAM_HTTP_STATUS = "http_status"
    const val PARAM_GRPC_STATUS = "grpc_status"
    const val PARAM_SERVER_LINEAGE = "server_lineage"
    const val PARAM_SERVER_VERSION = "server_version"

    /** The one reason `sign_in_failed` carries: the connect machine's event says the sign-in failed, not why. */
    const val REASON_SIGN_IN_FAILED = "failed"

    /** Seerr has no Radarr or Sonarr server set as default for the version, so it cannot delete the files. */
    const val REASON_NO_DEFAULT_SERVICE = "no_default_service"
}
