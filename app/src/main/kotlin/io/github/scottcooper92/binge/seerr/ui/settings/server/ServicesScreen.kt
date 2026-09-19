package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import com.binge.designsystem.R as DesR

class ServicesActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenInstance: (ServiceType, Int?) -> Unit,
    val onOpenRule: (Int?) -> Unit,
)

/** The services page: each kind's instances with a way to add one, and the override rules where the server has them. */
@Composable
fun ServicesScreen(
    state: ServicesUiState,
    actions: ServicesActions,
) {
    ScreenScaffold(title = stringResource(R.string.settings_group_services), onBack = actions.onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.outerPadding())) {
            val inner = padding.innerPadding()
            when (state) {
                ServicesUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is ServicesUiState.Error -> ErrorScreen(error = state.error, modifier = Modifier.padding(inner), onRetry = actions.onRetry)
                is ServicesUiState.Ready -> ServicesContent(state, actions, contentPadding = inner)
            }
        }
    }
}

@Composable
private fun ServicesContent(
    state: ServicesUiState.Ready,
    actions: ServicesActions,
    contentPadding: PaddingValues,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        ServiceType.entries.forEach { type ->
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            SettingsGroup(
                title = type.name,
                rows = instanceRows(type, state.instances.filter { it.type == type }, actions),
                modifier = Modifier.padding(horizontal = resolvedContentInset()),
            )
        }
        state.rules?.let { rules ->
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            SettingsGroup(
                title = stringResource(R.string.server_settings_rules),
                rows = ruleRows(rules, actions),
                modifier = Modifier.padding(horizontal = resolvedContentInset()),
            )
        }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    }
}

@Composable
private fun instanceRows(
    type: ServiceType,
    instances: List<DvrSummary>,
    actions: ServicesActions,
): List<SettingsRow> =
    instances.map { instance ->
        SettingsRow(
            icon = if (type == ServiceType.Radarr) Icons.Filled.Movie else Icons.Filled.Tv,
            iconTint = BingeSentiment.Info.fill(),
            label = instance.label(),
            detail = instance.address,
            onClick = { actions.onOpenInstance(type, instance.id) },
        )
    } +
        SettingsRow(
            icon = Icons.Filled.Add,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.server_settings_dvr_add, type.name),
            onClick = { actions.onOpenInstance(type, null) },
        )

@Composable
private fun DvrSummary.label(): String {
    val markers =
        listOfNotNull(
            if (is4k) stringResource(R.string.settings_service_4k) else null,
            if (isDefault) stringResource(R.string.settings_service_default) else null,
        )
    return (listOf(name) + markers).joinToString(stringResource(R.string.hub_meta_separator))
}

@Composable
private fun ruleRows(
    rules: List<OverrideRuleSummary>,
    actions: ServicesActions,
): List<SettingsRow> =
    rules.map { rule ->
        SettingsRow(
            icon = Icons.Filled.Rule,
            iconTint = BingeSentiment.Info.fill(),
            label = rule.instanceName,
            detail = pluralStringResource(R.plurals.server_settings_rule_conditions, rule.conditions, rule.conditions),
            onClick = { actions.onOpenRule(rule.id) },
        )
    } +
        SettingsRow(
            icon = Icons.Filled.Add,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.server_settings_rule_add),
            onClick = { actions.onOpenRule(null) },
        )
