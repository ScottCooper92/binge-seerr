package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvMessagePlate
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.RequestStateTone

/** How many items a board's row shows before its see-all tile takes over. */
internal const val TV_ROW_ITEM_CAP = 20

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
