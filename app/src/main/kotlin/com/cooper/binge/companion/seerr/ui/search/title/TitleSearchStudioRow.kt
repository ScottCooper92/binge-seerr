package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowHeader
import com.binge.designsystem.component.ListRowLogo
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.StudioResult
import com.binge.designsystem.R as DesR

/**
 * One studio the search found: its logo in the slot a poster takes, its name, and "Studio" under it.
 */
@Composable
internal fun StudioResultRow(
    studio: StudioResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        modifier = modifier,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leading = { ListRowLogo(logoUrl = studio.logoUrl) },
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            ListRowHeader(title = studio.name)
            Spacer(Modifier.height(dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)))
            Text(
                text = stringResource(R.string.title_kind_studio),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
