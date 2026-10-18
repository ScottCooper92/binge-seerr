package com.cooper.binge.companion.seerr.ui.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.ReportIssueContent
import com.cooper.binge.companion.seerr.ui.requests.UnsentGuardSheet
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.binge.designsystem.R as DesR

/** What the sheet can ask for. Only [onSend] sends anything; [onOpenApp] is for a phone that has to sign in first. */
internal data class ReportTitleActions(
    val onSend: (IssueType, String) -> Unit = { _, _ -> },
    val onOpenApp: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** The report as a sheet; dismissing it is closing it, and nothing is sent. A typed message is asked about first. */
@Composable
internal fun ReportTitleSheet(
    state: ReportTitleUiState,
    actions: ReportTitleActions,
) {
    UnsentGuardSheet(onDismiss = actions.onClose) { onUnsentChange ->
        ReportTitleContent(state = state, actions = actions, onUnsentChange = onUnsentChange)
    }
}

/** [ReportTitleSheet]'s body without its window, for a frame. */
@Composable
internal fun ReportTitleContent(
    state: ReportTitleUiState,
    actions: ReportTitleActions,
    modifier: Modifier = Modifier,
    onUnsentChange: (Boolean) -> Unit = {},
) {
    when (state) {
        ReportTitleUiState.Loading -> Note(modifier, message = stringResource(R.string.report_title_checking), loading = true)
        is ReportTitleUiState.Ready ->
            ReportIssueContent(
                report = state.report,
                onSend = actions.onSend,
                modifier = modifier,
                onUnsentChange = onUnsentChange,
                subtitle = if (state.is4k) stringResource(R.string.report_title_version_4k, state.title) else state.title,
            )
        is ReportTitleUiState.DifferentServer ->
            Note(
                modifier,
                message = stringResource(R.string.report_title_different_server, state.theirs, state.yours),
                onOpenApp = actions.onOpenApp,
                onClose = actions.onClose,
            )
        ReportTitleUiState.SignedOut ->
            Note(
                modifier,
                message = stringResource(R.string.report_title_signed_out),
                onOpenApp = actions.onOpenApp,
                onClose = actions.onClose,
            )
        ReportTitleUiState.CannotReport ->
            Note(modifier, message = stringResource(R.string.report_title_cannot_report), onClose = actions.onClose)
        is ReportTitleUiState.NotTracked ->
            Note(modifier, message = stringResource(R.string.report_title_not_tracked, state.title), onClose = actions.onClose)
        is ReportTitleUiState.Error -> Note(modifier, message = stringResource(state.error.messageRes()), onClose = actions.onClose)
    }
}

@Composable
private fun Note(
    modifier: Modifier,
    message: String,
    loading: Boolean = false,
    onOpenApp: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.issue_report_title), style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (loading) BingeLoadingIndicator()
        onOpenApp?.let {
            BingeFilledButton(label = stringResource(R.string.report_title_open_app), onClick = it, modifier = Modifier.fillMaxWidth())
        }
        onClose?.let {
            BingeOutlinedButton(
                label = stringResource(R.string.report_title_close),
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
