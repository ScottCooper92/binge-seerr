package io.github.scottcooper92.binge.seerr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffLinks
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffTarget
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.ui.handoff.SendAddressActions
import io.github.scottcooper92.binge.seerr.ui.handoff.SendAddressSheet
import io.github.scottcooper92.binge.seerr.ui.handoff.SendAddressViewModel

/**
 * The phone's end of the TV address hand-off (#323): the page a television serves links here with
 * `seerr-companion://tv-handoff?to=<ip:port>&token=<token>`, and this fills in the best address it
 * knows for the connected server and sends what the user confirms to that TV, only on their tap.
 *
 * Exported, and reachable from any browser page, so it trusts nothing in the link: the ViewModel
 * refuses a target that is not a private address on this network, and sends nothing without the
 * user's tap. Its own Activity rather than a route in [MainActivity], so the one link a web page can
 * open is this one, and the notification links stay unexported. The in-app scan (#773) opens
 * [ScannedSendAddressActivity] instead, which no link can reach and which alone may offer the phone's session.
 */
@AndroidEntryPoint
open class SendAddressActivity : ComponentActivity() {
    /** Whether the sheet was opened by this app's own scan, the only route that may offer the phone's session. */
    protected open val scanned: Boolean = false

    private val viewModel: SendAddressViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<SendAddressViewModel.Factory> { it.create(intent?.dataString, scanned) }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SeerrTheme {
                SendAddressSheet(
                    state = state,
                    actions =
                        SendAddressActions(
                            onEnterPin = viewModel::enterPin,
                            onEdit = viewModel::editAddress,
                            onChooseSignIn = viewModel::chooseSignIn,
                            onSend = viewModel::send,
                            onEditSignIn = viewModel::editSignIn,
                            onSendSignIn = viewModel::sendSignIn,
                            onSendSession = viewModel::sendSession,
                            onClose = ::finish,
                        ),
                )
            }
        }
    }

    companion object {
        /** The unexported [ScannedSendAddressActivity] for a code the app scanned itself, the link a TV's page opens, key included. */
        fun intent(
            context: Context,
            target: TvHandOffTarget,
        ): Intent =
            Intent(context, ScannedSendAddressActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(TvHandOffLinks.appLink(target)))
    }
}

/**
 * The send sheet for a code this app scanned (#773). Unexported, so only this app can start it: it is what lets the
 * sheet offer the phone's session, which a link from a web page must never get sent to a key it chose.
 */
@AndroidEntryPoint
class ScannedSendAddressActivity : SendAddressActivity() {
    override val scanned: Boolean = true
}
