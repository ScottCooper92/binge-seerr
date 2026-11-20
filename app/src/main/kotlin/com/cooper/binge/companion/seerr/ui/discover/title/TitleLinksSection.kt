package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
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
import com.binge.designsystem.resolvedContentInset
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.TitleLink
import com.cooper.binge.companion.seerr.discover.title.TitleLinkKind
import com.binge.designsystem.R as DesR

/** The pages about the title on other sites, each a chip that opens outside the app. Nothing is drawn when there are none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TitleLinksSection(
    links: List<TitleLink>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) return
    SectionHeader(stringResource(R.string.person_page_links), modifier)
    FlowRow(
        modifier.padding(horizontal = resolvedContentInset()),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        links.forEach { link ->
            val (label, icon) =
                when (link.kind) {
                    TitleLinkKind.Imdb -> stringResource(R.string.person_page_imdb) to Icons.Filled.Movie
                    TitleLinkKind.Tmdb -> stringResource(R.string.title_link_tmdb) to Icons.Filled.Public
                    TitleLinkKind.Website -> stringResource(R.string.person_page_website) to Icons.Filled.Language
                }
            BingeLinkChip(label = label, leadingIcon = icon, onClick = { onOpen(link.url) })
        }
    }
}
