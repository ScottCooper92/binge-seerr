package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * Cancel and Save, pinned to the bottom of an [EditorPage] that has [EditorValidation] so neither
 * scrolls away with a long form. Save stays tappable while the draft has issues: the tap is what
 * shows the user which ones, rather than a dimmed button that says nothing.
 */
@Composable
internal fun EditorActionBar(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saving: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = BingeShapes.HeroTop,
        shadowElevation = dimensionResource(DesR.dimen.snackbar_elevation),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = resolvedContentInset())
                    .padding(top = dimensionResource(DesR.dimen.padding_sm), bottom = dimensionResource(DesR.dimen.padding_m)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            BingeOutlinedButton(
                label = stringResource(R.string.editor_cancel),
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
            BingeFilledButton(
                label = stringResource(R.string.user_settings_save),
                onClick = onSave,
                enabled = saveEnabled,
                loading = saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
