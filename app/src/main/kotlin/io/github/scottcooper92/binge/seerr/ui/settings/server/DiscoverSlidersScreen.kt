package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/** What the slider list does beside the editor: reorder, switch, open a custom slider, and reset. */
class SlidersActions(
    val onMove: (id: Int, up: Boolean) -> Unit,
    val onToggle: (Int) -> Unit,
    val onOpenSlider: (Int?) -> Unit,
    val onReset: () -> Unit,
)

/**
 * The slider list in Discover's order: each with its switch and a move up or down, a custom one
 * opening its own page; then a way to add one, and the reset behind a confirmation.
 */
@Composable
fun DiscoverSlidersScreen(
    state: EditorUiState<List<DiscoverSlider>>,
    events: Flow<EditorEvent>,
    actions: EditorActions<List<DiscoverSlider>>,
    sliderActions: SlidersActions,
) {
    EditorPage(
        title = stringResource(R.string.server_settings_sliders),
        state = state,
        events = events,
        actions = actions,
    ) { sliders, enabled ->
        sliders.forEachIndexed { index, slider ->
            SliderRow(
                slider = slider,
                first = index == 0,
                last = index == sliders.lastIndex,
                enabled = enabled,
                actions = sliderActions,
            )
        }
        BingeOutlinedButton(
            label = stringResource(R.string.server_settings_slider_add),
            onClick = { sliderActions.onOpenSlider(null) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        ResetButton(enabled = enabled, onReset = sliderActions.onReset)
    }
}

@Composable
private fun SliderRow(
    slider: DiscoverSlider,
    first: Boolean,
    last: Boolean,
    enabled: Boolean,
    actions: SlidersActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .then(if (slider.builtIn) Modifier else Modifier.clickable(enabled = enabled) { actions.onOpenSlider(slider.id) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(slider.label(), style = MaterialTheme.typography.bodyLarge)
            Text(slider.caption(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { actions.onMove(slider.id, true) }, enabled = enabled && !first) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.server_settings_slider_move_up))
        }
        IconButton(onClick = { actions.onMove(slider.id, false) }, enabled = enabled && !last) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.server_settings_slider_move_down))
        }
        Switch(checked = slider.enabled, onCheckedChange = { actions.onToggle(slider.id) }, enabled = enabled)
    }
}

@Composable
private fun ResetButton(
    enabled: Boolean,
    onReset: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    BingeOutlinedButton(
        label = stringResource(R.string.server_settings_sliders_reset),
        onClick = { confirming = true },
        enabled = enabled,
        contentColor = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(R.string.server_settings_sliders_reset_title),
            message = stringResource(R.string.server_settings_sliders_reset_message),
            confirmLabel = stringResource(R.string.server_settings_sliders_reset),
            destructive = true,
            onConfirm = {
                confirming = false
                onReset()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** A built-in slider is named by its kind; a custom one by its title, with the kind beneath. */
@Composable
internal fun DiscoverSlider.label(): String =
    title ?: type?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.server_settings_slider_unknown_type, typeCode)

@Composable
private fun DiscoverSlider.caption(): String =
    if (builtIn) {
        stringResource(R.string.server_settings_slider_built_in)
    } else {
        listOfNotNull(type?.let { stringResource(it.labelRes()) }, data?.takeIf { it.isNotBlank() })
            .joinToString(stringResource(R.string.hub_meta_separator))
    }

@StringRes
internal fun SliderType.labelRes(): Int =
    when (this) {
        SliderType.RecentlyAdded -> R.string.server_settings_slider_recently_added
        SliderType.RecentRequests -> R.string.server_settings_slider_recent_requests
        SliderType.Watchlist -> R.string.server_settings_slider_watchlist
        SliderType.Trending -> R.string.server_settings_slider_trending
        SliderType.PopularMovies -> R.string.server_settings_slider_popular_movies
        SliderType.MovieGenres -> R.string.server_settings_slider_movie_genres
        SliderType.UpcomingMovies -> R.string.server_settings_slider_upcoming_movies
        SliderType.Studios -> R.string.server_settings_slider_studios
        SliderType.PopularTv -> R.string.server_settings_slider_popular_tv
        SliderType.TvGenres -> R.string.server_settings_slider_tv_genres
        SliderType.UpcomingTv -> R.string.server_settings_slider_upcoming_tv
        SliderType.Networks -> R.string.server_settings_slider_networks
        SliderType.MovieKeyword -> R.string.server_settings_slider_movie_keyword
        SliderType.TvKeyword -> R.string.server_settings_slider_tv_keyword
        SliderType.MovieGenre -> R.string.server_settings_slider_movie_genre
        SliderType.TvGenre -> R.string.server_settings_slider_tv_genre
        SliderType.Studio -> R.string.server_settings_slider_studio
        SliderType.Network -> R.string.server_settings_slider_network
        SliderType.Search -> R.string.server_settings_slider_search
        SliderType.MovieStreamingServices -> R.string.server_settings_slider_movie_streaming
        SliderType.TvStreamingServices -> R.string.server_settings_slider_tv_streaming
    }

/** What a custom kind's data holds, in the shape the web client stores it. */
@StringRes
internal fun SliderType.dataHintRes(): Int =
    when (this) {
        SliderType.MovieKeyword, SliderType.TvKeyword -> R.string.server_settings_slider_data_keywords
        SliderType.MovieGenre, SliderType.TvGenre -> R.string.server_settings_slider_data_genre
        SliderType.Studio, SliderType.Network -> R.string.server_settings_slider_data_company
        SliderType.Search -> R.string.server_settings_slider_data_search
        SliderType.MovieStreamingServices, SliderType.TvStreamingServices -> R.string.server_settings_slider_data_streaming
        else -> R.string.server_settings_slider_data_generic
    }
