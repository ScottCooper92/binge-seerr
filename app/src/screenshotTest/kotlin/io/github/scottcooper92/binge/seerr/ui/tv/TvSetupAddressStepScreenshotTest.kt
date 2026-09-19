package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupUiState

private val NoSetupActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {})

/**
 * The address step's body copy (#323): it used to describe typing as the only route to a server
 * address, and now points at the phone app's Settings too. Framed so a future reword of this text
 * is caught the way the link plate's copy already is.
 */
class TvSetupAddressStepScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun address() =
        TvSetupScreen(
            state = SetupUiState.Address(serverUrl = "", insecure = false, isInspecting = false, error = null),
            actions = NoSetupActions,
        )
}
