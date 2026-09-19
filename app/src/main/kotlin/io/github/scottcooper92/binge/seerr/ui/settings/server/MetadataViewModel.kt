package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The metadata page: which provider serves series and which anime, and a test that reaches the ones the draft would use. */
@HiltViewModel
class MetadataViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ExtrasEditorViewModel<MetadataForm, MetadataExtras>(MetadataExtras(), dispatcher) {
        init {
            reload()
        }

        override suspend fun load(): MetadataForm = connection.api().metadataSettings().toForm()

        override suspend fun write(draft: MetadataForm): MetadataForm = connection.api().updateMetadataSettings(draft.toDto()).toForm()

        fun test() {
            val draft = ready()?.draft ?: return
            if (currentExtras().testing) return
            editExtras { it.copy(testing = true) }
            viewModelScope.launch(dispatcher) {
                val outcome = runCatching { connection.api().testMetadataProviders(draft.testBody()) }
                editExtras { it.copy(testing = false) }
                outcome
                    .onSuccess { notify(EditorEvent.Notice(R.string.server_settings_metadata_tested)) }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }
    }
