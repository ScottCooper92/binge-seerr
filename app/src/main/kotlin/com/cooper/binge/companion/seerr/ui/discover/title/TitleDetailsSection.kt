package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.DetailInfoGrid
import com.binge.designsystem.component.InfoValue
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.navOverlayEnd
import com.binge.designsystem.navOverlayStart
import com.binge.designsystem.resolvedContentPadding
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The facts at the end of a title's page, under a "Details" heading, in columns across the width as Binge's detail pages have them.
 * A page whose facts are all blank draws neither the heading nor the grid, so a section the server gave nothing for takes no space.
 */
@Composable
internal fun TitleDetailsSection(
    entries: List<DetailInfoEntry>,
    modifier: Modifier = Modifier,
) {
    if (entries.none { it.value.hasText() }) return
    Column(modifier.padding(bottom = dimensionResource(DesR.dimen.padding_m))) {
        // The design system's section header, as Cast and Keywords have, padded by what the grid below pads itself by, which is
        // smaller on the edge shared with a pane beside this page.
        val padding = resolvedContentPadding()
        val direction = LocalLayoutDirection.current
        SectionHeader(
            stringResource(R.string.title_page_details),
            Modifier.readingMargin(),
            startPadding = padding.calculateStartPadding(direction) + navOverlayStart(),
            endPadding = padding.calculateEndPadding(direction) + navOverlayEnd(),
        )
        DetailInfoGrid(entries)
    }
}

private fun InfoValue.hasText(): Boolean =
    when (this) {
        is InfoValue.Plain -> text.isNotBlank()
        is InfoValue.Link -> text.isNotBlank()
        is InfoValue.Links -> links.any { it.text.isNotBlank() }
    }
