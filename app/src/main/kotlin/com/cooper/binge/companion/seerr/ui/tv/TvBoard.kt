package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.binge.designsystem.tv.component.TvMessagePlate
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.RequestStateTone

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
    val restore = rememberSheetFocusReturn()
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen && restore.pending) restoreTvOverlayFocus(restore.requester)
    }
    return restore
}

/**
 * The restore state for a board that only puts focus back from a sheet of its own, through the sheet's closer. A page
 * the shell shows above the rail is not one of those: when it closes, the shell hands focus back into the destination
 * and the board's own entry routing lands it. A second request from the board races that hand-off, and the loser is
 * the opened card (#1509).
 */
@Composable
internal fun rememberSheetFocusReturn(): OverlayFocusRestore {
    val requester = remember { FocusRequester() }
    val rowId = rememberSaveable { mutableStateOf<Int?>(null) }
    val seeAllKey = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { OverlayFocusRestore(requester, rowId, seeAllKey) }
}

/** A board's whole-content message: the first load, or an empty list. A failure is [TvErrorPlate][com.binge.designsystem.tv.component.TvErrorPlate]'s. */
@Composable
internal fun TvBoardPlate(
    body: String,
    modifier: Modifier = Modifier,
    headline: String? = null,
    icon: ImageVector? = null,
) {
    TvMessagePlate(
        body = body,
        headline = headline,
        icon = icon,
        alignment = Alignment.Center,
        modifier = modifier,
    )
}

/** Where a paged list is in a load, as the boards read it off the pager. */
internal sealed interface TvLoadPhase {
    data object Idle : TvLoadPhase

    data object Loading : TvLoadPhase

    /** Any failure, a rejected session included: the app-wide gate, not the list, moves a rejected session to sign-in. */
    data object Failed : TvLoadPhase

    /** The server is on the local network and Android has not let this app reach it: a failure that only the permission fixes (#1507). */
    data object Denied : TvLoadPhase
}

/** Whether the load failed, for whatever reason: [TvLoadPhase.Failed] or [TvLoadPhase.Denied]. */
internal val TvLoadPhase.isFailure: Boolean get() = this == TvLoadPhase.Failed || this == TvLoadPhase.Denied

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
