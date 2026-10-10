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
import com.binge.designsystem.component.HintCard
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import io.github.scottcooper92.binge.seerr.R
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
    BingeScreenScaffold(
        bar = ScreenBar.Small,
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
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
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
        HintCard(text = stringResource(R.string.advanced_intro))
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
