package io.github.scottcooper92.binge.seerr

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/**
 * Draws this Activity's window behind the status and navigation bars, as Binge's does. Every screen
 * then handles the bars' insets itself: a top bar takes the status bar, and a body pads for the
 * navigation bar where its last row or its pinned bar would otherwise sit under it.
 *
 * The bar icons follow the system's dark mode, which is what the theme follows too. The contrast
 * scrim the system lays over three-button navigation is turned off (API 29+), so a list that scrolls
 * behind the navigation bar shows through it rather than under a translucent band.
 *
 * A television has no system bars, so there this changes nothing.
 */
internal fun ComponentActivity.drawEdgeToEdge() {
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}
