package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvSideSheet
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetRows
import com.binge.designsystem.tv.component.TvSideSheetStepFocus
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R

/**
 * The request's actions past the row's two. An action with a sheet of its own swaps this sheet for it through
 * [onOpenActionSheet], so that sheet hands focus back to Manage when it closes; the others run from here and close it
 * through [onDismiss], which hands focus back too.
 */
@Composable
internal fun TvRequestManageSheet(
    managed: List<TvRequestActionItem>,
    onOpenActionSheet: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvSideSheet(onDismissRequest = onDismiss) { entryFocus ->
        TvRequestManageStep(
            managed = managed,
            onSelect = { item ->
                if (item.action.sheet != null) onOpenActionSheet() else onDismiss()
                item.onClick()
            },
            entryFocus = entryFocus,
        )
    }
}

/** Manage's rows, in [TvRequestAction]'s order, so the destructive ones are last and read as such. */
@Composable
internal fun ColumnScope.TvRequestManageStep(
    managed: List<TvRequestActionItem>,
    onSelect: (TvRequestActionItem) -> Unit,
    entryFocus: FocusRequester,
) {
    TvSideSheetTitle(stringResource(R.string.tv_detail_manage))
    TvSideSheetRows {
        managed.forEachIndexed { index, item ->
            TvSideSheetRow(
                label = item.label,
                icon = item.icon,
                destructive = item.action.destructive,
                onClick = { onSelect(item) },
                modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
    }
    TvSideSheetStepFocus(entryFocus)
}
