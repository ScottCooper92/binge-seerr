package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind

/** The server at a glance, as the first page's card shows it: which server and version as its [title], then its [lines]. */
internal class TvSettingsSummary(
    val title: String,
    val lines: List<String>,
)

/**
 * The summary: the server and its version, with an update or how far behind it is on that line only; then its address,
 * how it is answering, and who is signed in. How it is answering waits on the hub, so Settings never says a health it
 * has not checked.
 */
@Composable
internal fun tvSettingsSummary(
    state: SettingsUiState.Ready,
    hub: HubUiState?,
): TvSettingsSummary =
    TvSettingsSummary(
        title = state.server.versionLine(),
        lines =
            listOfNotNull(
                state.connection.baseUrl,
                (hub as? HubUiState.Ready)?.health?.let { stringResource(it.labelRes()) },
                state.connection.signedInLine(),
            ),
    )

private fun ConnectionHealth.labelRes(): Int =
    when (this) {
        ConnectionHealth.Healthy -> R.string.hub_health_connected
        ConnectionHealth.Checking -> R.string.tv_hub_health_checking
        ConnectionHealth.Unauthorized -> R.string.hub_unauthorized_headline
        ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_headline
        ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_headline
        ConnectionHealth.Unreachable -> R.string.hub_unreachable_headline
    }

@Composable
private fun ConnectionSummary.signedInLine(): String =
    when (signInKind) {
        SignInKind.ApiKey -> stringResource(R.string.tv_settings_signed_in_api_key)
        SignInKind.Session -> stringResource(R.string.tv_settings_signed_in_as, userName ?: stringResource(R.string.settings_value_unknown))
    }

@Composable
private fun ServerSummary.versionLine(): String {
    val edition =
        versionLabel?.let { stringResource(R.string.setup_server_edition, variant.displayName, it) }
            ?: stringResource(R.string.setup_server_development, variant.displayName)
    return when {
        commitsBehind > 0 -> stringResource(R.string.settings_version_behind, edition, commitsBehind)
        updateAvailable -> stringResource(R.string.settings_version_update, edition)
        else -> edition
    }
}
