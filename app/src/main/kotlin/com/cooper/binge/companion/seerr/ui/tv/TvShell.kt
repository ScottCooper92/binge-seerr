package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import com.binge.designsystem.tv.component.TvIconButton
import com.binge.designsystem.tv.focus.TvFocusSink
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.binge.designsystem.tv.focus.tvExitFocusGroup
import com.binge.designsystem.tv.nav.LocalTvHostedAsOverlay
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** The pages of the connected TV app: Home, and the three that open above it from its top row. */
internal enum class TvDestination(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
    /** Whether the page is a side sheet over Home, which draws its own scrim, rather than a full-screen page. */
    val isSheet: Boolean = false,
) {
    Account("account", R.string.tv_rail_account, Icons.Filled.Person, isSheet = true),
    Hub("hub", R.string.tv_rail_hub, Icons.Filled.Home),
    Issues("issues", R.string.hub_section_issues, Icons.Filled.ReportProblem),
    Settings("settings", R.string.hub_section_settings, Icons.Filled.Settings, isSheet = true),
}

/**
 * Home with a top row of buttons that open the other pages above it. A [page] other than [TvDestination.Hub] is a full-screen
 * surface over Home or, for [TvDestination.isSheet], a side sheet that draws its own scrim. Either owns Back and returns focus to
 * the button that opened it, and [content] is given a `close` for a page that ends itself. There is no rail: Home is the only
 * destination, so Back on it leaves the app. An [overlay] (a detail page, a see-all grid) sits above both and owns Back itself.
 */
@Composable
internal fun TvHomeShell(
    page: TvDestination,
    onOpenPage: (TvDestination) -> Unit,
    onClosePage: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    /** Whether Issues is offered: only for an account the server lets list issues. */
    showIssues: Boolean = true,
    content: @Composable (destination: TvDestination, close: () -> Unit) -> Unit,
) {
    val opener = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val closer = rememberTvOverlayCloser(restoreTo = opener.getValue(page), onClose = onClosePage)
    val homeEntry = remember { FocusRequester() }
    Box(modifier = modifier.fillMaxSize()) {
        // Nothing sits beside Home, so it carries its own overscan and claims first focus as an overlay does.
        CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
            // Inert while a page covers it, so the page's own hand-off is the only one that takes focus back.
            HandBackHost(entry = homeEntry, active = page == TvDestination.Hub) { content(TvDestination.Hub, closer::close) }
        }
        TvHomeTopRow(
            destinations = TvDestination.entries.filter { it != TvDestination.Hub && (it != TvDestination.Issues || showIssues) },
            opener = opener,
            homeEntry = homeEntry,
            onOpen = onOpenPage,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        if (page != TvDestination.Hub) {
            // Back is the page's own handler only while no overlay above it is up to take it first.
            BackHandler(enabled = overlay == null, onBack = closer::close)
            CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
                HandBackHost(
                    entry = remember { FocusRequester() },
                    active = true,
                    // A trap refuses a request leaving it, so the page lets go of it while an overlay above takes focus.
                    modifier =
                        Modifier
                            .then(if (page.isSheet) Modifier else Modifier.background(MaterialTheme.colorScheme.background))
                            .then(if (overlay == null) Modifier.tvExitFocusGroup() else Modifier),
                ) { content(page, closer::close) }
            }
        }
        overlay?.let {
            CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
                Box(modifier = Modifier.fillMaxSize().tvExitFocusGroup(), content = it)
            }
        }
    }
}

/**
 * A surface whose focus is handed back to it, as the rail's content pane does, when a page or grid above it closes.
 *
 * Closing disposes the focused node, and Compose parks focus on the first focusable thing left, which is the wrong
 * card, and a request into a group that already holds focus skips its entry routing. So the surface carries a [TvFocusSink]
 * while nothing in it has focus; the sink takes the parked focus, and the hand-off then enters the content's own group,
 * where the hub's memory lands the card or tile that opened the page (#1509).
 */
@Composable
private fun HandBackHost(
    entry: FocusRequester,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hostHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize().onFocusChanged { hostHasFocus = it.hasFocus }.focusGroup()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onFocusChanged { contentHasFocus = it.hasFocus }
                    .focusRequester(entry)
                    .focusGroup(),
        ) { content() }
        if (active && !contentHasFocus) TvFocusSink()
    }
    val sinkHoldsFocus = active && hostHasFocus && !contentHasFocus
    LaunchedEffect(sinkHoldsFocus) {
        if (sinkHoldsFocus) restoreTvOverlayFocus(entry)
    }
}

/** The icon buttons in Home's top-right corner, each revealing its name on focus. */
@Composable
private fun TvHomeTopRow(
    destinations: List<TvDestination>,
    opener: Map<TvDestination, FocusRequester>,
    homeEntry: FocusRequester,
    onOpen: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.padding(
                top = dimensionResource(TvR.dimen.tv_overscan_vertical),
                end = dimensionResource(TvR.dimen.tv_overscan_horizontal),
            ),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        destinations.forEach { destination ->
            TvIconButton(
                icon = destination.icon,
                label = stringResource(destination.labelRes),
                onClick = { onOpen(destination) },
                // Down is routed, not searched for: the buttons sit at the far end from where Home's rows start, and
                // entering the group lets its own memory land the card the user came from.
                modifier = Modifier.focusRequester(opener.getValue(destination)).focusProperties { down = homeEntry },
            )
        }
    }
}
