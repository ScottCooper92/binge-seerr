package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeStatusButton
import com.binge.designsystem.component.BingeStatusButtonEmphasis
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.ui.requests.OpenTarget
import com.cooper.binge.companion.seerr.ui.requests.ServiceItem
import com.cooper.binge.companion.seerr.ui.requests.ServiceKind
import com.cooper.binge.companion.seerr.ui.requests.canResolve
import com.cooper.binge.companion.seerr.ui.requests.mediaServerBaseUrl
import com.cooper.binge.companion.seerr.ui.requests.openServiceApp
import com.cooper.binge.companion.seerr.ui.requests.openTarget
import com.cooper.binge.companion.seerr.ui.requests.toServiceKind

/**
 * How a title in the library is watched: in the media server's own app where one is installed and answers, otherwise on the
 * server's web page. A title that is not in the library, or has neither, has no Watch and the page shows its status instead.
 */
class TitleWatch(
    val target: OpenTarget,
)

/** The title's Watch, or null where it is not in the library or there is nowhere to send it, which keeps the status primary. */
@Composable
fun rememberTitleWatch(
    state: TitleState,
    watchUrl: String?,
    mediaServer: SeerrMediaServer?,
    services: List<ServiceGroup>,
    kind: TitleKind,
    tmdbId: Int,
): TitleWatch? {
    if (!state.isInLibrary()) return null
    val context = LocalContext.current
    val fromServices =
        services
            .firstOrNull { it.kind.isMediaServer }
            ?.rows
            ?.filterIsInstance<ServiceRow.Open>()
            ?.firstOrNull()
            ?.target
    // Without a request's page to name the item, the app is asked with the server's link where Seerr gives one, and by TMDB id alone where not.
    val target =
        fromServices ?: remember(watchUrl, kind, tmdbId, mediaServer) {
            (mediaServer?.toServiceKind() ?: ServiceKind.Plex).openTarget(
                ServiceItem(
                    serverUrl = watchUrl?.let(::mediaServerBaseUrl),
                    itemId = watchUrl?.let(::itemIdOf),
                    tmdbType = if (kind == TitleKind.Series) "tv" else "movie",
                    tmdbId = tmdbId,
                ),
                webUrl = watchUrl,
            ) { context.canResolve(it) }
        }
    return target?.let(::TitleWatch)
}

/** The media server's item id in a Jellyfin or Emby web link (`…/web/#/details?id=…`), where it has one. */
fun itemIdOf(url: String): String? =
    Regex("[?&]id=([^&#]+)")
        .find(url)
        ?.groupValues
        ?.get(1)
        ?.let(Uri::decode)

/** Opens the title: in the app where there is one, otherwise the server's web page through [openUrl]. */
fun TitleWatch.play(
    context: Context,
    openUrl: (String) -> Unit,
) = when (target) {
    is OpenTarget.App -> context.openServiceApp(target)
    is OpenTarget.Web -> openUrl(target.url)
}

/** The primary once the title is in the library and has somewhere to open: Watch, filled, with its play icon. */
@Composable
fun WatchPrimary(
    onClick: () -> Unit,
    modifier: Modifier,
) {
    BingeStatusButton(
        label = stringResource(R.string.title_sheet_watch),
        onClick = onClick,
        sentiment = BingeSentiment.Neutral,
        modifier = modifier.fillMaxWidth(),
        icon = Icons.Filled.PlayArrow,
        emphasis = BingeStatusButtonEmphasis.Filled,
    )
}
