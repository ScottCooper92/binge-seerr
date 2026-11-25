package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import com.binge.companion.sdk.attempt
import com.binge.designsystem.tv.focus.TvFocusSink
import com.binge.designsystem.tv.focus.TvOverlayCloser
import com.binge.designsystem.tv.focus.tvExitFocusGroup
import com.binge.designsystem.tv.nav.LocalTvHostedAsOverlay
import com.binge.designsystem.tv.nav.LocalTvTopInset
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/** The pages of the connected TV app: the tabs in the top bar, and the Settings sheet it opens over them. */
internal enum class TvDestination(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
    /** Whether the page is a side sheet over the current tab, which draws its own scrim, rather than a tab. */
    val isSheet: Boolean = false,
) {
    Hub("hub", AdminR.string.hub_section_requests, Icons.Filled.Inbox),
    Issues("issues", AdminR.string.hub_section_issues, Icons.Filled.ReportProblem),
    Settings("settings", R.string.hub_section_settings, Icons.Filled.Settings, isSheet = true),
}

/**
 * A title's own page over everything, the bar included: it fills the screen, so it takes no top inset. It draws its own
 * background, a page an opaque one and a side sheet its scrim, so a sheet can show the board through. Focus is trapped in
 * it; Back is the page's own, and it closes through [closer], which lets focus go as the exit starts.
 * The page beneath takes focus back through its own hand-off.
 */
@Composable
internal fun TvShellTopLayer(
    closer: TvOverlayCloser,
    content: @Composable (TvOverlayCloser) -> Unit,
) {
    closer.ReportDisposal()
    CompositionLocalProvider(LocalTvTopInset provides Dp.Hairline, LocalTvHostedAsOverlay provides true) {
        HandBackHost(
            entry = remember { FocusRequester() },
            active = true,
            modifier = Modifier.tvExitFocusGroup { closer.trapping },
        ) { content(closer) }
    }
}

/** A sheet slides in over everything, the bar included; Back closes it unless a page above it takes Back first. */
@Composable
internal fun TvShellSheet(
    overlayOpen: Boolean,
    closer: TvOverlayCloser,
    content: @Composable () -> Unit,
) {
    closer.ReportDisposal()
    BackHandler(enabled = !overlayOpen, onBack = closer::close)
    CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
        HandBackHost(
            entry = remember { FocusRequester() },
            active = true,
            modifier = Modifier.tvExitFocusGroup { closer.trapping },
            content = content,
        )
    }
}

/**
 * A surface whose focus is handed back to it when a page or grid above it closes (#1509). Closing parks focus on the wrong
 * card, and a request into a group that already holds focus skips its entry routing, so a [TvFocusSink] takes the parked
 * focus and the hand-off then enters the content's own group.
 *
 * With [sinkOnlyWhileCovered] the sink is there only while [covered] and until the hand-off runs, so a page with nothing
 * focusable does not leave it as the one thing the bar's Down can reach. A hand-off that finds nothing sends focus up to the bar.
 */
@Composable
internal fun HandBackHost(
    entry: FocusRequester,
    active: Boolean,
    modifier: Modifier = Modifier,
    covered: Boolean = false,
    sinkOnlyWhileCovered: Boolean = false,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var hostHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var handBackOwed by remember { mutableStateOf(false) }
    LaunchedEffect(covered) { if (covered) handBackOwed = true }
    LaunchedEffect(contentHasFocus) { if (contentHasFocus) handBackOwed = false }
    val sinkWanted = active && !contentHasFocus && (!sinkOnlyWhileCovered || handBackOwed)
    Box(modifier = modifier.fillMaxSize().onFocusChanged { hostHasFocus = it.hasFocus }.focusGroup()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onFocusChanged { contentHasFocus = it.hasFocus }
                    .focusRequester(entry)
                    .focusGroup(),
        ) { content() }
        if (sinkWanted) TvFocusSink()
    }
    val sinkHoldsFocus = active && hostHasFocus && !contentHasFocus
    LaunchedEffect(sinkHoldsFocus) {
        if (sinkHoldsFocus) {
            val entered = attempt { entry.requestFocus() }.getOrNull() == true
            handBackOwed = false
            if (!entered && sinkOnlyWhileCovered) focusManager.moveFocus(FocusDirection.Up)
        }
    }
}
