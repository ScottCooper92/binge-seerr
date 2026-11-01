package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.tv.component.TvPairingCard
import com.binge.designsystem.tv.component.TvPairingCardLoading
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.AddressHandOff

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
    // The pane's room sizes the code; the card is measured and then fits the height, so a short pane shrinks the code
    // rather than cutting off the PIN.
    FitQrCard(modifier = Modifier.weight(1f).fillMaxWidth()) { qrSize ->
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

private fun String.spaced(): String = toList().joinToString(" ")

private fun AddressHandOff.Reason.messageRes(): Int =
    when (this) {
        AddressHandOff.Reason.NoLocalNetwork -> R.string.tv_handoff_no_network
        AddressHandOff.Reason.CouldNotListen -> R.string.tv_handoff_could_not_listen
    }
