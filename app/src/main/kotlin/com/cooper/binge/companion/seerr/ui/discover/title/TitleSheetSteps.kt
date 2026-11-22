package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.ui.requests.SheetScope

/** A request's sheet was asked for from outside the page, as the Requests list does: open it on that request. */
internal fun TitlePageViewModel.openRequest(id: Int) =
    updateReady {
        it.copy(
            manageRequestId = id,
            manageSheetOpen = true,
            requestsSheetOpen = false,
            manageFromList = false,
            manageScope = SheetScope.Both,
        )
    }

/** A request was picked from the title's list: its own level opens, and Back returns to the list. */
internal fun TitlePageViewModel.pickRequest(id: Int) =
    updateReady {
        it.copy(
            manageRequestId = id,
            manageSheetOpen = true,
            requestsSheetOpen = false,
            manageFromList = true,
            manageScope = SheetScope.Request,
        )
    }

/** Back to the title's own level of the sheet, from the list of requests or from one of them. */
internal fun TitlePageViewModel.backToRequests() =
    updateReady {
        it.copy(
            manageSheetOpen = true,
            requestsSheetOpen = false,
            manageScope = SheetScope.Title,
            manageRequestId =
                it.movie.requests
                    .toManage()
                    ?.id ?: it.manageRequestId,
            manageFromList = false,
        )
    }

internal fun TitlePageViewModel.setRequestsSheetOpen(open: Boolean) =
    updateReady {
        it.copy(requestsSheetOpen = open)
    }

internal fun TitlePageViewModel.setServiceSheetOpen(open: Boolean) =
    updateReady {
        it.copy(serviceSheetOpen = open)
    }

/** A request's sheet was asked for from outside the page, as the Requests list does: open it on that request. */
internal fun SeriesPageViewModel.openRequest(id: Int) =
    updateReady {
        it.copy(
            manageRequestId = id,
            manageSheetOpen = true,
            requestsSheetOpen = false,
            manageFromList = false,
            manageScope = SheetScope.Both,
        )
    }

/** A request was picked from the title's list: its own level opens, and Back returns to the list. */
internal fun SeriesPageViewModel.pickRequest(id: Int) =
    updateReady {
        it.copy(
            manageRequestId = id,
            manageSheetOpen = true,
            requestsSheetOpen = false,
            manageFromList = true,
            manageScope = SheetScope.Request,
        )
    }

/** Back to the title's own level of the sheet, from the list of requests or from one of them. */
internal fun SeriesPageViewModel.backToRequests() =
    updateReady {
        it.copy(
            manageSheetOpen = true,
            requestsSheetOpen = false,
            manageScope = SheetScope.Title,
            manageRequestId =
                it.series.requests
                    .toManage()
                    ?.id ?: it.manageRequestId,
            manageFromList = false,
        )
    }

internal fun SeriesPageViewModel.setRequestsSheetOpen(open: Boolean) =
    updateReady {
        it.copy(requestsSheetOpen = open)
    }

internal fun SeriesPageViewModel.setServiceSheetOpen(open: Boolean) =
    updateReady {
        it.copy(serviceSheetOpen = open)
    }
