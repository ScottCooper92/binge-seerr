package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTopBar
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The hand-off screen Binge opens for "request with options": the server, the quality profile and
 * the root folder, each opening on what a plain request would use. Built from the shared design
 * system like the setup screen, so the hand-off reads as a continuation of Binge, not a detour.
 */
@Composable
fun AdvancedRequestScreen(
    state: AdvancedRequestUiState,
    onSelectServer: (Int) -> Unit,
    onSelectProfile: (Int) -> Unit,
    onSelectRootFolder: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenSetup: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(topBar = { BingeTopBar(title = stringResource(R.string.advanced_title), onBack = onClose) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                AdvancedRequestUiState.Loading, AdvancedRequestUiState.Submitted ->
                    BingeLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                is AdvancedRequestUiState.Failed -> FailurePanel(state.error, onOpenSetup, onClose)
                is AdvancedRequestUiState.Ready -> OptionsForm(state, onSelectServer, onSelectProfile, onSelectRootFolder, onSubmit)
            }
        }
    }
}

@Composable
private fun OptionsForm(
    state: AdvancedRequestUiState.Ready,
    onSelectServer: (Int) -> Unit,
    onSelectProfile: (Int) -> Unit,
    onSelectRootFolder: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.advanced_intro), style = MaterialTheme.typography.bodyMedium)
        ChoicePicker(
            title = stringResource(R.string.advanced_server),
            choices = state.servers.map { it.id to it.label },
            selected = state.serverId,
            onSelect = onSelectServer,
        )
        if (state.isLoadingChoices) {
            BingeLoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            ChoicePicker(
                title = stringResource(R.string.advanced_profile),
                choices = state.profiles.map { it.id to it.label },
                selected = state.profileId,
                onSelect = onSelectProfile,
            )
            ChoicePicker(
                title = stringResource(R.string.advanced_root_folder),
                choices = state.rootFolders.map { it to it },
                selected = state.rootFolder,
                onSelect = onSelectRootFolder,
            )
        }
        state.error?.let { error ->
            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        BingeFilledButton(
            label = stringResource(R.string.advanced_submit),
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One choice per chip, stacked: root-folder paths are long, and a wrapped path is unreadable. */
@Composable
internal fun <T> ChoicePicker(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    if (choices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs))) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        choices.forEach { (id, label) ->
            FilterChip(selected = id == selected, onClick = { onSelect(id) }, label = { Text(label) })
        }
    }
}

@Composable
private fun FailurePanel(
    error: AdvancedRequestError,
    onOpenSetup: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(error.messageRes()), style = MaterialTheme.typography.bodyMedium)
        if (error == AdvancedRequestError.NotConnected) {
            BingeFilledButton(
                label = stringResource(R.string.advanced_open_setup),
                onClick = onOpenSetup,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BingeOutlinedButton(label = stringResource(R.string.advanced_close), onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

internal fun AdvancedRequestError.messageRes(): Int =
    when (this) {
        AdvancedRequestError.NotConnected -> R.string.advanced_error_not_connected
        AdvancedRequestError.NoServers -> R.string.advanced_error_no_servers
        AdvancedRequestError.Rejected -> R.string.advanced_error_rejected
        AdvancedRequestError.Unreachable -> R.string.advanced_error_unreachable
        AdvancedRequestError.Unknown -> R.string.advanced_error_unknown
    }
