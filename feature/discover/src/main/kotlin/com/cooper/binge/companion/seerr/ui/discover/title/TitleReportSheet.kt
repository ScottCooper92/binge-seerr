package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.handoff.TitleReportLink
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleActions
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleSheet
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleViewModel

/**
 * The report form for a title this page shows, over the page. It is the form a scanned code opens, for a title that is
 * on this app's own server, so the server's answer to who may report and the send are the same ones.
 */
@Composable
fun TitleReportSheet(
    mediaType: String,
    tmdbId: Int,
    onClose: () -> Unit,
) {
    val viewModel =
        hiltViewModel<ReportTitleViewModel, ReportTitleViewModel.Factory>(
            key = "report-$mediaType-$tmdbId",
            creationCallback = { factory -> factory.create(TitleReportLink("", mediaType, tmdbId, is4k = false, ownServer = true)) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportTitleSheet(
        state = state,
        actions =
            ReportTitleActions(
                onSend = viewModel::send,
                onOpenApp = onClose,
                onReportAnyway = viewModel::reportAnyway,
                onClose = {
                    viewModel.dismiss()
                    onClose()
                },
            ),
    )
}
