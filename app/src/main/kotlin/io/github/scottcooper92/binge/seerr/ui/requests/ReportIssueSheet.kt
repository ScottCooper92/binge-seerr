package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeSheetFooter
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone
import com.binge.designsystem.R as DesR

/** Report a problem with a tracked title: the kind, and a message for whoever fixes it. */
@Composable
internal fun ReportIssueSheet(
    report: IssueReport,
    onSend: (IssueType, String) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        ReportIssueContent(report = report, onSend = onSend)
    }
}

@Composable
internal fun ReportIssueContent(
    report: IssueReport,
    onSend: (IssueType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by rememberSaveable { mutableStateOf(IssueType.Video) }
    var message by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.issue_report_title), style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            IssueType.entries.forEach { candidate ->
                BingeFilterChip(label = stringResource(candidate.labelRes()), selected = candidate == type, onClick = { type = candidate })
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(stringResource(R.string.issue_report_message)) },
            minLines = 3,
            enabled = report != IssueReport.Sending,
            modifier = Modifier.fillMaxWidth(),
        )
        when (report) {
            is IssueReport.Failed ->
                Text(
                    stringResource(R.string.issue_report_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            IssueReport.Sent -> RequestStateChip(label = stringResource(R.string.issue_report_sent), tone = RequestStateTone.Success)
            IssueReport.Idle, IssueReport.Sending -> Unit
        }
        BingeSheetFooter(
            label = stringResource(R.string.issue_report_send),
            onClick = { onSend(type, message) },
            enabled = message.isNotBlank() && report != IssueReport.Sending && report != IssueReport.Sent,
            loading = report == IssueReport.Sending,
        )
    }
}

internal fun IssueType.labelRes(): Int =
    when (this) {
        IssueType.Video -> R.string.issue_type_video
        IssueType.Audio -> R.string.issue_type_audio
        IssueType.Subtitles -> R.string.issue_type_subtitles
        IssueType.Other -> R.string.issue_type_other
    }
