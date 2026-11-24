package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.res.Resources
import com.cooper.binge.companion.seerr.feature.discover.R

/** One way to watch a title, as the Watch chooser lists it. */
class WatchRow(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The Watch rows of a title in the library that has a link: Binge first where it answers the title link, then the media
 * server. Watch on the title page offers these where Ephyra does not answer. A title not in the library, or with no link, has none.
 */
fun titleWatchRows(
    state: TitleState,
    watchUrl: String?,
    mediaServerName: String?,
    onWatchInBinge: (() -> Unit)?,
    onWatchOnServer: (String) -> Unit,
    resources: Resources,
): List<WatchRow> {
    val url = watchUrl?.takeIf { state.isInLibrary() } ?: return emptyList()
    val serverLabel =
        mediaServerName?.let { resources.getString(R.string.title_sheet_watch_on, it) } ?: resources.getString(R.string.title_sheet_watch)
    return listOfNotNull(
        onWatchInBinge?.let { WatchRow(resources.getString(R.string.title_sheet_watch_in_binge), it) },
        WatchRow(serverLabel) { onWatchOnServer(url) },
    )
}
