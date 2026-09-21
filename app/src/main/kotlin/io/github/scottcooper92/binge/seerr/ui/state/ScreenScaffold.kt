package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.OverlaidHeaderContent

/**
 * The frame every phone screen with a top bar shares: Binge's transparent [BingeTopBar] over a body
 * that scrolls under it.
 *
 * The bar draws no container of its own. It slides away as the body scrolls down and comes back as
 * it scrolls up ([TopAppBarDefaults.enterAlwaysScrollBehavior]), and a scrim ramps in with that
 * collapse, so the title stays legible over the rows passing under it.
 *
 * [content] is handed the Scaffold's padding. Place it with [outerPadding] and [innerPadding]: the
 * top inset belongs inside the scroll, or the body stops at the bar's lower edge and nothing ever
 * passes under it.
 *
 * A [header], such as a search field and filter chips, is drawn over the body just below the bar, as
 * Binge's gallery draws its chips. The body then scrolls under the bar and the header together, one
 * scrim spans both, and [content]'s top padding already clears the header.
 *
 * [barScrim] is false only where something under the bar draws the scrim for it, such as a filter
 * pager's own header. The title still follows the collapse, so it stays legible on that scrim.
 *
 * [bottomBar] is for a screen's one primary action that has to stay reachable regardless of scroll
 * position - a long editable list's "add", say, where the equivalent button buried at the list's own
 * end would need a scroll to reach. Persistent rather than a floating action button so it cannot
 * drift over a trailing control the last visible row already has, such as a switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
    barScrim: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (padding: PaddingValues) -> Unit,
) {
    val collapsed = scrollBehavior.state.collapsedFraction
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BingeTopBar(
                title = title,
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                containerColor = Color.Transparent,
                scrimFraction = if (barScrim && header == null) collapsed else 0f,
                foregroundScrimFraction = collapsed,
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { snackbarHostState?.let { BingeSnackbarHost(it) } },
    ) { padding ->
        if (header == null) {
            content(padding)
        } else {
            OverlaidHeaderContent(
                // The bar's height joins the header rather than padding it from outside, so the body reaches
                // the top of the window and passes under both.
                header = {
                    Spacer(Modifier.height(padding.calculateTopPadding()))
                    header()
                },
                modifier = Modifier.padding(padding.outerPadding()),
                headerBackground = Color.Transparent,
                scrimFraction = collapsed,
            ) { overlay ->
                content(PaddingValues(top = overlay.calculateTopPadding(), bottom = padding.calculateBottomPadding()))
            }
        }
    }
}
