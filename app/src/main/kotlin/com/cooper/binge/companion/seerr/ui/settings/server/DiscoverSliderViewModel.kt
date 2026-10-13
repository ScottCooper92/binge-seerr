package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.SavedDraft
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One custom slider, new ([id] null) or existing: its title, its kind, and what it queries. Keywords are picked by
 * search and a genre from the server's list, both kept as the ids the server stores; the other kinds are typed.
 */
@HiltViewModel(assistedFactory = DiscoverSliderViewModel.Factory::class)
class DiscoverSliderViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val listCatalog: ServerListCatalog,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val id: Int?,
        @ApplicationScope appScope: CoroutineScope,
        savedState: SavedStateHandle = SavedStateHandle(),
    ) : ExtrasEditorViewModel<SliderForm, SliderExtras>(SliderExtras(), dispatcher) {
        /**
         * A new slider's form, kept across the process being killed (#1246). It holds no credential. An existing slider
         * saves as it changes, so it has no unsaved draft to keep.
         */
        override val savedDraft = SavedDraft(savedState, SliderForm.serializer()).takeIf { id == null }

        /**
         * An existing slider saves as it changes (#930), each write the saved slider with the change in it. A change that
         * leaves it without a title or the data its kind needs (a new kind drops the old kind's data) is held until it is
         * whole again. A new slider keeps its explicit Create.
         */
        override val saveAsMadeScope: CoroutineScope? = appScope.takeIf { id != null }

        private val keywords =
            KeywordLookup(
                scope = viewModelScope,
                dispatcher = dispatcher,
                api = connection::api,
                current = { currentExtras().keywords },
                edit = { change -> editExtras { it.copy(keywords = change(it.keywords)) } },
            )

        private val studios =
            CompanyLookup(
                scope = viewModelScope,
                dispatcher = dispatcher,
                api = connection::api,
                current = { currentExtras().studios },
                edit = { change -> editExtras { it.copy(studios = change(it.studios)) } },
            )

        private val lists =
            ListChoicesLoader(
                scope = viewModelScope,
                dispatcher = dispatcher,
                catalog = listCatalog,
                held = { currentExtras().regions },
                set = { _, choices -> editExtras { it.copy(regions = choices) } },
            )

        private val readers =
            SliderReaders(
                scope = viewModelScope,
                dispatcher = dispatcher,
                api = connection::api,
                current = ::currentExtras,
                edit = { change -> editExtras(change) },
                nameKeywords = keywords::name,
                nameStudio = studios::name,
            )

        init {
            reload()
        }

        override suspend fun load(): SliderForm {
            val form =
                if (id == null) {
                    SliderForm()
                } else {
                    connection
                        .api()
                        .discoverSliders()
                        .firstOrNull { it.id == id }
                        ?.toForm() ?: throw NoSuchElementException("slider $id")
                }
            // What the page will show is the kept draft where there is one, so it is the draft that is named and offered.
            readers.readFor(savedDraft?.restoreOver(form) ?: form)
            return form
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

        /**
         * A kind that keeps its data differently drops it: ids picked for one kind mean nothing to another, a movie genre
         * is not a TV one, and a provider list is one kind's. Keyword ids are the same for both of their kinds, so those
         * stay.
         */
        fun selectType(type: SliderType) {
            val draft = ready()?.draft
            val keepsData = draft != null && draft.type.keepsDataFor(type)
            edit { it.copy(type = type, data = if (keepsData) it.data else "") }
            // A kind that dropped its data reads for the new one. A kind that kept it has its names and lists already, except
            // the genres, which are asked for again so that a read that failed is tried again.
            if (keepsData) readers.ensureGenres(type) else readers.readFor(SliderForm(type = type, data = ""))
        }

        fun toggleKeyword(keywordId: Int) = edit { it.copy(data = it.data.withIdToggled(keywordId)) }

        fun selectGenre(genreId: Int) = edit { it.copy(data = genreId.toString()) }

        fun searchStudios(query: String) = studios.search(query)

        /** Takes [company] as the slider's studio, and keeps its name so the row can show it. */
        fun selectStudio(company: Company) {
            editExtras { it.copy(studios = it.studios.copy(names = it.studios.names + (company.id to company.name))) }
            edit { it.copy(data = company.id.toString()) }
        }

        /** Reads the streaming regions for the region sheet, once; a failed read can be asked for again. */
        fun loadRegions() = lists.load(ServerList.StreamingRegions)

        /** A new region has its own providers: the ones picked in the last are dropped and the new region's are read. */
        fun selectRegion(code: String) {
            val type = ready()?.draft?.type ?: return
            edit { form -> form.copy(data = StreamingPick(region = code).encode()) }
            if (code.isBlank()) editExtras { it.copy(providers = ProviderChoices.Idle) } else readers.providers(type, code)
        }

        fun toggleProvider(providerId: Int) =
            edit {
                it.copy(
                    data =
                        it.data
                            .toStreamingPick()
                            .withProviderToggled(providerId)
                            .encode(),
                )
            }

        fun nameNetwork(id: Int) = readers.network(id)

        fun searchKeywords(query: String) = keywords.search(query)

        fun loadKeywordNames(ids: List<Int>) = keywords.name(ids)

        fun delete() {
            val existing = id ?: return
            // A change still waiting would otherwise be written to the slider as it goes.
            discardUnsent()
            // On the main thread, as save-as-made needs: only the request hops to the dispatcher.
            viewModelScope.launch {
                withContext(dispatcher) { attempt { connection.api().deleteDiscoverSlider(existing) } }
                    .onSuccess { notify(EditorEvent.Deleted) }
                    .onFailure { failure ->
                        notify(EditorEvent.Failed(failure.toSeerrError()))
                        // The slider is still there, so the change discarded above is owed to it again: sent now, and kept for a
                        // retry if that fails too.
                        if (ready()?.dirty == true) save()
                    }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: Int?): DiscoverSliderViewModel
        }
    }
