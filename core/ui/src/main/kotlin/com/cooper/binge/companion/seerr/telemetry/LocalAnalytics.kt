package com.cooper.binge.companion.seerr.telemetry

import androidx.compose.runtime.staticCompositionLocalOf
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics

/** The app's [Analytics], for the screens and the television shell, which have no view model to hand it to. Silent by default. */
val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoOpAnalytics }
