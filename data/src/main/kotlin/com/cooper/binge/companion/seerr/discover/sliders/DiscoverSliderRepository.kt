package com.cooper.binge.companion.seerr.discover.sliders

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.toWireBody
import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSlider
import com.cooper.binge.companion.seerr.ui.settings.server.SliderForm
import com.cooper.binge.companion.seerr.ui.settings.server.toBody
import com.cooper.binge.companion.seerr.ui.settings.server.toDto
import com.cooper.binge.companion.seerr.ui.settings.server.toForm
import com.cooper.binge.companion.seerr.ui.settings.server.toSlider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** What the slider list is now. The hubs and the slider editor read the same one. */
sealed interface SliderListState {
    /** Nothing has been read yet, or the server just changed. */
    data object Loading : SliderListState

    /** The list. [isDefault] is the server's own default set, for a server with no slider API. */
    data class Ready(
        val sliders: List<DiscoverSlider>,
        val isDefault: Boolean = false,
    ) : SliderListState

    /** The first read failed, so there is no list to show. A failed read after a good one keeps the good one. */
    data class Failed(
        val error: Throwable,
    ) : SliderListState
}

/**
 * The one copy of the server's slider list. The slider editor and the hubs observe [sliders], and every write here puts
 * the server's answer into it, so a hub already on the back stack shows a change made in the editor without a reload.
 * [refresh] reads it again, for a return to the foreground or a pull to refresh, which picks up a change made in the web
 * client. A write or a read that fails throws, as the call did, and leaves the list as it was.
 */
interface DiscoverSliderRepository {
    val sliders: StateFlow<SliderListState>

    /** Reads the list from the server, or the default set where it has no slider API, and publishes it. */
    suspend fun refresh(): List<DiscoverSlider>

    /** Replaces the whole list: its order and each slider's switch. */
    suspend fun save(draft: List<DiscoverSlider>): List<DiscoverSlider>

    /** Adds a custom slider. The server places it, so the list is read again. */
    suspend fun add(form: SliderForm): SliderForm

    suspend fun update(
        id: Int,
        form: SliderForm,
    ): SliderForm

    suspend fun delete(id: Int)

    /**
     * Puts the built-in sliders back in their default order and drops the custom ones. The server's answer is empty, so the list
     * is not read here: the caller reads it next with [refresh], as the editor does, and that is what reaches the other observers.
     */
    suspend fun reset()
}

/**
 * [DiscoverSliderRepository] over the Seerr API. [serverChanges] emits when the saved server or its sign-in changes, and
 * the list goes back to loading so another server never shows this one's. It is a single-server list, as the app is; in the
 * shared library it would be keyed by the server. [io] is the dispatcher the calls run on, with no default.
 */
class SeerrDiscoverSliderRepository(
    private val api: suspend () -> SeerrApi,
    private val profile: suspend () -> SeerrServerProfile,
    serverChanges: Flow<Any?>,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : DiscoverSliderRepository {
    private val state = MutableStateFlow<SliderListState>(SliderListState.Loading)
    override val sliders: StateFlow<SliderListState> = state.asStateFlow()

    init {
        // The first value is the server already saved, and is no change.
        scope.launch { serverChanges.distinctUntilChanged().drop(1).collect { state.value = SliderListState.Loading } }
    }

    override suspend fun refresh(): List<DiscoverSlider> =
        attempt { read() }
            .onSuccess { (list, isDefault) -> state.value = SliderListState.Ready(list, isDefault) }
            .onFailure { failure -> state.update { if (it is SliderListState.Ready) it else SliderListState.Failed(failure) } }
            .getOrThrow()
            .first

    private suspend fun read(): Pair<List<DiscoverSlider>, Boolean> =
        withContext(io) {
            if (profile().hasDiscoverSliders) api().discoverSliders().mapNotNull { it.toSlider() } to false else defaultSliders() to true
        }

    override suspend fun save(draft: List<DiscoverSlider>): List<DiscoverSlider> {
        val saved = withContext(io) { api().updateDiscoverSliders(draft.map { it.toDto() }.toWireBody()).mapNotNull { it.toSlider() } }
        state.value = SliderListState.Ready(saved)
        return saved
    }

    override suspend fun add(form: SliderForm): SliderForm {
        val added = withContext(io) { api().addDiscoverSlider(form.toBody()) }.toForm()
        // The new slider's place is the server's to say, so the list is read again; a failed read leaves the last one for the next.
        attempt { refresh() }
        return added
    }

    override suspend fun update(
        id: Int,
        form: SliderForm,
    ): SliderForm {
        val answered = withContext(io) { api().updateDiscoverSlider(id, form.toBody()) }
        answered.toSlider()?.let { updated -> replace(updated) }
        return answered.toForm()
    }

    override suspend fun delete(id: Int) {
        withContext(io) { api().deleteDiscoverSlider(id) }
        state.update { current ->
            if (current is SliderListState.Ready) current.copy(sliders = current.sliders.filterNot { it.id == id }) else current
        }
    }

    override suspend fun reset() {
        withContext(io) {
            val response = api().resetDiscoverSliders()
            if (!response.isSuccessful) throw HttpException(response)
        }
    }

    private fun replace(updated: DiscoverSlider) =
        state.update { current ->
            if (current is SliderListState.Ready && current.sliders.any { it.id == updated.id }) {
                current.copy(sliders = current.sliders.map { if (it.id == updated.id) updated else it })
            } else {
                current
            }
        }
}
