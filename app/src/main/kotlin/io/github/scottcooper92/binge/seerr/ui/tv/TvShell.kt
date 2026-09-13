package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.nav.BingeTvNavRail
import com.binge.designsystem.tv.nav.TvNavRailItem
import io.github.scottcooper92.binge.seerr.R
import kotlinx.coroutines.delay

/**
 * How long the rail's highlight may lead the content: the rail selects on focus, so holding ↓ walks every
 * destination between, and content that followed at once would mount one screen per step.
 */
internal const val CONTENT_SETTLE_MILLIS = 220L

/** The rail's destinations, in rail order; Settings is the footer. */
internal enum class TvDestination(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Hub("hub", R.string.tv_rail_hub, Icons.Filled.Home),
    Requests("requests", R.string.hub_section_requests, Icons.Filled.Inbox),
    Issues("issues", R.string.hub_section_issues, Icons.Filled.ReportProblem),
    Settings("settings", R.string.hub_section_settings, Icons.Filled.Settings),
}

/**
 * The rail beside a content pane, with per-destination UI state retained across switches and the TV Back
 * hierarchy: Back with focus in the content moves it onto the rail; Back on the rail off Home goes Home;
 * Back on Home leaves the app. An [overlay] is a full-screen surface above the rail that owns Back itself.
 */
@Composable
internal fun TvShellScaffold(
    selected: TvDestination,
    onSelect: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (TvDestination) -> Unit,
) {
    var settled by rememberSaveable { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != settled) {
            delay(CONTENT_SETTLE_MILLIS)
            settled = selected
        }
    }
    val railFocus = remember { FocusRequester() }
    var railHasFocus by remember { mutableStateOf(false) }
    val overlayOpen = overlay != null
    BackHandler(enabled = !overlayOpen && (!railHasFocus || selected != TvDestination.Hub)) {
        if (!railHasFocus) runCatching { railFocus.requestFocus() } else onSelect(TvDestination.Hub)
    }
    Box(modifier = modifier.fillMaxSize()) {
        BingeTvNavRail(
            header = null,
            items = TvDestination.entries.filter { it != TvDestination.Settings }.map { it.toRailItem() },
            footer = TvDestination.Settings.toRailItem(),
            selectedKey = selected.key,
            onSelect = { key -> TvDestination.entries.firstOrNull { it.key == key }?.let(onSelect) },
            railFocusRequester = railFocus,
            onRailFocusChanged = { railHasFocus = it },
        ) {
            // Each destination keeps its own saved UI state — scroll, the described settings row — while it is
            // off screen; its ViewModels are the Activity's, so they are retained regardless.
            val holder = rememberSaveableStateHolder()
            holder.SaveableStateProvider(settled.key) { content(settled) }
        }
        overlay?.let { Box(modifier = Modifier.fillMaxSize(), content = it) }
    }
}

@Composable
private fun TvDestination.toRailItem(): TvNavRailItem = TvNavRailItem(key = key, label = stringResource(labelRes), icon = icon)
