package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvConfirmDialogContent
import com.binge.designsystem.R as DesR

/** A confirm dialog's card on its own, since a dialog's window is not part of a frame; Cancel is drawn focused, where the user lands. */
@Composable
internal fun ConfirmDialogFrame(
    title: String,
    message: String,
    confirmLabel: String,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TvConfirmDialogContent(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            dismissLabel = stringResource(DesR.string.action_cancel),
            destructive = true,
            onConfirm = {},
            onDismissRequest = {},
            initiallyDismissFocused = true,
        )
    }
}
