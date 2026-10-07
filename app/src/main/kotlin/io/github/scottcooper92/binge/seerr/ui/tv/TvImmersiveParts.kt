package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.BingeTheme
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusIndicator
import com.binge.designsystem.tv.template.TvImmersiveGrid
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageAction
import io.github.scottcooper92.binge.seerr.R

private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val CHIP_SCRIM_ALPHA = 0.7f

/** A poster as a focusable card for an immersive hub or grid; the backdrop names it, so there is no caption. */
@Composable
internal fun TvPosterCard(
    title: String,
    posterUrl: String?,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(POSTER_ASPECT_RATIO)
                .tvFocusIndicator(isFocused = isFocused, shape = BingeShapes.MediaCard)
                .clip(BingeShapes.MediaCard)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = title }
                .tvClickable(onFocusChanged = onFocusChanged, enabled = enabled, onClick = onClick),
    ) {
        posterUrl?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        badge?.invoke(this)
    }
}

/**
 * A small round icon chip for the corner of a poster — a state in a glyph, over a scrim so it reads on any artwork.
 * Place it in [TvPosterCard]'s `badge` slot.
 */
@Composable
internal fun BoxScope.TvPosterIconChip(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(dimensionResource(R.dimen.tv_poster_chip_inset))
                .size(dimensionResource(R.dimen.tv_poster_chip_size))
                .clip(CircleShape)
                .background(BingeTheme.colors.scrim.copy(alpha = CHIP_SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(dimensionResource(R.dimen.tv_poster_chip_icon)),
        )
    }
}

/** The focused title's backdrop, falling back to its poster, then a flat plate. */
@Composable
internal fun TvBackdropArtwork(
    backdropUrl: String?,
    posterUrl: String?,
) {
    val url = backdropUrl ?: posterUrl
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The backdrop's text band: a meta line, the title, a coloured status line and an optional synopsis. */
@Composable
internal fun ColumnScope.TvBackdropCopy(
    meta: String,
    title: String,
    status: String,
    statusColor: Color,
    synopsis: String?,
) {
    Text(
        text = meta,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = status,
        style = MaterialTheme.typography.titleMedium,
        color = statusColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    synopsis?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What an immersive board shows when no row has anything: loading, why it could not load (with the way out), or
 * that there is nothing yet. [rows] are every row's pager, in any order. A whole-page message, so it holds focus
 * itself: the page it hands over to, when rows arrive, is placed by the shell.
 */
@Composable
internal fun TvRowsFallback(
    rows: List<TvPagedRows<*>>,
    emptyBody: String,
    onRetryLoad: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = rows.firstNotNullOfOrNull { it.refresh as? TvLoadPhase.Failed }
    val loading = rows.any { it.refresh is TvLoadPhase.Loading }
    when {
        loading -> TvMessagePage(body = stringResource(R.string.tv_loading), modifier = modifier, loading = true)
        failed != null ->
            TvMessagePage(
                body = stringResource(if (failed.rejected) R.string.requests_reconnect else R.string.tv_list_load_failed),
                modifier = modifier,
                icon = Icons.Filled.Warning,
                primary =
                    if (failed.rejected) {
                        TvPageAction(stringResource(R.string.tv_hub_reconnect), onReconnect)
                    } else {
                        TvPageAction(stringResource(R.string.hub_retry), onRetryLoad)
                    },
            )
        else -> TvMessagePage(body = emptyBody, modifier = modifier, icon = Icons.Filled.Inbox)
    }
}

/**
 * A see-all destination: every item behind a hub row, as a paged grid under the same backdrop, above the rail.
 * Back closes it through [onBack]. [detailOpen] says a page opened from one of its cards is showing above it, so
 * that focus returns to the card the moment that page closes.
 */
@Composable
internal fun <T : Any> TvPagedGridScreen(
    heading: String,
    rows: TvPagedRows<T>,
    emptyBody: String,
    onRetryLoad: () -> Unit,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
    detailOpen: Boolean,
    artwork: @Composable (T) -> Unit,
    copy: @Composable ColumnScope.(T) -> Unit,
    cell: @Composable (item: T, isFocused: Boolean, onFocusChanged: (Boolean) -> Unit, cellModifier: Modifier) -> Unit,
) {
    BackHandler(onBack = onBack)
    val restoreFocus = remember { FocusRequester() }
    var detailWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(detailOpen) {
        if (detailOpen) {
            detailWasOpen = true
        } else if (detailWasOpen) {
            detailWasOpen = false
            restoreTvOverlayFocus(restoreFocus)
        }
    }
    if (rows.count > 0) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TvImmersiveGrid(
                heading = heading,
                count = rows.count,
                itemAt = rows.at,
                itemKey = rows.itemKey,
                artwork = artwork,
                copy = copy,
                rememberedCellModifier = Modifier.focusRequester(restoreFocus),
                cell = cell,
            )
        }
    } else {
        TvRowsFallback(
            rows = listOf(rows),
            emptyBody = emptyBody,
            onRetryLoad = onRetryLoad,
            onReconnect = onReconnect,
        )
    }
}
