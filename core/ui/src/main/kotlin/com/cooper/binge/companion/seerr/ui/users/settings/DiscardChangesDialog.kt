package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.cooper.binge.companion.seerr.core.ui.R

/** "Discard changes?", with Keep editing as the way out that loses nothing. */
@Composable
fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    BingeConfirmDialog(
        title = stringResource(R.string.editor_discard_title),
        message = stringResource(R.string.editor_discard_message),
        confirmLabel = stringResource(R.string.editor_discard),
        dismissLabel = stringResource(R.string.editor_keep_editing),
        destructive = true,
        onConfirm = onDiscard,
        onDismissRequest = onKeepEditing,
    )
}
