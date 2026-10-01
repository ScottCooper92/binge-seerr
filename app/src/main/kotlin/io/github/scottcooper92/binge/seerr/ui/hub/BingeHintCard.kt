package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.tonalContainer
import com.binge.designsystem.R as DesR

/**
 * The hub's hint about Binge. It looks like the design system's dismissible hint card, with a text
 * button beneath, since these ask the user to go somewhere rather than only telling them something.
 * The button's trailing icon says it leaves the app.
 */
@Composable
internal fun BingeHintCard(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BingeShapes.Large)
                .background(MaterialTheme.colorScheme.primary.tonalContainer()),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = dimensionResource(DesR.dimen.settings_group_row_padding_h),
                            top = dimensionResource(DesR.dimen.settings_group_row_padding_v),
                            end = dimensionResource(DesR.dimen.hint_card_dismiss_end_inset),
                        ),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(dimensionResource(DesR.dimen.icon_size_l)),
                )
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            TextButton(
                onClick = onAction,
                modifier = Modifier.defaultMinSize(minHeight = dimensionResource(DesR.dimen.button_filled_height)),
                shape = BingeShapes.Medium,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(dimensionResource(DesR.dimen.button_filled_icon_spacing)))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(dimensionResource(DesR.dimen.button_filled_icon_size)),
                )
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(DesR.string.hint_card_dismiss))
        }
    }
}
