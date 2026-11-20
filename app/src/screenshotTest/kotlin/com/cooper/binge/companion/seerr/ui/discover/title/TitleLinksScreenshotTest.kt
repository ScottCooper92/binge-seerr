package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.title.TitleLink
import com.cooper.binge.companion.seerr.discover.title.TitleLinkKind
import com.cooper.binge.companion.seerr.preview.PHONE_WIDTH
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews

/** The title page's links: all three, and only TMDB, which every title has. Worst case at font scale 2, where the chips wrap. */
class TitleLinksScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun allLinks() = Frame(ALL)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun tmdbOnly() = Frame(ALL.filter { it.kind == TitleLinkKind.Tmdb })

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun allLinksSpanish() = Frame(ALL)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun allLinksLargestText() = Frame(ALL)
}

private val ALL =
    listOf(
        TitleLink(TitleLinkKind.Imdb, "https://www.imdb.com/title/tt0133093"),
        TitleLink(TitleLinkKind.Tmdb, "https://www.themoviedb.org/movie/603"),
        TitleLink(TitleLinkKind.Website, "https://film.example.org/"),
    )

@Composable
private fun Frame(links: List<TitleLink>) {
    Column(Modifier.width(PHONE_WIDTH).background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
        TitleLinksSection(links, onOpen = {})
    }
}
