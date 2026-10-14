package com.cooper.binge.companion.seerr.ui.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeSheetKind
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.RequestStateChip
import com.cooper.binge.companion.seerr.ui.state.RequestStateTone
import com.cooper.binge.companion.seerr.ui.users.settings.DiscardChangesDialog
import com.cooper.binge.companion.seerr.ui.users.settings.EditorTextField
import com.binge.designsystem.R as DesR

/**
 * Report a problem with a tracked title: the kind, and a message for whoever fixes it.
 *
 * While the message holds text not yet sent, leaving asks first (#941). The sheet locks, so a swipe or a tap outside
 * it cannot close it before the question can be asked, and Back asks "Discard changes?", with Keep editing as the way
 * out that loses nothing. An empty message, or one already sent, closes as any sheet does.
 */
@Composable
internal fun ReportIssueSheet(
    report: IssueReport,
    onSend: (IssueType, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var unsent by remember { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf(false) }
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss, gesturesEnabled = !unsent) {
        // Inside the sheet, which has a window and a Back of its own; the lock above stops it closing on Back itself.
        BackHandler(enabled = unsent) { asking = true }
        ReportIssueContent(report = report, onSend = onSend, onUnsentChange = { unsent = it })
    }
    if (asking && unsent) {
        DiscardChangesDialog(
            onDiscard = {
                asking = false
                onDismiss()
            },
            onKeepEditing = { asking = false },
        )
    }
}

/** The report's form, apart from the sheet. [onUnsentChange] says whether the message holds text not yet sent. */
@Composable
internal fun ReportIssueContent(
    report: IssueReport,
    onSend: (IssueType, String) -> Unit,
    modifier: Modifier = Modifier,
    onUnsentChange: (Boolean) -> Unit = {},
) {
    var type by rememberSaveable { mutableStateOf(IssueType.Video) }
    var message by rememberSaveable { mutableStateOf("") }
    val unsent = message.isNotBlank() && report != IssueReport.Sent
    LaunchedEffect(unsent) { onUnsentChange(unsent) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(DesR.dimen.screen_content_inset)),
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
        EditorTextField(
            message,
            stringResource(R.string.issue_report_message),
            placeholder = stringResource(R.string.issue_message_placeholder),
            enabled = report != IssueReport.Sending,
            singleLine = false,
            minLines = 3,
            prose = true,
            onValueChange = { message = it },
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
        BingeActionFooter(
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

/** What an issue is about, as its row's glyph where the kinds are listed as rows. */
internal fun IssueType.icon(): ImageVector =
    when (this) {
        IssueType.Video -> Icons.Filled.Videocam
        IssueType.Audio -> Icons.AutoMirrored.Filled.VolumeUp
        IssueType.Subtitles -> Icons.Filled.Subtitles
        IssueType.Other -> Icons.Filled.MoreHoriz
    }
