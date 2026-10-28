package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.template.TvTwoPaneCopy
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.SetupActions

/** The television's way into the demo (#1705): sample data on the device, for a viewer with no server to point at. */
@Composable
internal fun ColumnScope.TvDemoContent(
    actions: SetupActions,
    enabled: Boolean,
) {
    TvTwoPaneCopy(
        headline = stringResource(R.string.demo_indicator),
        body = stringResource(R.string.setup_try_demo_note),
        illustration = Icons.Filled.PlayCircle,
        alignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(dimensionResource(R.dimen.tv_form_field_width)),
    )
    TvButton(label = stringResource(R.string.setup_try_demo), onClick = actions.onTryDemo, style = TvButtonStyle.Primary, enabled = enabled)
}
