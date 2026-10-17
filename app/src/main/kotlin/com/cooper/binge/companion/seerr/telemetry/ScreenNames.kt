package com.cooper.binge.companion.seerr.telemetry

import androidx.compose.runtime.staticCompositionLocalOf
import com.cooper.binge.companion.seerr.ui.BlocklistDetailRoute
import com.cooper.binge.companion.seerr.ui.BlocklistRoute
import com.cooper.binge.companion.seerr.ui.DiscoverSliderRoute
import com.cooper.binge.companion.seerr.ui.DvrInstanceRoute
import com.cooper.binge.companion.seerr.ui.EditConnectionRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.IssueDetailRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.NotificationAgentRoute
import com.cooper.binge.companion.seerr.ui.OverrideRuleRoute
import com.cooper.binge.companion.seerr.ui.RequestDetailRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.SettingsRoute
import com.cooper.binge.companion.seerr.ui.UserDetailRoute
import com.cooper.binge.companion.seerr.ui.UserSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.UserSettingsRoute
import com.cooper.binge.companion.seerr.ui.UsersRoute
import com.cooper.binge.companion.seerr.ui.tv.TvDestination

/**
 * The name a screen is reported under. Written out because R8 renames classes in a release build, and
 * free of ids because a route's ids are the server's (`privacy.md` covers the Crashlytics key).
 *
 * [SeerrRoute]'s debug-only members live in `src/debug` (#467) and cannot be named here; [debugScreenName]
 * covers them per variant. The cost is the `when`'s exhaustiveness check: a new common route that misses
 * a branch here falls through silently rather than failing the build.
 */
fun SeerrRoute.screenName(): String =
    when (this) {
        HomeRoute -> "home"
        HubRoute -> "hub"
        RequestsRoute -> "requests"
        is RequestDetailRoute -> "request_detail"
        IssuesRoute -> "issues"
        is IssueDetailRoute -> "issue_detail"
        BlocklistRoute -> "blocklist"
        is BlocklistDetailRoute -> "blocklist_detail"
        UsersRoute -> "users"
        is UserDetailRoute -> "user_detail"
        is UserSettingsRoute -> "user_settings"
        is UserSettingsPageRoute -> "user_settings_page"
        is ServerSettingsPageRoute -> "server_settings_page"
        is DvrInstanceRoute -> "dvr_instance"
        is DiscoverSliderRoute -> "discover_slider"
        is NotificationAgentRoute -> "notification_agent"
        is OverrideRuleRoute -> "override_rule"
        SettingsRoute -> "settings"
        EditConnectionRoute -> "edit_connection"
        else -> debugScreenName(this)
    }

/** A television destination's name: its own key, marked so it never collides with a phone screen. */
internal fun TvDestination.screenName(): String = "tv_$key"

/** The app's [Analytics], for the television shell, which has no route stack to watch. Silent by default. */
val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoOpAnalytics }
