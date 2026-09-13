package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvMessagePlate
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.TvStableFocusScroll
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import com.binge.designsystem.tv.focus.tvFocusGroup
import com.binge.designsystem.tv.nav.LocalTvContentInset
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

private const val TRANSIENT_MESSAGE_MILLIS = 4_000L

/**
 * A rail destination's frame: the theme background, the rail cleared on the start edge through
 * [LocalTvContentInset], overscan on the other three, and the board's title on the rail's top line.
 */
@Composable
internal fun TvBoardFrame(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val verticalInset = dimensionResource(TvR.dimen.tv_overscan_vertical)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    start = LocalTvContentInset.current + dimensionResource(TvR.dimen.tv_content_gutter_start),
                    end = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                    top = verticalInset,
                    bottom = verticalInset,
                ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_board_section_gap)),
    ) {
        TvBoardTitle(title)
        content()
    }
}

/** The title band, as tall as the rail's own top item so the two sit on one line. */
@Composable
internal fun TvBoardTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    val bandHeight = dimensionResource(TvR.dimen.tv_nav_rail_item_height)
    Box(modifier = modifier.height(bandHeight), contentAlignment = Alignment.CenterStart) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The rule between a board's two panes. */
@Composable
internal fun TvBoardDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(dimensionResource(DesR.dimen.hairline_thickness))
                .background(MaterialTheme.colorScheme.border),
    )
}

/**
 * A row of mutually exclusive filters where OK is the commit: passing over one changes nothing, since
 * changing a filter replaces the list beneath. [initialFocusedLabel] seeds a pill for a preview.
 */
@Composable
internal fun <T> TvFilterBand(
    filters: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    initialFocusedLabel: String? = null,
    arrival: TvArrivalFocus? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_filter_band_gap)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEachIndexed { index, (key, label) ->
            val isSelected = key == selected
            TvChoicePill(
                label = label,
                selected = isSelected,
                onSelect = { if (!isSelected) onSelect(key) },
                initiallyFocused = label == initialFocusedLabel,
                modifier = if (index == 0 && arrival != null) Modifier.tvArrivalTarget(arrival) else Modifier,
            )
        }
    }
}

/** One filter: focus fills the pill amber, the chosen one carries the amber label at rest. */
@Composable
internal fun TvChoicePill(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyFocused: Boolean = false,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val resting = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            modifier
                .height(dimensionResource(R.dimen.tv_form_option_height))
                .clip(BingeShapes.Pill)
                .background(MaterialTheme.colorScheme.surface, BingeShapes.Pill)
                .tvFocusFill(isFocused = focused, shape = BingeShapes.Pill)
                .tvClickable(onFocusChanged = { focused = it }, onClick = onSelect)
                .padding(horizontal = dimensionResource(R.dimen.tv_form_option_padding_horizontal))
                .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = tvFocusContentColor(isFocused = focused, resting = resting),
            maxLines = 1,
        )
    }
}

/** A board's whole-content message — the first load, its failure, or an empty list — with up to two ways out. */
@Composable
internal fun TvBoardPlate(
    body: String,
    modifier: Modifier = Modifier,
    headline: String? = null,
    icon: ImageVector? = null,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
    arrival: TvArrivalFocus? = null,
) {
    TvMessagePlate(
        body = body,
        headline = headline,
        icon = icon,
        alignment = Alignment.Center,
        modifier = modifier,
        actions =
            if (primary == null && secondary == null) {
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
                        secondary?.let { (label, onClick) ->
                            TvButton(
                                label = label,
                                onClick = onClick,
                                modifier = if (primary == null && arrival != null) Modifier.tvArrivalTarget(arrival) else Modifier,
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

    data class Failed(
        val rejected: Boolean,
    ) : TvLoadPhase
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

/**
 * A paged list's rows with the states the pager reports: the rows once there are any, with the next page's
 * state as a footer; the whole-content plates for the first load, its failure, and an empty result.
 */
@Composable
internal fun <T> TvPagedList(
    rows: TvPagedRows<T>,
    emptyMessage: String,
    onRetryLoad: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    val refresh = rows.refresh
    when {
        rows.count > 0 ->
            TvStableFocusScroll {
                LazyColumn(
                    modifier = modifier.fillMaxSize().tvFocusGroup(),
                    contentPadding = PaddingValues(vertical = dimensionResource(TvR.dimen.tv_focus_ring_bleed)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_list_row_gap)),
                ) {
                    items(count = rows.count, key = rows.itemKey) { index -> rows.at(index)?.let { row(it) } }
                    item { TvAppendFooter(rows.append, onRetryLoad, onReconnect) }
                }
            }
        refresh is TvLoadPhase.Loading -> TvBoardPlate(body = stringResource(R.string.tv_loading), modifier = modifier)
        refresh is TvLoadPhase.Failed ->
            TvBoardPlate(
                body = stringResource(if (refresh.rejected) R.string.requests_reconnect else R.string.tv_list_load_failed),
                icon = Icons.Filled.Warning,
                primary =
                    if (refresh.rejected) {
                        stringResource(R.string.tv_hub_reconnect) to onReconnect
                    } else {
                        stringResource(R.string.hub_retry) to onRetryLoad
                    },
                modifier = modifier,
            )
        else -> TvBoardPlate(body = emptyMessage, icon = Icons.Filled.Inbox, modifier = modifier)
    }
}

/** Under a list that is showing: a line while the next page loads, or a button to retry it. */
@Composable
private fun TvAppendFooter(
    state: TvLoadPhase,
    onRetryLoad: () -> Unit,
    onReconnect: () -> Unit,
) {
    when (state) {
        TvLoadPhase.Idle -> Unit
        TvLoadPhase.Loading ->
            Text(
                text = stringResource(R.string.tv_list_loading_more),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = dimensionResource(DesR.dimen.padding_m)),
            )
        is TvLoadPhase.Failed ->
            TvButton(
                label = stringResource(if (state.rejected) R.string.tv_hub_reconnect else R.string.tv_list_load_more_failed),
                onClick = if (state.rejected) onReconnect else onRetryLoad,
                modifier = Modifier.padding(vertical = dimensionResource(DesR.dimen.padding_m)),
            )
    }
}
