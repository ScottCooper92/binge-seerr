package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The connected server's address in large monospace, the way a television's own link plate renders
 * a code to read across a room instead of recalling — Settings is where the television's address
 * step sends a user who is already signed in on the phone.
 */
@Composable
internal fun ServerAddressPlate(
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BingeShapes.Large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            text = stringResource(R.string.settings_address_plate_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = baseUrl,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_xxs)),
        )
        Text(
            text = stringResource(R.string.settings_address_plate_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_xs)),
        )
    }
}
