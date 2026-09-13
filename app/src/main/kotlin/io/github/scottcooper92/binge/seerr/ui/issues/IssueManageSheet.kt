package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import com.binge.designsystem.R as DesR

/**
 * The page's overflow: the issue on the server, and, for a manager or the reporter, deleting it
 * behind a confirm that says the thread goes with it.
 */
@Composable
internal fun IssueManageSheet(
    detail: IssueDetail,
    onDeleteIssue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    BingeBottomSheet(onDismissRequest = onDismiss) {
        IssueManageContent(
            canDelete = detail.canDelete,
            onOpenWeb = {
                onDismiss()
                context.openInBrowser(detail.webUrl)
            },
            onDelete = { confirmingDelete = true },
        )
    }
    if (confirmingDelete) {
        BingeConfirmDialog(
            title = stringResource(R.string.issue_delete_confirm_title),
            message = stringResource(R.string.issue_delete_confirm_message),
            confirmLabel = stringResource(R.string.issue_delete),
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDismiss()
                onDeleteIssue()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
internal fun IssueManageContent(
    canDelete: Boolean,
    onOpenWeb: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
        ManageRow(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.request_open_web), onOpenWeb)
        if (canDelete) {
            ManageRow(
                Icons.Filled.Delete,
                stringResource(R.string.issue_delete),
                onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ManageRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(onClick = onClick)
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
