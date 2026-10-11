package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.SavedStateHandle
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.ui.users.settings.EditorViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.SavedDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
        @IoDispatcher dispatcher: CoroutineDispatcher,
        savedState: SavedStateHandle = SavedStateHandle(),
    ) : EditorViewModel<TautulliForm>(dispatcher) {
        /** A connection form, kept across the process being killed (#1246). The API key is not kept: the record's goes back in. */
        override val savedDraft =
            SavedDraft(
                savedState,
                TautulliForm.serializer(),
                scrub = { it.copy(apiKey = "") },
                restore = { kept, loaded -> kept.copy(apiKey = loaded.apiKey) },
            )

        init {
            reload()
        }

        override suspend fun load(): TautulliForm = connection.api().tautulliSettings().toForm()

        override suspend fun write(draft: TautulliForm): TautulliForm = connection.api().updateTautulliSettings(draft.toDto()).toForm()

        override fun canSave(draft: TautulliForm): Boolean = draft.valid
    }
