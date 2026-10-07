package io.github.scottcooper92.binge.seerr.ui.handoff

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.SendAddressActivity
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffLinks
import io.github.scottcooper92.binge.seerr.seerr.AndroidLocalNetworkPermission
import io.github.scottcooper92.binge.seerr.seerr.LOCAL_NETWORK_PERMISSION

/**
 * The phone's in-app scan of a television's hand-off code (#773): Play Services' own scanner, so the app holds no
 * camera permission, then straight to the send sheet with the code's key, without the browser hop. Text that is
 * not a TV's code is ignored with a line saying so; the sheet still checks the target as it does for a page link.
 */
@Composable
internal fun rememberScanTvCode(): () -> Unit {
    val context = LocalContext.current
    // On SDK 37 both finding a TV and sending to it need the local-network permission, so the scan is where it's asked
    // for: the user has just said they want a TV on this network. The scan goes ahead whatever they answer.
    val askThenScan = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { scanTvCode(context) }
    return remember(context, askThenScan) {
        {
            if (AndroidLocalNetworkPermission(context).isGranted()) scanTvCode(context) else askThenScan.launch(LOCAL_NETWORK_PERMISSION)
        }
    }
}

private fun scanTvCode(context: Context) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning
        .getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val target = TvHandOffLinks.parseCode(barcode.rawValue)
            if (target == null) {
                Toast.makeText(context, R.string.scan_tv_not_a_tv_code, Toast.LENGTH_LONG).show()
            } else {
                context.startActivity(SendAddressActivity.intent(context, target))
            }
        }.addOnFailureListener {
            Toast.makeText(context, R.string.scan_tv_unavailable, Toast.LENGTH_LONG).show()
        }
}
