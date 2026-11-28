package com.cooper.binge.companion.seerr.ui.discover.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLinkChip
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.template.readingInset
import com.cooper.binge.companion.seerr.discover.ExternalLink
import com.cooper.binge.companion.seerr.discover.ExternalLinkKind
import com.cooper.binge.companion.seerr.feature.discover.R
import com.binge.designsystem.R as DesR

/**
 * The pages about a title or a person on other sites, each a chip that opens outside the app, under a heading and at the reading
 * column's inset. Nothing is drawn when there are none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExternalLinksSection(
    links: List<ExternalLink>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) return
    // One column, as the keywords section is: its heading and chips are not spaced by the page's own column.
    Column(modifier) {
        SectionHeader(
            stringResource(R.string.external_links_heading),
            horizontalPadding = readingInset(),
            bottomPadding = dimensionResource(DesR.dimen.section_header_padding_bottom_tight),
        )
        FlowRow(
            Modifier.padding(horizontal = readingInset()),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            links.forEach { link ->
                val (label, icon) =
                    when (link.kind) {
                        ExternalLinkKind.Imdb -> stringResource(R.string.external_link_imdb) to Icons.Filled.Movie
                        ExternalLinkKind.Tmdb -> stringResource(R.string.external_link_tmdb) to Icons.Filled.Public
                        ExternalLinkKind.Website -> stringResource(R.string.external_link_website) to Icons.Filled.Language
                    }
                BingeLinkChip(label = label, leadingIcon = icon, onClick = { onOpen(link.url) })
            }
        }
    }
}
