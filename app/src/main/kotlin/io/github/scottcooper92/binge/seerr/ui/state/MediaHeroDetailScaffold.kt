package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.DetailHero
import com.binge.designsystem.component.DetailOverlayTopBar

/**
 * The outer frame a media-hero detail page shares — [RequestDetailScreen][io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailScreen]'s
 * and [BlocklistDetailScreen][io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistDetailScreen]'s.
 * No top bar of the Scaffold's own: [MediaHeroDetailPage]'s own [DetailOverlayTopBar] clears the
 * status bar itself, so full-bleed content is all that is left, with the snackbar and the page's end
 * clearing the navigation bar by hand — [pageEdgeInsets] — rather than through the Scaffold's own
 * insets, which are zeroed here.
 */
@Composable
internal fun MediaHeroDetailScaffold(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { BingeSnackbarHost(snackbarHostState, Modifier.windowInsetsPadding(pageEdgeInsets())) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}

/**
 * The hero/scroll/footer layout a media-hero detail page shares.
 *
 * The hero draws no chrome; [DetailOverlayTopBar] floats over it with back and [topBarActions], and
 * brings its scrim in as the hero's tail passes under it.
 *
 * [footer] is a fixed-height sibling below a `weight(1f, fill = false)` scroll rather than pinned over
 * it, so `Column`'s own measure policy — not a remembered pixel height fed back through
 * `onSizeChanged` — sizes the scroll to clear it, correctly on the very first frame including one
 * restored mid-scroll. A `null` [footer] drops it entirely and lets the scroll carry the safe-drawing
 * bottom inset itself; a non-null one is always rendered, so a caller with no footer today must pass
 * `null` rather than an empty lambda, or the scroll stops clearing the bottom inset for no visible
 * reason.
 *
 * The horizontal safe-drawing inset scopes to the scroll and [DetailOverlayTopBar] alone, in the [Box]
 * that wraps them, rather than the outer [Column] — so a raised footer's own surface reaches the true
 * screen edge in landscape, rather than stopping short of a display-cutout inset it does not need
 * protecting from.
 *
 * `tagline`, `showChrome` and `richBackdrop` are not parameters here: every caller today wants `null`,
 * `false` and `true` respectively, and Binge's own movie-detail screen — the one other place
 * [DetailHero] and [DetailOverlayTopBar] pair up — uses a different outer shape entirely (a single
 * scroll with inline actions, no pinned footer), so there is no known near-term caller that would need
 * them to vary. Widening this signature later, if one shows up, is a non-breaking, defaulted-parameter
 * change.
 */
@Composable
internal fun MediaHeroDetailPage(
    title: String,
    backdropUrl: String?,
    metaText: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    topBarActions: @Composable RowScope.(glassBackgroundAlpha: Float) -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .let {
                            if (footer != null) it else it.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        },
            ) {
                DetailHero(
                    title = title,
                    backdropUrl = backdropUrl,
                    tagline = null,
                    metaText = metaText,
                    onBack = onBack,
                    showChrome = false,
                    richBackdrop = true,
                )
                body()
            }
            DetailOverlayTopBar(title = title, scrollState = scrollState, onBack = onBack, actions = topBarActions)
        }
        footer?.invoke()
    }
}
