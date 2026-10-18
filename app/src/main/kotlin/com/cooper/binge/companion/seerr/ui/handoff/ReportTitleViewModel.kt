package com.cooper.binge.companion.seerr.ui.handoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.handoff.TitleReportLink
import com.cooper.binge.companion.seerr.handoff.TitleReportLinks
import com.cooper.binge.companion.seerr.seerr.SeerrCreateIssueBody
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.details
import com.cooper.binge.companion.seerr.seerr.rejectsSession
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.NoOpAnalytics
import com.cooper.binge.companion.seerr.ui.requests.IssueReport
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What the report-from-a-scanned-title sheet shows. Only [Ready] can send, and only to the server this phone is connected to. */
sealed interface ReportTitleUiState {
    data object Loading : ReportTitleUiState

    /** The title is on this phone's own server, which would take the report. [mediaId] is that server's id for it. */
    data class Ready(
        val title: String,
        val is4k: Boolean,
        val mediaId: Int,
        val report: IssueReport = IssueReport.Idle,
    ) : ReportTitleUiState

    /** The code is for another server: nothing is sent, and the phone's own is not told about it. */
    data class DifferentServer(
        val theirs: String,
        val yours: String,
    ) : ReportTitleUiState

    data object SignedOut : ReportTitleUiState

    /** This server or this account does not take issue reports. */
    data object CannotReport : ReportTitleUiState

    /** The server has no record of the title yet, so there is nothing for an issue to be about. */
    data class NotTracked(
        val title: String,
    ) : ReportTitleUiState

    data class Error(
        val error: SeerrError,
    ) : ReportTitleUiState
}

/**
 * The phone's end of "Report an issue" from a TV (#1511). The TV's code names a title and a server; this checks that server is
 * the one this phone is signed in to, looks the title up there, and sends the report on the user's tap with this phone's own
 * session. The code is only compared with the connected address, never contacted, so a code someone else wrote cannot point a
 * report, or this phone's session, at a server it chose.
 */
@HiltViewModel(assistedFactory = ReportTitleViewModel.Factory::class)
class ReportTitleViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val link: TitleReportLink,
        private val analytics: Analytics = NoOpAnalytics,
    ) : ViewModel() {
        private val state = MutableStateFlow<ReportTitleUiState>(ReportTitleUiState.Loading)
        val uiState: StateFlow<ReportTitleUiState> = state.asStateFlow()

        init {
            viewModelScope.launch(dispatcher) { state.value = check() }
        }

        private suspend fun check(): ReportTitleUiState {
            val connected =
                attempt { connection.current().baseUrl }.getOrElse { return failure(it) }
            if (!TitleReportLinks.isSameServer(link, connected)) {
                return ReportTitleUiState.DifferentServer(theirs = label(link.server), yours = label(connected))
            }
            return attempt {
                val viewer = connection.authenticatedUser().toPermissions()
                if (!connection.profile().hasIssues || !viewer.canReportIssues) return@attempt ReportTitleUiState.CannotReport
                val api = connection.api()
                val details = api.details(link.mediaType, link.tmdbId)
                val title = details.displayTitle.orEmpty()
                details.mediaInfo?.id?.let { ReportTitleUiState.Ready(title = title, is4k = link.is4k, mediaId = it) }
                    ?: ReportTitleUiState.NotTracked(title)
            }.getOrElse { failure(it) }
        }

        private fun failure(cause: Throwable): ReportTitleUiState =
            cause.toSeerrError().let { error ->
                if (error == SeerrError.NotConnected ||
                    error.rejectsSession
                ) {
                    ReportTitleUiState.SignedOut
                } else {
                    ReportTitleUiState.Error(error)
                }
            }

        /** Sends [message] as an issue of [type] on the title, once, with this phone's session. */
        fun send(
            type: IssueType,
            message: String,
        ) {
            val ready = state.value as? ReportTitleUiState.Ready ?: return
            if (ready.report == IssueReport.Sending || ready.report == IssueReport.Sent || message.isBlank()) return
            state.value = ready.copy(report = IssueReport.Sending)
            viewModelScope.launch(dispatcher) {
                val outcome =
                    attempt { connection.api().createIssue(SeerrCreateIssueBody(ready.mediaId, type.code, message.trim())) }
                        .fold({ IssueReport.Sent }, { IssueReport.Failed(it.toSeerrError()) })
                if (outcome is IssueReport.Sent) analytics.event(AnalyticsEvents.ISSUE_REPORTED)
                state.update { current -> (current as? ReportTitleUiState.Ready)?.copy(report = outcome) ?: current }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(link: TitleReportLink): ReportTitleViewModel
        }
    }

/** A server address as a person says it: the host, and the port when it is not the scheme's own. */
private fun label(address: String): String =
    address.toHttpUrlOrNull()?.let { url -> if (url.port == defaultPort(url.scheme)) url.host else "${url.host}:${url.port}" } ?: address

private fun defaultPort(scheme: String): Int = if (scheme == "https") HTTPS_PORT else HTTP_PORT

private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443
