package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/** The TV shell as a frame draws it: the bar over [content], on the start tab, with nothing open. */
@Composable
internal fun PreviewTabShell(
    account: TvTopBarAccount,
    tabs: List<TvTab> = listOf(TvTab.Requests, TvTab.Issues),
    content: @Composable () -> Unit,
) {
    TvTabShell(
        tab = TvTab.Requests,
        tabs = tabs,
        home = TvTab.Requests,
        onSelectTab = {},
        sheetOpen = false,
        onOpenSheet = {},
        onCloseSheet = {},
        account = account,
        contentRequester = remember { FocusRequester() },
    ) { content() }
}
