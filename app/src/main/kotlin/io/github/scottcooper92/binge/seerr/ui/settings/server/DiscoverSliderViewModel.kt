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
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

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
        private var genresJob: Job? = null
        private var providersJob: Job? = null

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
            readFor(form)
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
            val previous = ready()?.draft?.type
            val keepsData = previous != null && previous.keepsDataFor(type)
            edit { it.copy(type = type, data = if (keepsData) it.data else "") }
            if (!keepsData) readFor(SliderForm(type = type))
        }

        /** Reads what the form's kind needs to name or offer its data: the names it holds, or the list it picks from. */
        private fun readFor(form: SliderForm) {
            when (form.type.dataKind) {
                SliderDataKind.Keywords -> keywords.name(form.data.tagIds())
                SliderDataKind.Genre -> loadGenres(form.type)
                SliderDataKind.Studio ->
                    form.data
                        .trim()
                        .toIntOrNull()
                        ?.let(studios::name)
                SliderDataKind.Network ->
                    form.data
                        .trim()
                        .toIntOrNull()
                        ?.let(::nameNetwork)
                SliderDataKind.Streaming ->
                    form.data.toStreamingPick().region.takeIf { it.isNotBlank() }?.let {
                        loadProviders(
                            form.type,
                            it,
                        )
                    }
                SliderDataKind.Text -> Unit
            }
        }

        fun toggleKeyword(keywordId: Int) = edit { it.copy(data = it.data.withIdToggled(keywordId)) }

        fun selectGenre(genreId: Int) = edit { it.copy(data = genreId.toString()) }

        fun searchStudios(query: String) = studios.search(query)

        /** Takes [company] as the slider's studio, and keeps its name so the row can show it. */
        fun selectStudio(company: Company) {
            editExtras { it.copy(studios = it.studios.copy(names = it.studios.names + (company.id to company.name))) }
            edit { it.copy(data = company.id.toString()) }
        }

        fun loadStudioName(id: Int) = studios.name(id)

        /** Names the network a typed id is, once; a failed read leaves the id as it is. */
        fun nameNetwork(id: Int) {
            if (id in currentExtras().networkNames) return
            viewModelScope.launch(dispatcher) {
                val name = attempt { connection.api().network(id) }.getOrNull()?.name?.takeIf { it.isNotBlank() }
                if (name != null) editExtras { it.copy(networkNames = it.networkNames + (id to name)) }
            }
        }

        /** Reads the streaming regions for the region sheet, once; a failed read can be asked for again. */
        fun loadRegions() = lists.load(ServerList.StreamingRegions)

        /** A new region has its own providers: the ones picked in the last are dropped and the new region's are read. */
        fun selectRegion(code: String) {
            val type = ready()?.draft?.type ?: return
            edit { form -> form.copy(data = StreamingPick(region = code).encode()) }
            if (code.isBlank()) editExtras { it.copy(providers = ProviderChoices.Idle) } else loadProviders(type, code)
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

        /** The providers TMDB lists in [region] for the kind's movies or TV; a newer region supersedes an older read. */
        private fun loadProviders(
            type: SliderType,
            region: String,
        ) {
            val segment = type.providerSegment ?: return
            providersJob?.cancel()
            editExtras { it.copy(providers = ProviderChoices.Loading) }
            providersJob =
                viewModelScope.launch(dispatcher) {
                    val providers =
                        attempt { connection.api().watchProviders(segment, region) }
                            .fold(
                                { list ->
                                    ProviderChoices.Ready(
                                        list
                                            .mapNotNull { dto ->
                                                dto.name?.takeIf { it.isNotBlank() }?.let { Choice(dto.id, it) }
                                            }.sortedBy { it.label.lowercase() },
                                    )
                                },
                                { ProviderChoices.Failed },
                            )
                    editExtras { it.copy(providers = providers) }
                }
        }

        fun searchKeywords(query: String) = keywords.search(query)

        fun loadKeywordNames(ids: List<Int>) = keywords.name(ids)

        /** The genres of a genre slider's kind, named in the device's language; a newer pick supersedes an older read. */
        private fun loadGenres(type: SliderType) {
            val segment = type.genreSegment ?: return
            genresJob?.cancel()
            editExtras { it.copy(genres = GenreChoices.Loading) }
            genresJob =
                viewModelScope.launch(dispatcher) {
                    val genres =
                        attempt { connection.api().genres(segment, Locale.getDefault().toLanguageTag()) }
                            .fold(
                                { list -> GenreChoices.Ready(list.mapNotNull { dto -> dto.name?.let { Choice(dto.id, it) } }) },
                                { GenreChoices.Failed },
                            )
                    editExtras { it.copy(genres = genres) }
                }
        }

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
