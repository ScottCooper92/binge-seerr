package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/** The pages the server named for the person, each a chip that opens it. Nothing is drawn when it named none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonLinksRow(
    links: List<PersonLink>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        links.forEach { link ->
            val (label, icon) =
                when (link.kind) {
                    PersonLinkKind.Imdb -> stringResource(R.string.person_page_imdb) to Icons.Filled.Movie
                    PersonLinkKind.Website -> stringResource(R.string.person_page_website) to Icons.Filled.Language
                }
            AssistChip(onClick = { onOpen(link.url) }, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) })
        }
    }
}
