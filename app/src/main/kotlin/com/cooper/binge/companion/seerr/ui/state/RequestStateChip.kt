package com.cooper.binge.companion.seerr.ui.state

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.binge.designsystem.component.StatusChip
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.theme.SeerrTheme

/**
 * Colour roles for a status chip, named for what the state means: amber waiting, blue in motion,
 * green done, red refused, muted blocked. The label names the state, so colour is reinforcement.
 */
enum class RequestStateTone {
    Pending,
    Active,
    Success,
    Declined,
    Blocked,
}

/** A request's own status, as the requests browser and a request page show it. */
fun SeerrRequestStatusCode.tone(): RequestStateTone =
    when (this) {
        SeerrRequestStatusCode.Approved, SeerrRequestStatusCode.Completed -> RequestStateTone.Success
        SeerrRequestStatusCode.Declined, SeerrRequestStatusCode.Failed -> RequestStateTone.Declined
        else -> RequestStateTone.Pending
    }

@StringRes
fun SeerrRequestStatusCode.labelRes(): Int =
    when (this) {
        SeerrRequestStatusCode.Approved -> R.string.request_state_approved
        SeerrRequestStatusCode.Completed -> R.string.request_state_completed
        SeerrRequestStatusCode.Declined -> R.string.request_state_declined
        SeerrRequestStatusCode.Failed -> R.string.request_state_failed
        else -> R.string.request_state_pending
    }

/** What the download client is doing for one version of a title, as far as Seerr's queue reports it. */
enum class DownloadActivity {
    None,
    Queued,
    Downloading,
}

/** A title's state as every surface names it: the label and its tone. */
data class MediaStateLabel(
    @param:StringRes val labelRes: Int,
    val tone: RequestStateTone,
)

/**
 * The one mapping from a title's media status to its label, read by the list, the title's badge, Manage media and TV.
 * `Processing` reads Downloading only while something transfers: Seerr leaves a title processing after its files are
 * deleted, or when nothing was ever grabbed, so with nothing in the queue it reads "Approved · not downloading" (#1719).
 */
fun SeerrMediaStatusCode.stateLabel(download: DownloadActivity): MediaStateLabel =
    if (this == SeerrMediaStatusCode.Processing) {
        when (download) {
            DownloadActivity.Downloading -> MediaStateLabel(R.string.media_state_downloading, RequestStateTone.Active)
            DownloadActivity.Queued -> MediaStateLabel(R.string.request_state_queued, RequestStateTone.Pending)
            DownloadActivity.None -> MediaStateLabel(R.string.media_state_not_downloading, RequestStateTone.Pending)
        }
    } else {
        MediaStateLabel(labelRes(), tone())
    }

private fun SeerrMediaStatusCode.tone(): RequestStateTone =
    when (this) {
        SeerrMediaStatusCode.PartiallyAvailable, SeerrMediaStatusCode.Available -> RequestStateTone.Success
        SeerrMediaStatusCode.Blocklisted, SeerrMediaStatusCode.Deleted -> RequestStateTone.Blocked
        else -> RequestStateTone.Pending
    }

@StringRes
private fun SeerrMediaStatusCode.labelRes(): Int =
    when (this) {
        SeerrMediaStatusCode.PartiallyAvailable -> R.string.media_state_partially_available
        SeerrMediaStatusCode.Available -> R.string.media_state_available
        SeerrMediaStatusCode.Blocklisted -> R.string.media_state_blocklisted
        SeerrMediaStatusCode.Deleted -> R.string.media_state_deleted
        SeerrMediaStatusCode.Unknown -> R.string.media_state_unknown
        else -> R.string.request_state_pending
    }

@Composable
fun RequestStateChip(
    status: SeerrRequestStatusCode,
    modifier: Modifier = Modifier,
) {
    RequestStateChip(label = stringResource(status.labelRes()), tone = status.tone(), modifier = modifier)
}

@Composable
fun MediaStateChip(
    status: SeerrMediaStatusCode,
    download: DownloadActivity,
    modifier: Modifier = Modifier,
) {
    val state = status.stateLabel(download)
    RequestStateChip(label = stringResource(state.labelRes), tone = state.tone, modifier = modifier)
}

/** A status pill: the design system's [StatusChip], with this app's tone mapped onto its sentiment. */
@Composable
fun RequestStateChip(
    label: String,
    tone: RequestStateTone,
    modifier: Modifier = Modifier,
) {
    StatusChip(label = label, sentiment = tone.sentiment(), modifier = modifier)
}

private fun RequestStateTone.sentiment(): BingeSentiment =
    when (this) {
        RequestStateTone.Pending -> BingeSentiment.Caution
        RequestStateTone.Active -> BingeSentiment.Info
        RequestStateTone.Success -> BingeSentiment.Positive
        RequestStateTone.Declined -> BingeSentiment.Negative
        RequestStateTone.Blocked -> BingeSentiment.Neutral
    }

@Preview(showBackground = true)
@Composable
private fun PreviewRequestStateChip() {
    SeerrTheme { RequestStateChip(status = SeerrRequestStatusCode.Approved) }
}
