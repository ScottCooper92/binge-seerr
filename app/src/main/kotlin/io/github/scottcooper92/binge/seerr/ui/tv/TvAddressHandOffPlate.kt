package io.github.scottcooper92.binge.seerr.ui.tv

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** The four-module quiet zone the QR specification asks for around a code. */
private const val QR_QUIET_ZONE = 4

/** How much of the room the card leaves it the code takes: a little under all of it, so it sits in the card rather than against it. */
private const val QR_FILL = 0.88f
private const val QR_DARK = 0xFF000000.toInt()
private const val QR_LIGHT = 0xFFFFFFFF.toInt()

/**
 * The television's half of "send the address from your phone" (#323), as the second pane of the address
 * page: what the phone needs to scan or type, and its way out. Mounted only while a hand-off is up.
 *
 * The TV listens only while this is on screen, so leaving it — Cancel, Back, the app going to the
 * background, or the page changing under it — stops the listener. Held outside the Listening and
 * Unavailable branches: a listener that fails while open swaps one for the other at one call site, and
 * effects inside a branch would dispose then and cancel the plate away.
 */
@Composable
internal fun TvHandOffLifecycle(onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val cancel by rememberUpdatedState(onCancel)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { cancel() }
    DisposableEffect(Unit) { onDispose { cancel() } }
}

/** The pane's content: the code, or why there is none. */
@Composable
internal fun ColumnScope.TvHandOffContent(handOff: AddressHandOff) {
    when (handOff) {
        is AddressHandOff.Listening -> TvHandOffCodeCard(url = handOff.url, scanUrl = handOff.scanUrl, modifier = Modifier.weight(1f))
        is AddressHandOff.Unavailable -> TvFormNote(stringResource(handOff.reason.messageRes()), tone = TvFormNoteTone.Error)
    }
}

/** The way out of the code, to the form. */
@Composable
internal fun TvHandOffTypeInstead(
    onClick: () -> Unit,
    arrival: TvArrivalFocus,
    enabled: Boolean,
) {
    TvButton(
        label = stringResource(R.string.tv_handoff_type_instead),
        onClick = onClick,
        style = TvButtonStyle.Primary,
        enabled = enabled,
        modifier = Modifier.tvArrivalTarget(arrival),
    )
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BingeShapes.AccountCard)
                .background(MaterialTheme.colorScheme.surface)
                .border(dimensionResource(TvR.dimen.tv_button_border_width), MaterialTheme.colorScheme.border, BingeShapes.AccountCard)
                .padding(dimensionResource(DesR.dimen.padding_m)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        // Square, so the code is as big as the shorter of what the card's height and width leave it.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            TvQrCode(scanUrl, minOf(maxWidth, maxHeight) * QR_FILL)
        }
        Text(
            text = url,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun AddressHandOff.Reason.messageRes(): Int =
    when (this) {
        AddressHandOff.Reason.NoLocalNetwork -> R.string.tv_handoff_no_network
        AddressHandOff.Reason.CouldNotListen -> R.string.tv_handoff_could_not_listen
    }

/** The code itself: dark on light whatever the theme, because that is what a phone's camera reads. */
@Composable
private fun TvQrCode(
    text: String,
    size: Dp,
) {
    val image = remember(text) { qrImage(text) }
    Image(
        bitmap = image,
        contentDescription = stringResource(R.string.tv_handoff_code_description),
        filterQuality = FilterQuality.None,
        modifier = Modifier.size(size),
    )
}

/** [text] as a QR code, one pixel per module with its quiet zone; the Image scales it up without smoothing. */
internal fun qrImage(text: String): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to QR_QUIET_ZONE, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) QR_DARK else QR_LIGHT }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
