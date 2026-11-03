package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError

/**
 * How one of the about page's best-effort reads went. Not being allowed to read, or the server having no such
 * endpoint, is [Unavailable] and shows nothing. A read that failed is [Failed] when there is nothing earlier to show
 * and [Stale] when the last values are kept, so the page can word the two differently.
 */
enum class AboutRead { Read, Unavailable, Failed, Stale }

/** [Read] for an answer; [Unavailable] where the server refuses this user or has no such endpoint; otherwise [Failed]. */
internal fun <T> Result<T>.toAboutRead(): AboutRead =
    fold(
        onSuccess = { AboutRead.Read },
        onFailure = {
            when (it.toSeerrError()) {
                SeerrError.Forbidden, SeerrError.NotFound -> AboutRead.Unavailable
                else -> AboutRead.Failed
            }
        },
    )

/**
 * This read, with the values [earlier] showed for each source that failed this time, marked [AboutRead.Stale]. With
 * nothing earlier to keep, a failed source stays [AboutRead.Failed].
 */
internal fun AboutInfo.keepingEarlier(earlier: AboutInfo?): AboutInfo {
    if (earlier == null) return this
    val keepTotals = aboutRead == AboutRead.Failed && earlier.aboutRead.hasValues()
    val keepAppData = appDataRead == AboutRead.Failed && earlier.appDataRead.hasValues()
    return copy(
        totalRequests = if (keepTotals) earlier.totalRequests else totalRequests,
        totalMediaItems = if (keepTotals) earlier.totalMediaItems else totalMediaItems,
        timezone = if (keepTotals) earlier.timezone else timezone,
        aboutRead = if (keepTotals) AboutRead.Stale else aboutRead,
        appDataPath = if (keepAppData) earlier.appDataPath else appDataPath,
        appDataMounted = if (keepAppData) earlier.appDataMounted else appDataMounted,
        appDataWritable = if (keepAppData) earlier.appDataWritable else appDataWritable,
        appDataRead = if (keepAppData) AboutRead.Stale else appDataRead,
    )
}

private fun AboutRead.hasValues() = this == AboutRead.Read || this == AboutRead.Stale
