package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeMessageDialog
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeStatusButton
import com.binge.designsystem.component.BingeStatusButtonEmphasis
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.ui.requests.OpenTarget
import com.cooper.binge.companion.seerr.ui.requests.ServiceItem
import com.cooper.binge.companion.seerr.ui.requests.ServiceKind
import com.cooper.binge.companion.seerr.ui.requests.canResolve
import com.cooper.binge.companion.seerr.ui.requests.mediaServerBaseUrl
import com.cooper.binge.companion.seerr.ui.requests.openServiceApp
import com.cooper.binge.companion.seerr.ui.requests.openTarget
import com.cooper.binge.companion.seerr.ui.state.externalItem
import com.binge.designsystem.R as DesR

/**
 * How a title in the library is watched: in Ephyra where it is installed and answers, otherwise the other ways there are. One way
 * opens at once, several are offered in a chooser, and none at all is a dialog saying why. A default the user picks is #2755.
 */
internal class TitleWatch(
    val ephyra: OpenTarget.App?,
    val options: List<WatchRow>,
)

/** The title's ways to watch, or null where it is not in the library, which keeps the status primary. */
@Composable
internal fun rememberTitleWatch(
    state: TitleState,
    watchUrl: String?,
    mediaServerName: String?,
    services: List<ServiceGroup>,
    kind: TitleKind,
    tmdbId: Int,
    onWatchInBinge: (() -> Unit)?,
    onOpenUrl: (String) -> Unit,
): TitleWatch? {
    if (!state.isInLibrary()) return null
    val context = LocalContext.current
    val resources = LocalResources.current
    val fromServices =
        services
            .firstOrNull { it.kind.isMediaServer }
            ?.rows
            ?.filterIsInstance<ServiceRow.Open>()
            ?.firstNotNullOfOrNull { it.target as? OpenTarget.App }
    // Without a request's page to name the item, Ephyra is asked with the server's link where Seerr gives one, and by TMDB id alone where not.
    val ephyra =
        fromServices ?: remember(watchUrl, kind, tmdbId) {
            ServiceKind.Jellyfin.openTarget(
                ServiceItem(
                    serverUrl = watchUrl?.let(::mediaServerBaseUrl),
                    itemId = watchUrl?.let(::itemIdOf),
                    tmdbType = if (kind == TitleKind.Series) "tv" else "movie",
                    tmdbId = tmdbId,
                ),
                webUrl = null,
            ) { context.canResolve(it) } as? OpenTarget.App
        }
    val options = titleWatchRows(state, watchUrl, mediaServerName, onWatchInBinge, onOpenUrl, resources)
    return TitleWatch(ephyra, options)
}

/** The media server's item id in a Jellyfin or Emby web link (`…/web/#/details?id=…`), where it has one. */
internal fun itemIdOf(url: String): String? =
    Regex("[?&]id=([^&#]+)")
        .find(url)
        ?.groupValues
        ?.get(1)
        ?.let(Uri::decode)

/** Opens Ephyra, or the one way there is, or calls [onChoose] for the chooser or the dialog. */
internal fun TitleWatch.play(
    context: Context,
    onChoose: () -> Unit,
) {
    when {
        ephyra != null -> context.openServiceApp(ephyra)
        options.size == 1 -> options.first().onClick()
        else -> onChoose()
    }
}

/** The primary once the title is in the library: Watch, filled, with its play icon. */
@Composable
internal fun WatchPrimary(
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

/** What Watch shows when it cannot open one way at once: the chooser, or a dialog where nothing on the phone can play the title. */
@Composable
internal fun WatchChoice(
    watch: TitleWatch,
    onDismiss: () -> Unit,
) {
    if (watch.options.isEmpty()) {
        BingeMessageDialog(
            title = stringResource(R.string.title_page_watch_none_title),
            message = stringResource(R.string.title_page_watch_none_body),
            onDismiss = onDismiss,
        )
        return
    }
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        ) {
            ItemGroup(
                title = stringResource(R.string.title_page_watch_chooser),
                rows =
                    watch.options.map { option ->
                        externalItem(Icons.Filled.PlayArrow, option.label) {
                            onDismiss()
                            option.onClick()
                        }
                    },
            )
        }
    }
}
