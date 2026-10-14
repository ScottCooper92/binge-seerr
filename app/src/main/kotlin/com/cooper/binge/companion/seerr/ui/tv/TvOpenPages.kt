package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The pages open over each tab's root. Each tab keeps its own, so switching away hides them and switching back shows them
 * again; choosing the current tab again clears them, as the phone's reselect does.
 */
@Stable
internal class TvOpenPages(
    openRequestId: Int? = null,
    openIssueId: Int? = null,
    seeAllRequests: String? = null,
    seeAllIssues: String? = null,
    seeAllAccount: Boolean = false,
) {
    /** A request's detail page, over Home or over one of its see-all grids. */
    var openRequestId by mutableStateOf(openRequestId)

    /** An issue's read-only detail page, on the same footing over Issues. */
    var openIssueId by mutableStateOf(openIssueId)

    /** A see-all grid, by its filter's name. It stays up under a detail page opened from one of its cards. */
    var seeAllRequests by mutableStateOf(seeAllRequests)
    var seeAllIssues by mutableStateOf(seeAllIssues)

    /** Home's see-all for the signed-in user's own requests, which belong to no filter. */
    var seeAllAccount by mutableStateOf(seeAllAccount)

    fun anyOpen(tab: TvDestination): Boolean =
        when (tab) {
            TvDestination.Hub -> openRequestId != null || seeAllRequests != null || seeAllAccount
            TvDestination.Issues -> openIssueId != null || seeAllIssues != null
            else -> false
        }

    fun clear(tab: TvDestination) {
        when (tab) {
            TvDestination.Hub -> {
                openRequestId = null
                seeAllRequests = null
                seeAllAccount = false
            }
            TvDestination.Issues -> {
                openIssueId = null
                seeAllIssues = null
            }
            else -> Unit
        }
    }

    /**
     * Keeps the shell on an offered tab, as the phone's tabbed back stack reconciles: a tab that stops being offered loses
     * its pages, so they do not come back with it, and if it was [tab] the start tab, the first of [offered], takes over.
     */
    fun reconcile(
        tab: TvDestination,
        offered: List<TvDestination>,
    ): TvDestination {
        TvDestination.entries.filterNot { it in offered }.forEach(::clear)
        return if (tab in offered) tab else offered.first()
    }

    companion object {
        val Saver =
            listSaver<TvOpenPages, Any?>(
                save = { listOf(it.openRequestId, it.openIssueId, it.seeAllRequests, it.seeAllIssues, it.seeAllAccount) },
                restore = {
                    TvOpenPages(
                        openRequestId = it[0] as Int?,
                        openIssueId = it[1] as Int?,
                        seeAllRequests = it[2] as String?,
                        seeAllIssues = it[3] as String?,
                        seeAllAccount = it[4] as Boolean,
                    )
                },
            )
    }
}

@Composable
internal fun rememberTvOpenPages(): TvOpenPages = rememberSaveable(saver = TvOpenPages.Saver) { TvOpenPages() }
