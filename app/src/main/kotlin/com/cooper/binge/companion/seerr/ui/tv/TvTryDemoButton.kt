package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvButton
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.SetupActions

/** The television's way into the demo (#1705): sample data on the device, for a viewer with no server to point at. */
@Composable
internal fun TvTryDemoButton(
    actions: SetupActions,
    enabled: Boolean,
) {
    TvButton(label = stringResource(R.string.setup_try_demo), onClick = actions.onTryDemo, enabled = enabled)
}
