package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvQrCode
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** How much of the room the card leaves it the code takes: a little under all of it, so it sits in the card rather than against it. */
private const val QR_FILL = 0.6f

/** The pane's content: the code, or why there is none. */
@Composable
internal fun ColumnScope.TvHandOffContent(handOff: AddressHandOff) {
    when (handOff) {
        is AddressHandOff.Listening ->
            TvHandOffCodeCard(
                url = handOff.url,
                scanUrl = handOff.scanUrl,
                pin = handOff.pin,
                modifier = Modifier.weight(1f),
            )
        is AddressHandOff.Unavailable -> TvFormNote(stringResource(handOff.reason.messageRes()), tone = TvFormNoteTone.Error)
    }
}

/**
 * The code and its address together on one card, as Binge's TV sign-in sets its code: the address is the
 * fallback for a phone without a camera app, so it belongs with the code, legible from across a room. Spelled
 * out in full, `http://` included, and monospace because it is typed. The card is the form column's width, so
 * the button under it can end where it ends.
 */
@Composable
private fun TvHandOffCodeCard(
    url: String,
    scanUrl: String,
    pin: String?,
    modifier: Modifier = Modifier,
) {
    // The pane's room sizes the code; the card then wraps the code and the address and nothing more, as wide as the
    // address needs on one line, centred in what is left.
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val qrSize = minOf(maxWidth, maxHeight) * QR_FILL
        Column(
            modifier =
                Modifier
                    .width(IntrinsicSize.Max)
                    .clip(BingeShapes.AccountCard)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(dimensionResource(TvR.dimen.tv_button_border_width), MaterialTheme.colorScheme.border, BingeShapes.AccountCard)
                    .padding(dimensionResource(DesR.dimen.padding_l)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            // The design system's code: drawn at display size, black on a white plate that is its own quiet zone.
            TvQrCode(
                content = scanUrl,
                contentDescription = stringResource(R.string.tv_handoff_code_description),
                modifier = Modifier.size(qrSize),
            )
            // The PIN the phone asks for before it does anything with the code (#803): read across the room, so large and
            // spaced, and announced as the PIN rather than a number.
            pin?.let { digits ->
                val description = pinDescription(digits)
                Text(
                    text = stringResource(R.string.tv_handoff_pin, digits.toList().joinToString(" ")),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
            Text(
                text = url,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun pinDescription(digits: String): String = stringResource(R.string.tv_handoff_pin_description, digits.toList().joinToString(" "))

private fun AddressHandOff.Reason.messageRes(): Int =
    when (this) {
        AddressHandOff.Reason.NoLocalNetwork -> R.string.tv_handoff_no_network
        AddressHandOff.Reason.CouldNotListen -> R.string.tv_handoff_could_not_listen
    }
