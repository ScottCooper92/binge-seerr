package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.discover.sliders.DiscoverSliderRepository
import com.cooper.binge.companion.seerr.feature.admin.R
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.ui.users.settings.PageEvent
import com.cooper.binge.companion.seerr.ui.users.settings.SettingsEditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The slider list: the order and each slider's switch are the draft, saved as one batch, since that is how the server
 * takes them. The names of what its custom sliders hold are read beside it, for their captions. A custom slider's own
 * fields are edited on a page of its own; reset asks the server for its defaults and reads the list again. It saves as
 * made: a drag is one write once it settles.
 */
@HiltViewModel
class DiscoverSlidersViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val sliders: DiscoverSliderRepository,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @ApplicationScope appScope: CoroutineScope,
    ) : SettingsEditorViewModel<List<DiscoverSlider>, SliderNames>(SliderNames(), dispatcher, appScope) {
        private val names =
            SliderNameLoader(
                scope = viewModelScope,
                dispatcher = dispatcher,
                api = connection::api,
                current = { currentExtras() },
                edit = { change -> editExtras { change(it) } },
            )

        /** The list, and then, in the background, the names of what its custom sliders hold, for their captions. */
        override suspend fun load(): List<DiscoverSlider> = sliders.refresh().also(names::name)

        override suspend fun write(draft: List<DiscoverSlider>): List<DiscoverSlider> = sliders.save(draft)

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
                    withContext(dispatcher) { attempt { sliders.reset() } }
                reload()
                result
                    .onSuccess { notify(PageEvent.Notice(R.string.server_settings_sliders_reset_done)) }
                    .onFailure { failure -> notify(PageEvent.Failed.from(failure)) }
            }
        }
    }
