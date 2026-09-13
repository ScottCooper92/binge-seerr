package io.github.scottcooper92.binge.seerr.ui.state

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.accent
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode

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

/** A title's media status, as a row that tracks the media rather than one request shows it. */
fun SeerrMediaStatusCode.tone(): RequestStateTone =
    when (this) {
        SeerrMediaStatusCode.Processing -> RequestStateTone.Active
        SeerrMediaStatusCode.PartiallyAvailable, SeerrMediaStatusCode.Available -> RequestStateTone.Success
        SeerrMediaStatusCode.Blocklisted -> RequestStateTone.Blocked
        SeerrMediaStatusCode.Unknown -> RequestStateTone.Pending
        else -> RequestStateTone.Pending
    }

@StringRes
fun SeerrMediaStatusCode.labelRes(): Int =
    when (this) {
        SeerrMediaStatusCode.Processing -> R.string.media_state_processing
        SeerrMediaStatusCode.PartiallyAvailable -> R.string.media_state_partially_available
        SeerrMediaStatusCode.Available -> R.string.media_state_available
        SeerrMediaStatusCode.Blocklisted -> R.string.media_state_blocklisted
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
    modifier: Modifier = Modifier,
) {
    RequestStateChip(label = stringResource(status.labelRes()), tone = status.tone(), modifier = modifier)
}

/** A status pill on the design system's tag: the tone's label accent over its tonal wash. */
@Composable
fun RequestStateChip(
    label: String,
    tone: RequestStateTone,
    modifier: Modifier = Modifier,
) {
    val sentiment = tone.sentiment()
    BingeTag(label = label, modifier = modifier, tint = sentiment.accent(), fill = sentiment.fill(), uppercase = false)
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
    BingeExpressiveTheme { RequestStateChip(status = SeerrRequestStatusCode.Approved) }
}
