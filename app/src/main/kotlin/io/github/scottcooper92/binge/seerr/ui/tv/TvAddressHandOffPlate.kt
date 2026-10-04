package io.github.scottcooper92.binge.seerr.ui.tv

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff

/** The four-module quiet zone the QR specification asks for around a code. */
private const val QR_QUIET_ZONE = 4
private const val QR_DARK = 0xFF000000.toInt()
private const val QR_LIGHT = 0xFFFFFFFF.toInt()

/**
 * The television's half of "send the address from your phone" (#323), in the style of
 * [TvSetupLinkPlate]: a code to scan, the same URL spelled out under it for a phone without a
 * camera app, and a way out.
 *
 * The TV listens only while this is on screen, so leaving it — Cancel, Back, the app going to the
 * background, or the page changing under it — stops the listener.
 */
@Composable
internal fun TvAddressHandOffPlate(
    handOff: AddressHandOff,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    BackHandler(onBack = onCancel)
    when (handOff) {
        is AddressHandOff.Listening -> {
            val cancel by rememberUpdatedState(onCancel)
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { cancel() }
            DisposableEffect(Unit) { onDispose { cancel() } }
            TvFormPage(
                headline = stringResource(R.string.tv_handoff_title),
                body = stringResource(R.string.tv_handoff_body),
                note = stringResource(R.string.tv_handoff_no_camera),
                icon = Icons.Filled.QrCode2,
                modifier = modifier,
            ) {
                TvQrCode(handOff.url)
                // Monospace for the same reason as the link plate's code: read across a room and typed on a phone.
                Text(text = handOff.url, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                TvFormNote(stringResource(R.string.tv_handoff_note))
                TvButton(
                    label = stringResource(R.string.link_cancel),
                    onClick = onCancel,
                    style = TvButtonStyle.Primary,
                    modifier = Modifier.tvArrivalTarget(arrival),
                )
            }
        }
        is AddressHandOff.Unavailable ->
            TvFormPage(
                headline = stringResource(R.string.tv_handoff_title),
                body = stringResource(handOff.reason.messageRes()),
                icon = Icons.Filled.QrCode2,
                modifier = modifier,
            ) {
                TvButton(
                    label = stringResource(R.string.tv_handoff_type_instead),
                    onClick = onCancel,
                    style = TvButtonStyle.Primary,
                    modifier = Modifier.tvArrivalTarget(arrival),
                )
            }
    }
}

private fun AddressHandOff.Reason.messageRes(): Int =
    when (this) {
        AddressHandOff.Reason.NoLocalNetwork -> R.string.tv_handoff_no_network
        AddressHandOff.Reason.CouldNotListen -> R.string.tv_handoff_could_not_listen
    }

/** The code itself: dark on light whatever the theme, because that is what a phone's camera reads. */
@Composable
private fun TvQrCode(text: String) {
    val image = remember(text) { qrImage(text) }
    Image(
        bitmap = image,
        contentDescription = stringResource(R.string.tv_handoff_code_description),
        filterQuality = FilterQuality.None,
        modifier = Modifier.size(dimensionResource(R.dimen.tv_handoff_qr_size)),
    )
}

/** [text] as a QR code, one pixel per module with its quiet zone; the Image scales it up without smoothing. */
internal fun qrImage(text: String): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to QR_QUIET_ZONE, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) QR_DARK else QR_LIGHT }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
