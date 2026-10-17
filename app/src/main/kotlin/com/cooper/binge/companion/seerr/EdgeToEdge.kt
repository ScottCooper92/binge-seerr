package com.cooper.binge.companion.seerr

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/**
 * Draws this Activity's window behind the status and navigation bars, as Binge's does. Every screen
 * then handles the bars' insets itself: a top bar takes the status bar, and a body pads for the
 * navigation bar.
 *
 * The bar icons follow the system's dark mode, as the theme does. The three-button navigation scrim
 * is turned off (API 29+), so a list scrolling behind the bar shows through it. A TV has no bars.
 */
internal fun ComponentActivity.drawEdgeToEdge() {
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}
