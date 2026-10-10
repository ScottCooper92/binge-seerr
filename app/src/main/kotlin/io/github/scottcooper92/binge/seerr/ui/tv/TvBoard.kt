package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvMessagePlate
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

private const val TRANSIENT_MESSAGE_MILLIS = 4_000L

/** How many items a board's row shows before its see-all tile takes over. */
internal const val TV_ROW_ITEM_CAP = 20

/**
 * Where focus goes back to when a sheet or a page opened from a board closes: the row item that opened it, or the see-all
 * tile whose grid it was. It is pinned to the row, not to the open action item or id, because by the time a closer
 * requests the return the source it carried is already null, and the requester must still be attached somewhere.
 * Mark the item with [rowModifier] and the tile with [seeAllModifier], and call [leavingFromRow] or [leavingFromSeeAll]
 * as the overlay opens.
 */
@Stable
internal class OverlayFocusRestore(
    val requester: FocusRequester,
    private val rowId: MutableState<Int?>,
    private val seeAllKey: MutableState<String?>,
) {
    val pending: Boolean get() = rowId.value != null || seeAllKey.value != null

    fun leavingFromRow(id: Int) {
        rowId.value = id
        seeAllKey.value = null
    }

    fun leavingFromSeeAll(key: String) {
        seeAllKey.value = key
        rowId.value = null
    }

    fun rowModifier(
        id: Int,
        base: Modifier,
    ): Modifier = if (id == rowId.value) base.focusRequester(requester) else base

    fun seeAllModifier(key: String): Modifier = if (key == seeAllKey.value) Modifier.focusRequester(requester) else Modifier
}

/** The restore state for one board; it puts focus back once [overlayOpen] goes false, and survives a rotation. */
@Composable
internal fun rememberOverlayFocusRestore(overlayOpen: Boolean): OverlayFocusRestore {
    val requester = remember { FocusRequester() }
    val rowId = rememberSaveable { mutableStateOf<Int?>(null) }
    val seeAllKey = rememberSaveable { mutableStateOf<String?>(null) }
    val restore = remember { OverlayFocusRestore(requester, rowId, seeAllKey) }
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen && restore.pending) restoreTvOverlayFocus(requester)
    }
    return restore
}

/** A board's whole-content message — the first load, its failure, or an empty list — with up to three ways out. */
@Composable
internal fun TvBoardPlate(
    body: String,
    modifier: Modifier = Modifier,
    headline: String? = null,
    icon: ImageVector? = null,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
    arrival: TvArrivalFocus? = null,
    alternate: Pair<String, () -> Unit>? = null,
) {
    TvMessagePlate(
        body = body,
        headline = headline,
        icon = icon,
        alignment = Alignment.Center,
        modifier = modifier,
        actions =
            if (primary == null && alternate == null && secondary == null) {
                null
            } else {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                        primary?.let { (label, onClick) ->
                            TvButton(
                                label = label,
                                onClick = onClick,
                                style = TvButtonStyle.Primary,
                                modifier = arrival?.let { Modifier.tvArrivalTarget(it) } ?: Modifier,
                            )
                        }
                        alternate?.let { (label, onClick) ->
                            TvButton(
                                label = label,
                                onClick = onClick,
                                modifier = if (primary == null && arrival != null) Modifier.tvArrivalTarget(arrival) else Modifier,
                            )
                        }
                        secondary?.let { (label, onClick) ->
                            TvButton(
                                label = label,
                                onClick = onClick,
                                modifier =
                                    if (primary == null &&
                                        alternate == null &&
                                        arrival != null
                                    ) {
                                        Modifier.tvArrivalTarget(arrival)
                                    } else {
                                        Modifier
                                    },
                            )
                        }
                    }
                }
            },
    )
}

/** Where a paged list is in a load, as the boards read it off the pager. */
internal sealed interface TvLoadPhase {
    data object Idle : TvLoadPhase

    data object Loading : TvLoadPhase

    /** Any failure, a rejected session included: the app-wide gate, not the list, moves a rejected session to sign-in. */
    data object Failed : TvLoadPhase
}

/**
 * A paged list decomposed into a count and an accessor, so a board is reachable from a plain JVM test:
 * `collectAsLazyPagingItems` does not progress under a Compose test rule, and the accessor form is
 * what the thin entry wrapper supplies from the real pager.
 */
internal class TvPagedRows<T>(
    val count: Int,
    val at: (Int) -> T?,
    /** A stable key per index, read without paging the row in; the index itself where nothing better exists. */
    val itemKey: (Int) -> Any = { it },
    val refresh: TvLoadPhase = TvLoadPhase.Idle,
    val append: TvLoadPhase = TvLoadPhase.Idle,
)

/** A poster where the row has one, and the plate it would sit on where it does not. */
@Composable
internal fun TvPoster(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val shape = BingeShapes.ElementSmall
    Box(
        modifier =
            modifier
                .size(
                    width = dimensionResource(R.dimen.tv_list_row_poster_width),
                    height = dimensionResource(R.dimen.tv_list_row_poster_height),
                ).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A request or issue state, as a colour the TV theme has: what needs a hand is tertiary, what went wrong is error. */
@Composable
internal fun RequestStateTone.tvColor(): Color =
    when (this) {
        RequestStateTone.Pending -> MaterialTheme.colorScheme.tertiary
        RequestStateTone.Active -> MaterialTheme.colorScheme.primary
        RequestStateTone.Success -> MaterialTheme.colorScheme.onSurface
        RequestStateTone.Declined, RequestStateTone.Blocked -> MaterialTheme.colorScheme.error
    }

/**
 * The latest one-shot event, held for a few seconds and then cleared — the board's answer to the phone's
 * snackbar. A newer event supersedes the one still showing.
 */
@Composable
internal fun <T> rememberTvTransientEvent(events: Flow<T>): T? {
    var current by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(events) {
        events.collectLatest { event ->
            current = event
            delay(TRANSIENT_MESSAGE_MILLIS)
            current = null
        }
    }
    return current
}
