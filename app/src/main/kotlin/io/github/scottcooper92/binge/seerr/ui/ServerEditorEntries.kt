package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.settings.server.AgentActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.DiscoverSliderScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.DiscoverSliderViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.DvrInstanceScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.DvrInstanceViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.NotificationAgentScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.NotificationAgentViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.OverrideRuleActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.OverrideRuleScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.OverrideRuleViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerAgent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorActions
import kotlinx.coroutines.flow.Flow

/** The instance editor; leaves on its own once the instance is deleted. */
@Composable
internal fun DvrInstanceEntry(
    type: ServiceType,
    id: Int?,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<DvrInstanceViewModel, DvrInstanceViewModel.Factory>(creationCallback = { factory -> factory.create(type, id) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    LeaveOnDeleted(viewModel.events, onBack)
    DvrInstanceScreen(
        state = state,
        extras = extras,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        onTest = viewModel::test,
        onDelete = viewModel::delete,
    )
}

/** The custom slider editor; leaves on its own once the slider is deleted. */
@Composable
internal fun DiscoverSliderEntry(
    id: Int?,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<DiscoverSliderViewModel, DiscoverSliderViewModel.Factory>(creationCallback = { factory -> factory.create(id) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LeaveOnDeleted(viewModel.events, onBack)
    DiscoverSliderScreen(state = state, events = viewModel.events, actions = viewModel.editorActions(onBack), onDelete = viewModel::delete)
}

/** One agent's editor, with the test beside it. */
@Composable
internal fun NotificationAgentEntry(
    agent: ServerAgent,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<NotificationAgentViewModel, NotificationAgentViewModel.Factory>(creationCallback = { factory ->
            factory.create(agent)
        })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    NotificationAgentScreen(
        state = state,
        extras = extras,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        agentActions =
            AgentActions(
                onSetEnabled = viewModel::setEnabled,
                onSetOption = viewModel::setOption,
                onSetEncryption = viewModel::setEncryption,
                onToggleType = viewModel::toggleType,
                onTest = viewModel::test,
            ),
        agent = agent,
    )
}

/** The rule editor; leaves on its own once the rule is deleted. */
@Composable
internal fun OverrideRuleEntry(
    id: Int?,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<OverrideRuleViewModel, OverrideRuleViewModel.Factory>(creationCallback = { factory ->
            factory.create(id)
        })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    LeaveOnDeleted(viewModel.events, onBack)
    OverrideRuleScreen(
        state = state,
        extras = extras,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        ruleActions =
            OverrideRuleActions(
                onSelectInstance = viewModel::selectInstance,
                onToggleUser = viewModel::toggleUser,
                onToggleTag = viewModel::toggleTag,
                onDelete = viewModel::delete,
            ),
    )
}

/**
 * An editor whose record can be deleted leaves on [EditorEvent.Deleted] rather than on a flag that
 * stays raised: the record is gone, so there is nothing for the page to go on showing.
 */
@Composable
private fun LeaveOnDeleted(
    events: Flow<EditorEvent>,
    onBack: () -> Unit,
) {
    LaunchedEffect(events) { events.collect { event -> if (event == EditorEvent.Deleted) onBack() } }
}
