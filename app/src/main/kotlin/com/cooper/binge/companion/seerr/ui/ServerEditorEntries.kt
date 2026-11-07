package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.server.AgentActions
import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSliderScreen
import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSliderViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.DvrInstanceScreen
import com.cooper.binge.companion.seerr.ui.settings.server.DvrInstanceViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.NotificationAgentScreen
import com.cooper.binge.companion.seerr.ui.settings.server.NotificationAgentViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.OverrideRuleActions
import com.cooper.binge.companion.seerr.ui.settings.server.OverrideRuleScreen
import com.cooper.binge.companion.seerr.ui.settings.server.OverrideRuleViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.SliderEditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.editorActions
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
    LeaveOnDeleted(viewModel.events, onBack)
    DvrInstanceScreen(
        state = state,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        onTest = viewModel::test,
        onDelete = viewModel::delete,
    )
}

/**
 * The custom slider editor; leaves on its own once the slider is deleted, and once a new one is created. A created slider
 * is edited from the list, where the page opens for it with saving as it changes: this one was opened without an id, and
 * staying on it would show an Edit page whose edits and Delete go nowhere.
 */
@Composable
internal fun DiscoverSliderEntry(
    id: Int?,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<DiscoverSliderViewModel, DiscoverSliderViewModel.Factory>(creationCallback = { factory -> factory.create(id) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LeaveOnDeleted(viewModel.events, onBack, alsoOnSaved = id == null)
    DiscoverSliderScreen(
        state = state,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        sliderActions =
            SliderEditorActions(
                onSelectType = viewModel::selectType,
                onToggleKeyword = viewModel::toggleKeyword,
                onSelectGenre = viewModel::selectGenre,
                onSearchKeywords = viewModel::searchKeywords,
                onLoadKeywordNames = viewModel::loadKeywordNames,
                onSearchStudios = viewModel::searchStudios,
                onSelectStudio = viewModel::selectStudio,
                onNameNetwork = viewModel::nameNetwork,
                onLoadRegions = viewModel::loadRegions,
                onSelectRegion = viewModel::selectRegion,
                onToggleProvider = viewModel::toggleProvider,
                onDelete = viewModel::delete,
            ),
    )
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
    NotificationAgentScreen(
        state = state,
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
    LeaveOnDeleted(viewModel.events, onBack)
    OverrideRuleScreen(
        state = state,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        saveAsMade = id != null,
        ruleActions =
            OverrideRuleActions(
                onSelectInstance = viewModel::selectInstance,
                onToggleUser = viewModel::toggleUser,
                onToggleTag = viewModel::toggleTag,
                onToggleGenre = viewModel::toggleGenre,
                onLoadLanguages = viewModel::loadLanguages,
                onSelectLanguages = viewModel::selectLanguages,
                onToggleKeyword = viewModel::toggleKeyword,
                onSearchKeywords = viewModel::searchKeywords,
                onLoadKeywordNames = viewModel::loadKeywordNames,
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
    alsoOnSaved: Boolean = false,
) {
    // The current onBack, not the one first composed: a later one may close over a different route.
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(events) {
        events.collect { event ->
            if (event == EditorEvent.Deleted || (alsoOnSaved && event == EditorEvent.Saved)) currentOnBack()
        }
    }
}
