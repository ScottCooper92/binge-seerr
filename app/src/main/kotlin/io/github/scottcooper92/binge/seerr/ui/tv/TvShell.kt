package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.nav.TvNavRailItem
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.tv.nav.TvShellScaffold as DesignTvShellScaffold

/** The rail's destinations, in rail order; Settings is the footer. */
internal enum class TvDestination(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Account("account", R.string.tv_rail_account, Icons.Filled.Person),
    Hub("hub", R.string.tv_rail_hub, Icons.Filled.Home),
    Issues("issues", R.string.hub_section_issues, Icons.Filled.ReportProblem),
    Settings("settings", R.string.hub_section_settings, Icons.Filled.Settings),
}

/**
 * The rail beside a content pane, with per-destination UI state retained across switches and the TV Back
 * hierarchy: Back with focus in the content moves it onto the rail; Back on the rail off Home goes Home;
 * Back on Home leaves the app. An [overlay] is a full-screen surface above the rail that owns Back itself.
 * All of that is the design system's [DesignTvShellScaffold]; what is this app's own is the destinations, in rail order,
 * and which of them are on the rail.
 */
@Composable
internal fun TvShellScaffold(
    selected: TvDestination,
    onSelect: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    accountName: String? = null,
    accountAvatarUrl: String? = null,
    /** Whether Issues is on the rail: only for an account the server lets list issues. */
    showIssues: Boolean = true,
    content: @Composable (TvDestination) -> Unit,
) {
    DesignTvShellScaffold(
        // The account sits at the top of the rail, above Home, which is still where the app opens; its avatar is the
        // signed-in user's, or the person icon until they are known. Settings stays on the bottom edge.
        header = TvDestination.Account.toRailItem().copy(displayName = accountName, avatarUrl = accountAvatarUrl),
        items =
            TvDestination.entries
                .filter { it != TvDestination.Settings && it != TvDestination.Account }
                .filter { it != TvDestination.Issues || showIssues }
                .map { it.toRailItem() },
        footer = TvDestination.Settings.toRailItem(),
        selectedKey = selected,
        homeKey = TvDestination.Hub,
        onSelect = { key -> (key as? TvDestination)?.let(onSelect) },
        modifier = modifier,
        overlay = overlay,
        pinFooter = true,
    ) { key ->
        (key as? TvDestination)?.let { content(it) }
    }
}

// The destination itself is the key: the scaffold saves keys in a Bundle, and an enum is one.
@Composable
private fun TvDestination.toRailItem(): TvNavRailItem = TvNavRailItem(key = this, label = stringResource(labelRes), icon = icon)
