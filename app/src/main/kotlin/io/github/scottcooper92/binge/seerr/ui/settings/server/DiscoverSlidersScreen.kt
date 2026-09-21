package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPageActionBar
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.LocalEditorPageInsets
import kotlinx.coroutines.flow.Flow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.binge.designsystem.R as DesR

/** What the slider list does beside the editor: reorder, switch, open a custom slider, and reset. */
class SlidersActions(
    val onMove: (from: Int, to: Int) -> Unit,
    val onToggle: (Int) -> Unit,
    val onOpenSlider: (Int?) -> Unit,
    val onReset: () -> Unit,
)

/**
 * The slider list in Discover's order: each with its switch and a drag handle to reorder, a custom
 * one opening its own page. Not part of [EditorPage]'s own scroll - the list scrolls itself so
 * [rememberReorderableLazyListState] can track item positions, hence `scrolling = false`.
 *
 * Add and Reset both need to stay reachable regardless of how far down a long list the user has
 * scrolled: Add is the page's persistent [EditorPage.bottomBar] rather than a floating action
 * button, which would drift over whichever row happens to scroll under it - the last visible row's
 * own switch, say. Reset - the one action here a mis-tap can't easily undo - sits behind the
 * overflow menu instead of beside Add.
 */
@Composable
fun DiscoverSlidersScreen(
    state: EditorUiState<List<DiscoverSlider>>,
    events: Flow<EditorEvent>,
    actions: EditorActions<List<DiscoverSlider>>,
    sliderActions: SlidersActions,
) {
    val ready = state as? EditorUiState.Ready<List<DiscoverSlider>>
    val actionsEnabled = ready?.saving == false
    var showOverflow by rememberSaveable { mutableStateOf(false) }
    var confirmingReset by rememberSaveable { mutableStateOf(false) }

    EditorPage(
        title = stringResource(R.string.server_settings_sliders),
        state = state,
        events = events,
        actions = actions,
        scrolling = false,
        bottomBar = {
            if (ready != null) {
                EditorPageActionBar(
                    label = stringResource(R.string.server_settings_slider_add),
                    onClick = { sliderActions.onOpenSlider(null) },
                    enabled = actionsEnabled,
                )
            }
        },
        extraActions = {
            if (ready != null) {
                IconButton(onClick = { showOverflow = true }, enabled = actionsEnabled) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.server_settings_sliders_more_actions))
                }
            }
        },
    ) { sliders, enabled ->
        val lazyListState = rememberLazyListState()
        val reorderableState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                // from.index/to.index are absolute LazyColumn positions, one ahead of `sliders`'
                // own indices because of the reorder-hint item below - shift both back before they
                // reach a viewmodel that indexes straight into `sliders`.
                sliderActions.onMove(from.index - 1, to.index - 1)
            }
        // The top/bottom bar insets EditorPage itself leaves unapplied for a scrolling = false page
        // (see LocalEditorPageInsets) - folded in here as contentPadding rather than a Modifier.padding
        // around the list, so a row can still scroll fully under both transparent bars instead of
        // stopping dead at their edge.
        val insets = LocalEditorPageInsets.current
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding =
                PaddingValues(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            item {
                Text(
                    text = stringResource(R.string.server_settings_sliders_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(sliders, key = { _, slider -> slider.id }) { index, slider ->
                ReorderableItem(reorderableState, key = slider.id) {
                    val interactionSource = remember { MutableInteractionSource() }
                    SliderRow(
                        slider = slider,
                        enabled = enabled,
                        actions = sliderActions,
                        index = index,
                        lastIndex = sliders.lastIndex,
                        // draggableHandle()/longPressDraggableHandle() are extensions on this
                        // ReorderableCollectionItemScope, unreachable from SliderRow itself.
                        handleModifier = Modifier.longPressDraggableHandle(enabled = enabled, interactionSource = interactionSource),
                    )
                }
            }
        }
    }

    if (showOverflow) {
        BingeBottomSheet(onDismissRequest = { showOverflow = false }) {
            SlidersOverflowRow(
                onClick = {
                    showOverflow = false
                    confirmingReset = true
                },
            )
        }
    }
    if (confirmingReset) {
        BingeConfirmDialog(
            title = stringResource(R.string.server_settings_sliders_reset_title),
            message = stringResource(R.string.server_settings_sliders_reset_message),
            confirmLabel = stringResource(R.string.server_settings_sliders_reset),
            destructive = true,
            onConfirm = {
                confirmingReset = false
                sliderActions.onReset()
            },
            onDismiss = { confirmingReset = false },
        )
    }
}

@Composable
private fun SlidersOverflowRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(onClick = onClick)
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.server_settings_sliders_reset), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SliderRow(
    slider: DiscoverSlider,
    enabled: Boolean,
    actions: SlidersActions,
    index: Int,
    lastIndex: Int,
    handleModifier: Modifier,
) {
    val editable = !slider.builtIn && slider.type != null
    val moveUpLabel = stringResource(R.string.server_settings_slider_move_up)
    val moveDownLabel = stringResource(R.string.server_settings_slider_move_down)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                // An editable row's own tap opens its editor - the more valuable action, and still
                // there via the switch itself. A built-in row has no editor to open, so the whole
                // row toggles it instead of leaving that to the small switch alone.
                .then(
                    if (editable) {
                        Modifier.clickable(enabled = enabled) { actions.onOpenSlider(slider.id) }
                    } else {
                        Modifier.toggleable(
                            value = slider.enabled,
                            enabled = enabled,
                            role = Role.Switch,
                        ) { actions.onToggle(slider.id) }
                    },
                )
                // The drag gesture has no TalkBack equivalent, so the row itself still offers Move
                // Up/Move Down as custom actions - the same pair the row used to expose as buttons,
                // just read from the screen reader's rotor instead of drawn on screen.
                .semantics {
                    customActions =
                        listOfNotNull(
                            if (index > 0) {
                                CustomAccessibilityAction(moveUpLabel) {
                                    actions.onMove(index, index - 1)
                                    true
                                }
                            } else {
                                null
                            },
                            if (index < lastIndex) {
                                CustomAccessibilityAction(moveDownLabel) {
                                    actions.onMove(index, index + 1)
                                    true
                                }
                            } else {
                                null
                            },
                        )
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(slider.label(), style = MaterialTheme.typography.bodyLarge)
            Text(slider.caption(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // clearAndSetSemantics: the handle's own node would otherwise announce as an unlabelled
        // button in the same row the Move Up/Move Down actions already cover.
        IconButton(
            onClick = {},
            enabled = enabled,
            modifier = handleModifier.clearAndSetSemantics {},
        ) {
            Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.server_settings_slider_reorder))
        }
        // null for a built-in row: the row's own toggleable above owns the tap and the semantics
        // for it, and a still-interactive switch nested inside would double both up for TalkBack.
        Switch(
            checked = slider.enabled,
            onCheckedChange =
                if (editable) {
                    { actions.onToggle(slider.id) }
                } else {
                    null
                },
            enabled = enabled,
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
