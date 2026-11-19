package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.DetailInfoGrid
import com.binge.designsystem.component.InfoValue
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
    Column(
        modifier,
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Text(
            text = stringResource(R.string.title_page_details),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.readingMargin().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
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
