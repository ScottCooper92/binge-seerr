package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetNav
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.settings.onOffRes
import com.cooper.binge.companion.seerr.ui.settings.server.AgentForm
import com.cooper.binge.companion.seerr.ui.settings.server.AgentsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralSettings
import com.cooper.binge.companion.seerr.ui.settings.server.labelRes
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.binge.designsystem.R as DesR

internal const val KEY_HOUSEHOLD_PREFIX = "household:"
internal const val KEY_AGENT_PREFIX = "agent:"
private const val PAGE_AGENT_PREFIX = "agent-page:"
private const val KEY_AGENT_TOGGLE = "agent-toggle"
private const val KEY_BACK = "back"

/** The page an agent opens, named by its segment. */
internal fun agentPage(agent: ServerAgent): String = PAGE_AGENT_PREFIX + agent.segment

internal fun String.agentOrNull(): ServerAgent? =
    takeIf { startsWith(PAGE_AGENT_PREFIX) }?.removePrefix(PAGE_AGENT_PREFIX)?.let { segment ->
        ServerAgent.entries.firstOrNull {
            it.segment ==
                segment
        }
    }

/**
 * The household's server settings, as the sheet edits them: the draft, how its last change stands, and what to do about it.
 * Each toggle is saved as it is pressed, so [status] is the only feedback and [onRetry] its one action.
 */
internal class TvHousehold(
    val settings: ServerGeneralSettings,
    val status: SaveStatus,
    val onChange: ((ServerGeneralSettings) -> ServerGeneralSettings) -> Unit,
    val onRetry: () -> Unit,
)

/** The notification agents: the list's state, and the page that switches one on or off, supplied by the host that owns its view model. */
internal class TvAgents(
    val state: AgentsUiState,
    val page: @Composable ColumnScope.(agent: ServerAgent, nav: TvSideSheetNav) -> Unit,
    val onOpenAgent: (ServerAgent) -> Unit = {},
)

/** These agents with [onOpen] as what pressing one does, which only the sheet's navigation can say. */
internal fun TvAgents.opening(onOpen: (ServerAgent) -> Unit): TvAgents = TvAgents(state, page, onOpen)

/** The switches a household shares: what Discover hides and what a request may ask for. One that the server lacks is not shown. */
@Composable
internal fun householdGroup(household: TvHousehold): TvPaneGroup {
    val settings = household.settings
    val rows =
        listOfNotNull(
            toggle("hide-available", R.string.settings_hide_available, Icons.Filled.VisibilityOff, settings.hideAvailable, household) {
                it.copy(hideAvailable = !it.hideAvailable)
            },
            settings.hideRequested?.let { on ->
                toggle("hide-requested", R.string.server_settings_hide_requested, Icons.Filled.VisibilityOff, on, household) {
                    it.copy(hideRequested = !on)
                }
            },
            settings.hideBlocklisted?.let { on ->
                toggle("hide-blocklisted", R.string.server_settings_hide_blocklisted, Icons.Filled.Block, on, household) {
                    it.copy(hideBlocklisted = !on)
                }
            },
            toggle("partial-requests", R.string.server_settings_partial_requests, Icons.Filled.Tv, settings.partialRequests, household) {
                it.copy(partialRequests = !it.partialRequests)
            },
            settings.specialEpisodes?.let { on ->
                toggle("special-episodes", R.string.server_settings_special_episodes, Icons.Filled.Star, on, household) {
                    it.copy(specialEpisodes = !on)
                }
            },
        )
    return TvPaneGroup(
        title = stringResource(R.string.tv_settings_group_household),
        rows = rows,
        note = stringResource(R.string.tv_settings_household_note),
    )
}

@Composable
private fun toggle(
    key: String,
    @StringRes label: Int,
    icon: ImageVector,
    on: Boolean,
    household: TvHousehold,
    flip: (ServerGeneralSettings) -> ServerGeneralSettings,
): TvPaneRow {
    val name = stringResource(label)
    return TvPaneRow(
        key = KEY_HOUSEHOLD_PREFIX + key,
        label = name,
        detail = stringResource(onOffRes(on)),
        options = listOf(TvPaneOption(label = name, icon = icon, onSelect = { household.onChange(flip) })),
        icon = icon,
    )
}

/** Whether [group] is the household's, whose page also shows how its last change saved. */
internal fun TvPaneGroup.isHousehold(): Boolean = rows.any { it.key.startsWith(KEY_HOUSEHOLD_PREFIX) }

/** The agents this server offers, each opening a page with its switch; its state is read once, so it says unknown where it could not be. */
@Composable
internal fun agentsGroup(agents: TvAgents): TvPaneGroup? {
    val ready = agents.state as? AgentsUiState.Ready ?: return null
    return TvPaneGroup(
        title = stringResource(R.string.settings_group_notifications),
        rows =
            ready.agents.map { summary ->
                val name = stringResource(summary.agent.labelRes())
                TvPaneRow(
                    key = KEY_AGENT_PREFIX + summary.agent.segment,
                    label = name,
                    detail = summary.enabled?.let { stringResource(onOffRes(it)) } ?: stringResource(R.string.settings_value_unknown),
                    options =
                        listOf(
                            TvPaneOption(
                                label = stringResource(R.string.tv_settings_open_job),
                                icon = Icons.Filled.Notifications,
                                onSelect = { agents.onOpenAgent(summary.agent) },
                            ),
                        ),
                    icon = Icons.Filled.Notifications,
                )
            },
        note = stringResource(R.string.tv_settings_agents_note),
    )
}

/**
 * One agent's page: whether it is on, as a row to press, saved as it is pressed. An agent that cannot send without a setting
 * still blank cannot be switched on from here, and one already on without it is held unsent; both say so, since typing is the phone's.
 */
@Composable
internal fun ColumnScope.TvAgentPage(
    nav: TvSideSheetNav,
    agent: ServerAgent,
    state: EditorUiState<AgentForm>,
    onSetEnabled: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    TvSideSheetTitle(stringResource(agent.labelRes()))
    when (state) {
        EditorUiState.Loading -> TvSideSheetBody(stringResource(R.string.tv_loading))
        is EditorUiState.Error -> TvSideSheetBody(stringResource(R.string.tv_settings_agent_unavailable))
        is EditorUiState.Ready -> {
            val form = state.draft
            val blocked = !form.valid || (!form.enabled && !form.copy(enabled = true).valid)
            TvSideSheetBody(stringResource(if (blocked) R.string.tv_settings_agent_setup_first else R.string.tv_settings_agent_body))
            TvSideSheetRow(
                label = stringResource(if (form.enabled) R.string.tv_settings_agent_turn_off else R.string.tv_settings_agent_turn_on),
                icon = Icons.Filled.Notifications,
                enabled = form.enabled || !blocked,
                supportingText = stringResource(onOffRes(form.enabled)),
                onClick = { onSetEnabled(!form.enabled) },
                modifier = nav.row(KEY_AGENT_TOGGLE),
            )
            TvSaveStatus(status = state.saveStatus, onRetry = onRetry)
        }
    }
    TvSideSheetRow(label = stringResource(DesR.string.cd_navigate_back), onClick = nav::pop, modifier = nav.row(KEY_BACK))
}
