package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

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
    ) : ExtrasEditorViewModel<SliderForm, SliderExtras>(SliderExtras(), dispatcher) {
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
            readers.readFor(form)
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
            // Asked every time: what is already read is left alone, and a read that failed is tried again.
            readers.readFor(SliderForm(type = type, data = if (keepsData) draft.data else ""))
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
            viewModelScope.launch(dispatcher) {
                attempt { connection.api().deleteDiscoverSlider(existing) }
                    .onSuccess { notify(EditorEvent.Deleted) }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: Int?): DiscoverSliderViewModel
        }
    }
