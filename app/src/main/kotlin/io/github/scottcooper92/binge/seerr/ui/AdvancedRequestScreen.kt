package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPageActionBar
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
    val ready = state as? AdvancedRequestUiState.Ready
    ScreenScaffold(
        title = stringResource(R.string.advanced_title),
        onBack = onClose,
        bottomBar = {
            if (ready != null) {
                EditorPageActionBar(
                    label = stringResource(R.string.advanced_submit),
                    onClick = onSubmit,
                    enabled = ready.canSubmit,
                    loading = ready.isSubmitting,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.outerPadding())) {
            val inner = padding.innerPadding()
            when (state) {
                AdvancedRequestUiState.Loading, AdvancedRequestUiState.Submitted ->
                    BingeLoadingIndicator(modifier = Modifier.align(Alignment.Center).padding(inner))
                is AdvancedRequestUiState.Failed -> FailurePanel(state.error, onOpenSetup, onClose, Modifier.padding(inner))
                is AdvancedRequestUiState.Ready -> OptionsForm(state, onSelectServer, onSelectProfile, onSelectRootFolder, inner)
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
    contentPadding: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(resolvedContentInset()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.advanced_intro), style = MaterialTheme.typography.bodyMedium)
        ChoiceRow(
            title = stringResource(R.string.advanced_server),
            choices = state.destination.servers.map { it.id to it.label },
            selected = state.destination.serverId,
            onSelect = onSelectServer,
            enabled = !state.isSubmitting,
        )
        if (state.destination.loadingChoices) {
            BingeLoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            ChoiceRow(
                title = stringResource(R.string.advanced_profile),
                choices = state.destination.profiles.map { it.id to it.label },
                selected = state.destination.profileId,
                onSelect = onSelectProfile,
                enabled = !state.isSubmitting,
            )
            ChoiceRow(
                title = stringResource(R.string.advanced_root_folder),
                choices = state.destination.rootFolders.map { it to it },
                selected = state.destination.rootFolder,
                onSelect = onSelectRootFolder,
                enabled = !state.isSubmitting,
            )
        }
        state.error?.let { error ->
            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * One choice per chip, stacked. Only for a short list fixed in code — a list the server supplies
 * has no length or label width this app controls, and wants [ChoiceRow] instead; see #336.
 *
 * [enabled] is what an editor page passes while it saves. Without it a pick made mid-save lands in
 * a draft the request has already been sent from, and the chips give no sign the page is busy.
 */
@Composable
internal fun <T> ChoicePicker(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    if (choices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs))) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        choices.forEach { (id, label) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                label = { Text(label) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun FailurePanel(
    error: AdvancedRequestError,
    onOpenSetup: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(resolvedContentInset()),
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
