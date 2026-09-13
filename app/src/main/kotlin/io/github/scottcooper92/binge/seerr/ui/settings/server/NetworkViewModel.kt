package io.github.scottcooper92.binge.seerr.ui.settings.server

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import javax.inject.Inject

/** The network page: the switches, and on Seerr 3 the proxy and the DNS cache, saved as one record. */
@HiltViewModel
class NetworkViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : EditorViewModel<NetworkForm>() {
        init {
            reload()
        }

        override suspend fun load(): NetworkForm = connection.api().networkSettings().toForm()

        override suspend fun write(draft: NetworkForm): NetworkForm = connection.api().updateNetworkSettings(draft.toDto()).toForm()

        override fun canSave(draft: NetworkForm): Boolean = draft.valid
    }
