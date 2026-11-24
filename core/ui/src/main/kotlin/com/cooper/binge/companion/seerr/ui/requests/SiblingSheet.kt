package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.material3.SnackbarHostState

/**
 * Another request against this title, whose actions open in a sheet over this page. [open] is separate
 * from the request being present: dismissing hides the sheet but keeps the request's view model alive
 * until its result lands, so a moderation's snackbar and this page's reload are not dropped with it.
 */
class SiblingSheet(
    val requestId: Int,
    val open: Boolean,
    val snackbarHostState: SnackbarHostState,
    val onDismiss: () -> Unit,
    /** A moderation of that request finished, so this page's list of requests is stale. */
    val onChanged: () -> Unit,
    /** What the opener already knows of the request, so the sheet shows it at once while the detail loads. */
    val preview: RequestPreview? = null,
    /** What the title page adds above the request's own rows: Watch, then Request in 4K. Empty from every other opener. */
    val titleRows: List<SheetTitleRow> = emptyList(),
    val titleState: SheetStateChip? = null,
    val titleBack: (() -> Unit)? = null,
    val scope: SheetScope = SheetScope.Both,
    /** What the title level's request cards call back; null from every other opener. */
    val cards: RequestCardsHost? = null,
)

/** What a request card does beyond showing: open its level, and tell the page a moderation on it landed. */
class RequestCardsHost(
    val onOpen: (Int) -> Unit,
    val onChanged: () -> Unit,
    val snackbar: SnackbarHostState,
)

/** A request and what this viewer may do to it, as a list row knows them before its detail has loaded. */
class RequestPreview(
    val item: RequestItem,
    val actions: RequestActions,
)
