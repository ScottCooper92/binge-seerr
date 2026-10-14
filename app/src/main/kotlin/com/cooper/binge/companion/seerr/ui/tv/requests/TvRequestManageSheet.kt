package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetRows
import com.binge.designsystem.tv.component.TvSideSheetStack
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R

/** Manage's own page, the stack's root. The pages it opens are named for their [DetailSheet]. */
internal const val MANAGE_PAGE_ROOT = "manage"

/**
 * The request's actions past the row's leads, as a stack of pages. Edit, Mark as and Report open as pages over Manage, and
 * Back returns to the row that opened them. Block and Remove ask in a dialog over Manage, which stays behind it. The other
 * actions run from here and close the stack through [onDismiss], which hands focus back to Manage's button.
 * [onOpenPage] readies what a page reads before it opens; [page] draws it.
 */
@Composable
internal fun TvRequestManageSheet(
    managed: List<TvRequestActionItem>,
    actions: TvRequestDetailActions,
    onOpenPage: (DetailSheet) -> Unit,
    onDismiss: () -> Unit,
    page: @Composable ColumnScope.(step: DetailSheet, entryFocus: FocusRequester, pop: () -> Unit) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf<DetailSheet?>(null) }
    TvSideSheetStack(root = MANAGE_PAGE_ROOT, onDismissRequest = onDismiss) { nav ->
        val step = DetailSheet.entries.firstOrNull { it.name == nav.page }
        if (step == null) {
            TvRequestManageStep(managed, rowModifier = { nav.row(it.name) }) { item ->
                val sheet = item.action.sheet
                when {
                    sheet == null -> {
                        onDismiss()
                        item.onClick()
                    }
                    sheet.isPage -> {
                        onOpenPage(sheet)
                        nav.push(sheet.name, openedBy = item.action.name)
                    }
                    else -> confirming = sheet
                }
            }
        } else {
            val entryFocus = remember { FocusRequester() }
            page(step, entryFocus, nav::pop)
        }
    }
    confirming?.let { step ->
        TvRequestConfirm(
            step = step,
            actions = actions,
            onDone = { confirmed ->
                confirming = null
                // An answered confirm has run its action, and the request it acted on is gone or changed: Manage closes too.
                if (confirmed) onDismiss()
            },
        )
    }
}

/**
 * Manage's rows, in [TvRequestAction]'s order, so the destructive ones are last and read as such. [rowModifier] marks each
 * row for the stack, which focuses them.
 */
@Composable
internal fun ColumnScope.TvRequestManageStep(
    managed: List<TvRequestActionItem>,
    rowModifier: (TvRequestAction) -> Modifier,
    onSelect: (TvRequestActionItem) -> Unit,
) {
    TvSideSheetTitle(stringResource(R.string.tv_detail_manage))
    TvSideSheetRows {
        managed.forEach { item ->
            TvSideSheetRow(
                label = item.label,
                icon = item.icon,
                destructive = item.action.destructive,
                onClick = { onSelect(item) },
                modifier = rowModifier(item.action),
            )
        }
    }
}
