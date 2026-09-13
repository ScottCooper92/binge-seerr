package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One custom slider, new ([id] null) or existing: its title, its kind, and what it queries. */
@HiltViewModel(assistedFactory = DiscoverSliderViewModel.Factory::class)
class DiscoverSliderViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @Assisted private val id: Int?,
    ) : EditorViewModel<SliderForm>() {
        private val deletedState = MutableStateFlow(false)
        val deleted: StateFlow<Boolean> = deletedState.asStateFlow()

        init {
            reload()
        }

        override suspend fun load(): SliderForm =
            if (id == null) {
                SliderForm()
            } else {
                connection
                    .api()
                    .discoverSliders()
                    .firstOrNull { it.id == id }
                    ?.toForm() ?: throw NoSuchElementException("slider $id")
            }

        override suspend fun write(draft: SliderForm): SliderForm {
            val api = connection.api()
            val answered =
                if (draft.id ==
                    null
                ) {
                    api.addDiscoverSlider(draft.toBody())
                } else {
                    api.updateDiscoverSlider(draft.id, draft.toBody())
                }
            return answered.toForm()
        }

        override fun canSave(draft: SliderForm): Boolean = draft.valid

        fun delete() {
            val existing = id ?: return
            viewModelScope.launch {
                runCatching { connection.api().deleteDiscoverSlider(existing) }
                    .onSuccess { deletedState.value = true }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: Int?): DiscoverSliderViewModel
        }
    }
