package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.R as DesR

/**
 * One section of an editor form, laid out like the design system's `ItemGroup`: the title sits
 * above the surface and the fields sit on it. The title is a `heading()` so TalkBack can jump card
 * to card, and it is required, because a card with nothing naming it is the old unlabelled form.
 */
@Composable
internal fun EditorSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(horizontal = dimensionResource(DesR.dimen.padding_s))
                    .padding(bottom = dimensionResource(DesR.dimen.padding_s))
                    .semantics { heading() },
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BingeShapes.Large)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            content = content,
        )
    }
}
