package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvMessagePlate
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestError
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestUiState
import io.github.scottcooper92.binge.seerr.ui.messageRes
import com.binge.designsystem.R as DesR

/** Everything the TV picker can ask of its Activity, in one place so the shell stays a wiring. */
internal class TvAdvancedRequestActions(
    val onSelectServer: (Int) -> Unit,
    val onSelectProfile: (Int) -> Unit,
    val onSelectRootFolder: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onOpenSetup: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * The hand-off Binge opens for "request with options", on a television: the server, the quality profile
 * and the root folder as option rows the D-pad walks, and Request and Close at the foot. The same state
 * and the same ViewModel as the phone's picker.
 *
 * [initialFocusedLabel] seeds one option row as focused and [initialSubmitFocused] the Request button, for
 * a preview; production passes neither.
 */
@Composable
internal fun TvAdvancedRequestScreen(
    state: AdvancedRequestUiState,
    actions: TvAdvancedRequestActions,
    modifier: Modifier = Modifier,
    initialFocusedLabel: String? = null,
    initialSubmitFocused: Boolean = false,
) {
    when (state) {
        AdvancedRequestUiState.Loading -> TvLoadingPlate(modifier = modifier)
        AdvancedRequestUiState.Submitted -> TvLoadingPlate(modifier = modifier, body = stringResource(R.string.tv_advanced_submitting))
        is AdvancedRequestUiState.Failed -> TvFailurePlate(state.error, actions, modifier)
        is AdvancedRequestUiState.Ready -> TvOptionsForm(state, actions, modifier, initialFocusedLabel, initialSubmitFocused)
    }
}

@Composable
private fun TvOptionsForm(
    state: AdvancedRequestUiState.Ready,
    actions: TvAdvancedRequestActions,
    modifier: Modifier,
    initialFocusedLabel: String?,
    initialSubmitFocused: Boolean,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvFormPage(
        headline = stringResource(R.string.advanced_title),
        body = stringResource(R.string.advanced_intro),
        icon = Icons.Filled.Send,
        modifier = modifier,
    ) {
        TvOptionGroup(
            title = stringResource(R.string.advanced_server),
            choices = state.servers.map { it.id to it.label },
            selected = state.serverId,
            onSelect = actions.onSelectServer,
            initialFocusedLabel = initialFocusedLabel,
            arrival = arrival,
        )
        if (state.isLoadingChoices) {
            TvFormNote(stringResource(R.string.tv_loading))
        } else {
            TvOptionGroup(
                title = stringResource(R.string.advanced_profile),
                choices = state.profiles.map { it.id to it.label },
                selected = state.profileId,
                onSelect = actions.onSelectProfile,
                initialFocusedLabel = initialFocusedLabel,
            )
            TvOptionGroup(
                title = stringResource(R.string.advanced_root_folder),
                choices = state.rootFolders.map { it to it },
                selected = state.rootFolder,
                onSelect = actions.onSelectRootFolder,
                initialFocusedLabel = initialFocusedLabel,
            )
        }
        state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error) }
        // Stacked, not side by side: ↓ from the last option row would otherwise land on whichever button sits
        // nearer the row's centre, and a walk down a column of rows should end on Request.
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            TvButton(
                label = stringResource(if (state.isSubmitting) R.string.tv_advanced_submitting else R.string.advanced_submit),
                onClick = actions.onSubmit,
                style = TvButtonStyle.Primary,
                enabled = state.canSubmit,
                initiallyFocused = initialSubmitFocused,
            )
            TvButton(label = stringResource(R.string.advanced_close), onClick = actions.onClose)
        }
    }
}

/** Why the picker cannot open, with the one way out that applies: setup when nothing is connected. */
@Composable
private fun TvFailurePlate(
    error: AdvancedRequestError,
    actions: TvAdvancedRequestActions,
    modifier: Modifier,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    val notConnected = error == AdvancedRequestError.NotConnected
    TvMessagePlate(
        headline = stringResource(R.string.advanced_title),
        body = stringResource(error.messageRes()),
        icon = Icons.Filled.Warning,
        alignment = Alignment.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            if (notConnected) {
                TvButton(
                    label = stringResource(R.string.advanced_open_setup),
                    onClick = actions.onOpenSetup,
                    style = TvButtonStyle.Primary,
                    modifier = Modifier.tvArrivalTarget(arrival),
                )
            }
            TvButton(
                label = stringResource(R.string.advanced_close),
                onClick = actions.onClose,
                modifier = if (notConnected) Modifier else Modifier.tvArrivalTarget(arrival),
            )
        }
    }
}
