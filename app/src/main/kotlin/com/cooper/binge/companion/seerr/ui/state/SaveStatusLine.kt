package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.users.settings.SaveStatus
import com.binge.designsystem.R as DesR

/**
 * The quiet "Saving…" then "Saved" text a page that saves as it changes shows in its top bar (#930), through the form's
 * `extraActions`, so it takes no room from the form and says nothing when idle. A failed save says nothing here:
 * [SaveFailedSnackbar] owns that, with Retry.
 */
@Composable
fun SaveStatusLine(
    status: SaveStatus,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            failed -> return
            status == SaveStatus.Saving -> stringResource(R.string.editor_saving)
            status == SaveStatus.Saved -> stringResource(R.string.user_settings_saved)
            else -> return
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .padding(horizontal = dimensionResource(DesR.dimen.padding_s))
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
