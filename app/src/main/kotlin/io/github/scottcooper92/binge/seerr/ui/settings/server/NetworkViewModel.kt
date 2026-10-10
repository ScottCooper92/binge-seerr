package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.SavedDraft
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/** The network page: the switches, and on Seerr 3 the proxy and the DNS cache, saved as one record. */
@HiltViewModel
class NetworkViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        savedState: SavedStateHandle = SavedStateHandle(),
    ) : EditorViewModel<NetworkForm>(dispatcher) {
        /** A long form, kept across the process being killed (#1026). The proxy's password is not kept: the record's goes back in. */
        override val savedDraft =
            SavedDraft(
                savedState,
                NetworkForm.serializer(),
                scrub = { it.copy(proxy = it.proxy?.copy(password = "")) },
                restore = { kept, loaded -> kept.copy(proxy = kept.proxy?.copy(password = loaded.proxy?.password.orEmpty())) },
            )

        init {
            reload()
        }

        override suspend fun load(): NetworkForm = connection.api().networkSettings().toForm()

        override suspend fun write(draft: NetworkForm): NetworkForm = connection.api().updateNetworkSettings(draft.toDto()).toForm()

        override fun canSave(draft: NetworkForm): Boolean = draft.valid
    }
