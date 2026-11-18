package com.cooper.binge.companion.seerr.telemetry

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.BlocklistDetailRoute
import com.cooper.binge.companion.seerr.ui.BlocklistRoute
import com.cooper.binge.companion.seerr.ui.DiagnosticsRoute
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.DiscoverSliderRoute
import com.cooper.binge.companion.seerr.ui.DvrInstanceRoute
import com.cooper.binge.companion.seerr.ui.EditConnectionRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.IssueDetailRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.LicencesRoute
import com.cooper.binge.companion.seerr.ui.NotificationAgentRoute
import com.cooper.binge.companion.seerr.ui.OverrideRuleRoute
import com.cooper.binge.companion.seerr.ui.RequestDetailRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.SeasonEpisodesRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.SettingsRoute
import com.cooper.binge.companion.seerr.ui.SettingsSearchRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.ThisAppRoute
import com.cooper.binge.companion.seerr.ui.TitleCreditsRoute
import com.cooper.binge.companion.seerr.ui.TitleDetailRoute
import com.cooper.binge.companion.seerr.ui.UserDetailRoute
import com.cooper.binge.companion.seerr.ui.UserSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.UserSettingsRoute
import com.cooper.binge.companion.seerr.ui.UsersRoute
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import com.cooper.binge.companion.seerr.ui.tv.TvDestination
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * The name a screen is reported under. Written out because R8 renames classes in a release build, and
 * free of ids because a route's ids are the server's (`privacy.md` covers the Crashlytics key).
 *
 * [SeerrRoute]'s debug-only members live in `src/debug` (#467) and cannot be named here; [debugScreenName]
 * covers them per variant. The cost is the `when`'s exhaustiveness check: a new common route that misses a branch here
 * reaches [debugScreenName], which throws in a debug build and reports a generic name in a release one. The build does not
 * fail, so `ScreenNamesTest` walks every route and asks for its name (#1969).
 */
fun SeerrRoute.screenName(): String =
    when (this) {
        HomeRoute -> "home"
        HubRoute -> "hub"
        ShellRoute -> "shell"
        RequestsRoute -> "requests"
        is RequestDetailRoute -> "request_detail"
        is TitleDetailRoute -> "title_detail"
        is TitleCreditsRoute -> "title_credits"
        is SeasonEpisodesRoute -> "season_episodes"
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
        is DiscoverGridRoute -> "discover_grid"
        is NotificationAgentRoute -> "notification_agent"
        is OverrideRuleRoute -> "override_rule"
        SettingsRoute -> "settings"
        SettingsSearchRoute -> "settings_search"
        EditConnectionRoute -> "edit_connection"
        LicencesRoute -> "licences"
        DiagnosticsRoute -> "diagnostics"
        ThisAppRoute -> "this_app"
        else -> debugScreenName(this)
    }

/** A television destination's name: its own key, marked so it never collides with a phone screen. */
internal fun TvDestination.screenName(): String = "tv_$key"

/** The app's [Analytics], for the television shell, which has no route stack to watch. Silent by default. */
val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoOpAnalytics }

/**
 * The name a tab is reported under while the tab bar is on top. The Settings tab is the hub, so it keeps the hub's name, and
 * the other tabs take their own, so a switch between them is a screen change.
 */
internal fun SeerrTab.screenName(): String =
    when (this) {
        SeerrTab.Settings -> "hub"
        SeerrTab.Movies -> "movies"
        SeerrTab.Tv -> "tv"
        SeerrTab.Search -> "search"
    }

/**
 * The screen the app is showing: the tab bar's selected tab while it is on top, and otherwise the route on top. Null for a key
 * that is not one of the app's screens.
 */
internal fun screenNameFor(
    top: NavKey?,
    selectedTab: SeerrTab,
): String? =
    when (top) {
        ShellRoute -> selectedTab.screenName()
        is SeerrRoute -> top.screenName()
        else -> null
    }

/** The names of the screens as they change, one per change: a push, a pop, a link, or a switch of tab under the tab bar. */
internal fun screenNames(
    backStack: List<NavKey>,
    selectedTab: () -> SeerrTab,
): Flow<String> =
    snapshotFlow { screenNameFor(backStack.lastOrNull(), selectedTab()) }
        .filterNotNull()
        .distinctUntilChanged()
