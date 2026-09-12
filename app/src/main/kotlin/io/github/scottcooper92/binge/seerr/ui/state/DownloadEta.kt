package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import io.github.scottcooper92.binge.seerr.R
import java.time.Duration

private const val HALF_HOUR_MINUTES = 30L

/** "About 12 min left", switching to whole hours ("About 2 hr left") from an hour up. */
@Composable
fun downloadEtaLabel(etaMinutes: Int): String {
    val eta = Duration.ofMinutes(etaMinutes.toLong())
    return if (eta >= Duration.ofHours(1)) {
        val hours = eta.plusMinutes(HALF_HOUR_MINUTES).toHours().toInt()
        pluralStringResource(R.plurals.download_eta_hours, hours, hours)
    } else {
        pluralStringResource(R.plurals.download_eta_minutes, etaMinutes, etaMinutes)
    }
}
