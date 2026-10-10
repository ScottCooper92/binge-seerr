package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

/**
 * The slider list: the order and each slider's switch are the draft, saved as one batch, since
 * that is how the server takes them. A custom slider's own fields are edited on a page of its own;
 * reset asks the server for its defaults and reads the list again.
 */
@HiltViewModel
class DiscoverSlidersViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @ApplicationScope appScope: CoroutineScope,
    ) : EditorViewModel<List<DiscoverSlider>>(dispatcher) {
        /** The slider list saves as it changes (#930): order and on/off are small and easy to undo, and a drag is one write once it settles. */
        override val saveAsMadeScope: CoroutineScope = appScope

        override suspend fun load(): List<DiscoverSlider> = connection.api().discoverSliders().mapNotNull { it.toSlider() }

        override suspend fun write(draft: List<DiscoverSlider>): List<DiscoverSlider> =
            connection.api().updateDiscoverSliders(draft.map { it.toDto() }).mapNotNull { it.toSlider() }

        /** Moves the slider at [from] to [to] - a drag can cross more than one position in a gesture. */
        fun move(
            from: Int,
            to: Int,
        ) = edit { sliders ->
            if (from !in sliders.indices || to !in sliders.indices) return@edit sliders
            sliders.toMutableList().apply { add(to, removeAt(from)) }
        }

        fun toggle(id: Int) = edit { sliders -> sliders.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }

        /**
         * A change still waiting out its save delay is dropped at the tap, or it would be written over the reset (#1019). The
         * page then reads the list again on the main thread, as save-as-made needs, whether the reset went through or not:
         * either way it shows what the server holds.
         */
        fun reset() {
            discardUnsent()
            viewModelScope.launch {
                val result =
                    withContext(dispatcher) {
                        attempt {
                            val response = connection.api().resetDiscoverSliders()
                            if (!response.isSuccessful) throw HttpException(response)
                        }
                    }
                reload()
                result
                    .onSuccess { notify(EditorEvent.Notice(R.string.server_settings_sliders_reset_done)) }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }
    }
