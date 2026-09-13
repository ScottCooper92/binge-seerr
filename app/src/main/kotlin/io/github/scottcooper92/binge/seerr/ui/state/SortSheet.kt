package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.binge.designsystem.component.BingeBottomSheet
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/** A sort picker over any order: the choices as radio rows; picking one applies it and dismisses. */
@Composable
fun <T> SortSheet(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        SortContent(
            choices = choices,
            selected = selected,
            label = label,
            onSelect = {
                onSelect(it)
                onDismiss()
            },
        )
    }
}

@Composable
fun <T> SortContent(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
        Text(
            text = stringResource(R.string.requests_sort_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
        )
        choices.forEach { choice ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                        .selectable(selected = choice == selected, role = Role.RadioButton, onClick = { onSelect(choice) })
                        .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            ) {
                RadioButton(selected = choice == selected, onClick = null)
                Text(text = label(choice), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
