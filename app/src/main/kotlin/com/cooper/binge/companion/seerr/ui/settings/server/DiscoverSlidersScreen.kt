package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewCarousel
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.HintCard
import com.binge.designsystem.component.ReorderableHandleRow
import com.binge.designsystem.component.bingeSwitchColors
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.settings.Regions
import com.cooper.binge.companion.seerr.ui.state.ActionSheetGroup
import com.cooper.binge.companion.seerr.ui.state.actionItem
import com.cooper.binge.companion.seerr.ui.users.settings.EditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.EditorPage
import com.cooper.binge.companion.seerr.ui.users.settings.EditorPageActionBar
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.LocalEditorPageInsets
import com.cooper.binge.companion.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow
import sh.calvin.reorderable.ReorderableCollectionItemScope
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
    state: ExtrasEditorUiState<List<DiscoverSlider>, SliderNames>,
    events: Flow<EditorEvent>,
    actions: EditorActions<List<DiscoverSlider>>,
    sliderActions: SlidersActions,
) {
    val ready = state as? ExtrasEditorUiState.Ready<List<DiscoverSlider>, SliderNames>
    val names = ready?.extras ?: SliderNames()
    val actionsEnabled = ready?.saving == false
    var showOverflow by rememberSaveable { mutableStateOf(false) }
    var confirmingReset by rememberSaveable { mutableStateOf(false) }

    EditorPage(
        title = stringResource(R.string.server_settings_sliders),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        scrolling = false,
        saveAsMade = true,
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
        val moveHaptics = LocalHapticFeedback.current
        val reorderableState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                // A light tick each time the row passes another, so the drag can be felt slot by slot.
                moveHaptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
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
                HintCard(text = stringResource(R.string.server_settings_sliders_reorder_hint))
            }
            itemsIndexed(sliders, key = { _, slider -> slider.id }) { index, slider ->
                ReorderableItem(reorderableState, key = slider.id) {
                    SliderRow(
                        slider = slider,
                        names = names,
                        enabled = enabled,
                        actions = sliderActions,
                        index = index,
                        lastIndex = sliders.lastIndex,
                        handleModifier = dragHandle(enabled),
                    )
                }
            }
        }
    }

    if (showOverflow) {
        BingeBottomSheet(onDismissRequest = { showOverflow = false }) {
            SlidersOverflowContent(onReset = { confirmingReset = true })
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
            onDismissRequest = { confirmingReset = false },
        )
    }
}

@Composable
internal fun SlidersOverflowContent(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionSheetGroup(
        rows =
            listOf(
                actionItem(
                    Icons.Filled.RestartAlt,
                    stringResource(R.string.server_settings_sliders_reset),
                    destructive = true,
                    onClick = onReset,
                ),
            ),
        modifier = modifier,
    )
}

@Composable
private fun SliderRow(
    slider: DiscoverSlider,
    names: SliderNames,
    enabled: Boolean,
    actions: SlidersActions,
    index: Int,
    lastIndex: Int,
    handleModifier: Modifier,
) {
    val editable = !slider.builtIn && slider.type != null
    ReorderableHandleRow(
        handleModifier = handleModifier,
        onMoveUp = { actions.onMove(index, index - 1) }.takeIf { index > 0 },
        onMoveDown = { actions.onMove(index, index + 1) }.takeIf { index < lastIndex },
        // An editable row's own tap opens its editor - the more valuable action, and still
        // there via the switch itself. A built-in row has no editor to open, so the whole
        // row toggles it instead of leaving that to the small switch alone.
        modifier =
            if (editable) {
                Modifier.clickable(enabled = enabled) { actions.onOpenSlider(slider.id) }
            } else {
                Modifier.toggleable(value = slider.enabled, enabled = enabled, role = Role.Switch) { actions.onToggle(slider.id) }
            },
    ) {
        SliderKindIcon(slider.type?.icon() ?: Icons.Filled.ViewCarousel)
        Column(modifier = Modifier.weight(1f)) {
            Text(slider.label(), style = MaterialTheme.typography.bodyLarge)
            slider.caption(names)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            colors = bingeSwitchColors(),
        )
    }
}

/**
 * The kind's icon in the tinted box a settings row draws: the reorder row has no icon of its own, so the box is drawn
 * here to the same tokens as the design system's list rows.
 */
@Composable
private fun SliderKindIcon(icon: ImageVector) =
    Box(
        modifier =
            Modifier
                .size(dimensionResource(DesR.dimen.item_group_icon_size))
                .clip(BingeShapes.MoreCard)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimensionResource(DesR.dimen.item_group_icon_glyph)),
        )
    }

/** A built-in slider is named by its kind; a custom one by its title, with the kind beneath. */
@Composable
internal fun DiscoverSlider.label(): String =
    title ?: type?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.server_settings_slider_unknown_type, typeCode)

/** What a custom slider holds, under its title. A built-in slider has no caption: its name says what it is. */
@Composable
private fun DiscoverSlider.caption(names: SliderNames): String? =
    if (builtIn) {
        null
    } else {
        listOfNotNull(type?.let { stringResource(it.labelRes()) }, dataLabel(names, Regions::name))
            .joinToString(stringResource(R.string.hub_meta_separator))
    }

/**
 * A slider row's drag handle. draggableHandle() is an extension on this ReorderableCollectionItemScope, unreachable from
 * SliderRow itself. It drags on first touch, as Binge's reorder lists do, so a swipe elsewhere still scrolls, with a
 * tick as the drag starts and another as it ends.
 */
@Composable
private fun ReorderableCollectionItemScope.dragHandle(enabled: Boolean): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    return Modifier.draggableHandle(
        enabled = enabled,
        interactionSource = interactionSource,
        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
        onDragStopped = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
    )
}
