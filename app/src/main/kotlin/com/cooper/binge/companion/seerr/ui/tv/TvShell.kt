package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import com.binge.designsystem.tv.component.TvIconButton
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
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
) {
    Account("account", R.string.tv_rail_account, Icons.Filled.Person),
    Hub("hub", R.string.tv_rail_hub, Icons.Filled.Home),
    Issues("issues", R.string.hub_section_issues, Icons.Filled.ReportProblem),
    Settings("settings", R.string.hub_section_settings, Icons.Filled.Settings),
}

/**
 * Home with a top row of buttons that open the other pages above it. There is no rail: Home is the only
 * destination, so Back on it leaves the app, and a [page] other than [TvDestination.Hub] is a full-screen surface above
 * Home that owns Back and returns focus to the button that opened it. An [overlay] (a detail page, a see-all grid) sits
 * above both and owns Back itself.
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
    content: @Composable (TvDestination) -> Unit,
) {
    val opener = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val closer = rememberTvOverlayCloser(restoreTo = opener.getValue(page), onClose = onClosePage)
    Box(modifier = modifier.fillMaxSize()) {
        // Nothing sits beside Home, so it carries its own overscan and claims first focus as an overlay does.
        CompositionLocalProvider(LocalTvHostedAsOverlay provides true) { content(TvDestination.Hub) }
        TvHomeTopRow(
            destinations = TvDestination.entries.filter { it != TvDestination.Hub && (it != TvDestination.Issues || showIssues) },
            opener = opener,
            onOpen = onOpenPage,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        if (page != TvDestination.Hub) {
            // Back is the page's own handler only while no overlay above it is up to take it first.
            BackHandler(enabled = overlay == null, onBack = closer::close)
            CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).tvExitFocusGroup(),
                ) { content(page) }
            }
        }
        overlay?.let {
            CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
                Box(modifier = Modifier.fillMaxSize().tvExitFocusGroup(), content = it)
            }
        }
    }
}

/** The icon buttons in Home's top-right corner, each revealing its name on focus. */
@Composable
private fun TvHomeTopRow(
    destinations: List<TvDestination>,
    opener: Map<TvDestination, FocusRequester>,
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
                modifier = Modifier.focusRequester(opener.getValue(destination)),
            )
        }
    }
}
