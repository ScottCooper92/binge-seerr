package io.github.scottcooper92.binge.seerr.telemetry

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.scottcooper92.binge.seerr.ui.BlocklistDetailRoute
import io.github.scottcooper92.binge.seerr.ui.BlocklistRoute
import io.github.scottcooper92.binge.seerr.ui.DiscoverSliderRoute
import io.github.scottcooper92.binge.seerr.ui.DvrInstanceRoute
import io.github.scottcooper92.binge.seerr.ui.EditConnectionRoute
import io.github.scottcooper92.binge.seerr.ui.HomeRoute
import io.github.scottcooper92.binge.seerr.ui.HubRoute
import io.github.scottcooper92.binge.seerr.ui.IssueDetailRoute
import io.github.scottcooper92.binge.seerr.ui.IssuesRoute
import io.github.scottcooper92.binge.seerr.ui.NotificationAgentRoute
import io.github.scottcooper92.binge.seerr.ui.OverrideRuleRoute
import io.github.scottcooper92.binge.seerr.ui.RequestDetailRoute
import io.github.scottcooper92.binge.seerr.ui.RequestsRoute
import io.github.scottcooper92.binge.seerr.ui.SectionRoute
import io.github.scottcooper92.binge.seerr.ui.SeerrRoute
import io.github.scottcooper92.binge.seerr.ui.ServerSettingsPageRoute
import io.github.scottcooper92.binge.seerr.ui.SettingsRoute
import io.github.scottcooper92.binge.seerr.ui.UserDetailRoute
import io.github.scottcooper92.binge.seerr.ui.UserSettingsPageRoute
import io.github.scottcooper92.binge.seerr.ui.UserSettingsRoute
import io.github.scottcooper92.binge.seerr.ui.UsersRoute
import io.github.scottcooper92.binge.seerr.ui.tv.TvDestination

/**
 * The name a screen is reported under. Written out rather than taken from the class, because R8
 * renames classes in a release build, and a route's ids are the server's and never leave the app.
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
        is SectionRoute -> "section"
    }

/** A television destination's name: the rail's own key, marked so it never collides with a phone screen. */
internal fun TvDestination.screenName(): String = "tv_$key"

/** The app's [Analytics], for the television shell, which has no route stack to watch. Silent by default. */
val LocalAnalytics =
    staticCompositionLocalOf<Analytics> {
        object : Analytics {
            override fun screen(name: String) = Unit
        }
    }
