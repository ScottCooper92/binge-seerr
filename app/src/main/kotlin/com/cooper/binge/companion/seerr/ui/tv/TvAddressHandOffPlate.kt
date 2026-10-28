package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import com.binge.designsystem.tv.component.TvPairingCard
import com.binge.designsystem.tv.component.TvPairingCardLoading
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.AddressHandOff
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** How much of the room the card leaves it the code takes: a little under all of it, so it sits in the card rather than against it. */
private const val QR_FILL = 0.6f

/**
 * The pane's content: the design system's pairing card (#1064) with the code while the TV listens, or a placeholder of
 * the same shape while [preparing], so the page does not move as the code arrives. [preparing] is true only when a code
 * is actually being fetched; with no code and nothing coming, the pane stays empty rather than promise one. A TV with no
 * code to give says why in a note instead, with the way to type the address under it.
 */
@Composable
internal fun ColumnScope.TvHandOffContent(
    handOff: AddressHandOff?,
    preparing: Boolean,
) {
    if (handOff is AddressHandOff.Unavailable) {
        TvFormNote(stringResource(handOff.reason.messageRes()), tone = TvFormNoteTone.Error)
        return
    }
    // The pane's room sizes the code; the card then wraps it and nothing more, centred in what is left. The card's own
    // lines and padding come out of the height first, so a short pane shrinks the code rather than cutting off the PIN.
    val chrome = pairingCardChrome()
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val qrSize = minOf(maxWidth * QR_FILL, maxOf(maxHeight - chrome, Dp.Hairline))
        if (handOff is AddressHandOff.Listening) {
            TvHandOffCodeCard(handOff, qrSize)
        } else if (preparing) {
            TvPairingCardLoading(message = stringResource(R.string.tv_setup_status_preparing), qrSize = qrSize)
        }
    }
}

/**
 * The code and its address together, as Binge's TV sign-in sets its code: the address is the fallback for a phone
 * without a camera app, spelled out in full, `http://` included. The PIN the phone asks for before it does anything
 * with the code (#803) sits between them, read across the room and announced as the PIN rather than a number.
 */
@Composable
private fun TvHandOffCodeCard(
    code: AddressHandOff.Listening,
    qrSize: Dp,
) {
    TvPairingCard(
        payload = code.scanUrl,
        qrDescription = stringResource(R.string.tv_handoff_code_description),
        code = code.pin?.let { stringResource(R.string.tv_handoff_pin, it.spaced()) },
        codeDescription = code.pin?.let { stringResource(R.string.tv_handoff_pin_description, it.spaced()) },
        address = code.url,
        qrSize = qrSize,
    )
}

/**
 * The height [TvPairingCard] adds around its code: its padding and border, and the PIN and address lines with the gaps
 * between them, measured in the styles the card sets them in.
 */
@Composable
private fun pairingCardChrome(): Dp {
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val pin = measurer.measure("0", typography.headlineMedium.copy(fontFamily = FontFamily.Monospace)).size.height
    val address = measurer.measure("0", typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)).size.height
    val padding = dimensionResource(DesR.dimen.padding_l) * 2 + dimensionResource(TvR.dimen.tv_button_border_width) * 2
    val gaps = dimensionResource(DesR.dimen.padding_s) * 2
    return with(density) { (pin + address).toDp() } + padding + gaps
}

private fun String.spaced(): String = toList().joinToString(" ")

private fun AddressHandOff.Reason.messageRes(): Int =
    when (this) {
        AddressHandOff.Reason.NoLocalNetwork -> R.string.tv_handoff_no_network
        AddressHandOff.Reason.CouldNotListen -> R.string.tv_handoff_could_not_listen
    }
