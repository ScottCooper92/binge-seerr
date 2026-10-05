package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.DetailHero
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.HeroDetailScreen

/**
 * A media-hero detail page — a request's, a blocklisted title's — on the design system's
 * [HeroDetailScreen]: the hero runs full-bleed, the bar fades in over it, and [footer] sits below the scroll
 * so the scroll always clears it. What is this app's own is the hero: [DetailHero] with its chrome off,
 * since the template's bar carries back and [topBarActions], the rich backdrop, and the
 * [LayoutAnchors.Detail.HERO] anchor a page's skeleton is checked against (#572).
 *
 * The body runs full-width, as it always has here, and the status bar is left to the theme. [onBack] is
 * null where the pane beside this one already offers the way back. [inFlight] shows a moderation under way.
 */
@Composable
internal fun MediaHeroDetailPage(
    title: String,
    backdropUrl: String?,
    metaText: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    scrollState: ScrollState = rememberScrollState(),
    topBarActions: @Composable RowScope.(glassBackgroundAlpha: Float) -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    inFlight: Boolean = false,
    metaContent: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    HeroDetailScreen(
        title = title,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        scrollState = scrollState,
        contentMaxWidth = Dp.Infinity,
        darkStatusBar = false,
        inFlight = inFlight,
        actions = topBarActions,
        footer = footer,
        hero = {
            DetailHero(
                title = title,
                backdropUrl = backdropUrl,
                tagline = null,
                metaText = metaText,
                onBack = {},
                showChrome = false,
                richBackdrop = true,
                metaContent = metaContent,
                modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.HERO)),
            )
        },
        content = body,
    )
}

/** Where a hero page's skeleton or its failure sits before there is a page: the theme's background, full window. */
@Composable
internal fun MediaHeroDetailPlaceholder(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
}
