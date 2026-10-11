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
import com.binge.designsystem.resolvedContentInset
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.users.settings.SaveStatus
import com.binge.designsystem.R as DesR

/**
 * The quiet "Saving…" then "Saved" line a page that saves as it changes shows (#930). It keeps its line when there is
 * nothing to say, so the form under it does not jump as the status comes and goes. A failed save says nothing here:
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
            failed -> ""
            status == SaveStatus.Saving -> stringResource(R.string.editor_saving)
            status == SaveStatus.Saved -> stringResource(R.string.user_settings_saved)
            else -> ""
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .padding(horizontal = resolvedContentInset(), vertical = dimensionResource(DesR.dimen.padding_xs))
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
