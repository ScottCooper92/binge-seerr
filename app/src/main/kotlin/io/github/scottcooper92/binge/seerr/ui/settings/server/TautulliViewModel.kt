package io.github.scottcooper92.binge.seerr.ui.settings.server

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import javax.inject.Inject

/**
 * Tautulli, beside a Plex server: the address and the key. The server reaches Tautulli with the
 * body before it saves, so a save that fails is a Tautulli that could not be reached or is too old.
 */
@HiltViewModel
class TautulliViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : EditorViewModel<TautulliForm>() {
        init {
            reload()
        }

        override suspend fun load(): TautulliForm = connection.api().tautulliSettings().toForm()

        override suspend fun write(draft: TautulliForm): TautulliForm = connection.api().updateTautulliSettings(draft.toDto()).toForm()

        override fun canSave(draft: TautulliForm): Boolean = draft.valid
    }
