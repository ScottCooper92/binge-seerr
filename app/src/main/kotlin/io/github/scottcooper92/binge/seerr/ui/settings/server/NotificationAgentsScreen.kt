package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.onOffRes
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

class AgentsActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenAgent: (ServerAgent) -> Unit,
)

/** The agents page: every agent this server has, on or off, each opening its own page. */
@Composable
fun NotificationAgentsScreen(
    state: AgentsUiState,
    actions: AgentsActions,
) {
    Scaffold(topBar = { BingeTopBar(title = stringResource(R.string.settings_group_notifications), onBack = actions.onBack) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                AgentsUiState.Loading -> LoadingScreen()
                is AgentsUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
                is AgentsUiState.Ready ->
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
                        SettingsGroup(
                            title = stringResource(R.string.settings_group_notifications),
                            rows = state.agents.map { agentRow(it, actions) },
                            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
                        )
                        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
                    }
            }
        }
    }
}

@Composable
private fun agentRow(
    summary: AgentSummary,
    actions: AgentsActions,
): SettingsRow =
    SettingsRow(
        icon = summary.agent.icon(),
        iconTint = BingeSentiment.Info.fill(),
        label = stringResource(summary.agent.labelRes()),
        detail = stringResource(summary.enabled?.let(::onOffRes) ?: R.string.settings_value_unknown),
        detailColor = if (summary.enabled == true) BingeSentiment.Positive.fill() else null,
        onClick = { actions.onOpenAgent(summary.agent) },
    )
