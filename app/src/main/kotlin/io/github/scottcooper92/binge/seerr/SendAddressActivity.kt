package io.github.scottcooper92.binge.seerr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.ui.handoff.SendAddressActions
import io.github.scottcooper92.binge.seerr.ui.handoff.SendAddressSheet
import io.github.scottcooper92.binge.seerr.ui.handoff.SendAddressViewModel

/**
 * The phone's end of the TV address hand-off (#323): the page a television serves links here with
 * `seerr-companion://tv-handoff?to=<ip:port>&token=<token>`, and this offers the addresses it knows
 * for the connected server and sends the one the user picks to that TV, only on their tap.
 *
 * Exported, and reachable from any browser page, so it trusts nothing in the link: the ViewModel
 * refuses a target that is not a private address on this network, and sends nothing without the
 * user's tap. Its own Activity rather than a route in [MainActivity], so the one link a web page can
 * open is this one, and the notification links stay unexported.
 */
@AndroidEntryPoint
class SendAddressActivity : ComponentActivity() {
    private val viewModel: SendAddressViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<SendAddressViewModel.Factory> { it.create(intent?.dataString) }
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
                            onChoose = viewModel::choose,
                            onEditOther = viewModel::editOther,
                            onSend = viewModel::send,
                            onClose = ::finish,
                        ),
                )
            }
        }
    }
}
