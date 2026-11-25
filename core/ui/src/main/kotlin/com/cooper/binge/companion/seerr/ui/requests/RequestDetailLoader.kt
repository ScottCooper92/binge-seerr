package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How a screen outside the requests area reads one request's detail: given the request's id, the detail once it has loaded, and null
 * until then. The nav host provides the real one, which reads it through the request's own view model, so a list and a sheet over the
 * same request fetch it once. The default finds nothing, so a screen composed without a host (a frame) shows no detail.
 */
val LocalRequestDetail = staticCompositionLocalOf<@Composable (Int) -> RequestDetail?> { { null } }
