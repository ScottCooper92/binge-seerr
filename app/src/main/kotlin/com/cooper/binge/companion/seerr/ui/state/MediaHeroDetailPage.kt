package com.cooper.binge.companion.seerr.ui.state

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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.DetailCinematicHeader
import com.binge.designsystem.component.DetailHero
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.isExpandedLayout
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.HeroDetailScreen
import com.binge.designsystem.R as DesR

/**
 * What the expanded header shows beside the backdrop: the [posterUrl], the [synopsis] and the [stats] the single-column page
 * keeps in its body. A page that gives none of it keeps the single-column hero at every width.
 */
internal class WideHeader(
    val posterUrl: String?,
    val synopsis: String?,
    val stats: List<DetailStat>,
)

/**
 * A media-hero detail page — a request's, a blocklisted title's — on the design system's
 * [HeroDetailScreen]: the hero runs full-bleed, the bar fades in over it, and [footer] sits below the scroll.
 * This app owns the hero: [DetailHero] with its chrome off, since the template's bar carries back and
 * [topBarActions], the rich backdrop, and the [LayoutAnchors.Detail.HERO] anchor a page's skeleton is checked
 * against (#572). The body runs full-width, and the status bar is left to the theme.
 *
 * @param wideHeader at the expanded width, a page that passes this swaps in the design system's [DetailCinematicHeader]; without it,
 *   or on a narrower window, the single-column hero stays.
 * @param onBack null where the pane beside this one already offers the way back.
 * @param inFlight shows a moderation under way.
 * @param genres drawn under the title in the accent colour, with the italic [tagline] under them.
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
    genres: List<String> = emptyList(),
    tagline: String? = null,
    wideHeader: WideHeader? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    // At the expanded width the cinematic header takes the place of the hero, as Binge's detail pages do.
    val cinematic = wideHeader != null && isExpandedLayout()
    HeroDetailScreen(
        title = title,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        scrollState = scrollState,
        heroHeight = dimensionResource(if (cinematic) DesR.dimen.detail_cinematic_header_height else DesR.dimen.detail_hero_height),
        horizontalInset = dimensionResource(if (cinematic) DesR.dimen.detail_cinematic_header_padding else DesR.dimen.padding_m),
        contentMaxWidth = Dp.Infinity,
        darkStatusBar = false,
        inFlight = inFlight,
        actions = topBarActions,
        footer = footer,
        hero = {
            if (cinematic && wideHeader != null) {
                DetailCinematicHeader(
                    title = title,
                    genres = genres,
                    synopsis = wideHeader.synopsis,
                    stats = wideHeader.stats,
                    backdropUrl = backdropUrl,
                    posterUrl = wideHeader.posterUrl,
                    tagline = tagline,
                    modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.HERO)),
                )
            } else {
                DetailHero(
                    title = title,
                    backdropUrl = backdropUrl,
                    tagline = tagline,
                    genres = genres,
                    metaText = metaText,
                    onBack = {},
                    showChrome = false,
                    richBackdrop = true,
                    metaContent = metaContent,
                    modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.HERO)),
                )
            }
        },
        content = body,
    )
}

/** Where a hero page's skeleton or its failure sits before there is a page: the theme's background, full window. */
@Composable
internal fun MediaHeroDetailPlaceholder(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
}
