package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cooper.binge.companion.seerr.seerr.SeerrError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** Says each failed pull in the usual message, over the page that stayed as it was. */
@Composable
internal fun ReloadFailureSnackbar(
    failures: Flow<SeerrError>,
    snackbarHostState: SnackbarHostState,
) {
    val reporter = rememberErrorReporter(snackbarHostState)
    LaunchedEffect(failures) { failures.collectLatest { error -> reporter.report(error) } }
}
