package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.cooper.binge.companion.seerr.seerr.SeerrError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** Says each failed pull in the usual message, over the page that stayed as it was. */
@Composable
internal fun ReloadFailureSnackbar(
    failures: Flow<SeerrError>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(failures) {
        failures.collectLatest { error ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = resources.getString(error.messageRes()), kind = SnackbarMessageKind.Error)
        }
    }
}
