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
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val id: Int?,
    ) : ExtrasEditorViewModel<SliderForm, SliderExtras>(SliderExtras(), dispatcher) {
        private var genresJob: Job? = null

        private val keywords =
            KeywordLookup(
                scope = viewModelScope,
                dispatcher = dispatcher,
                api = connection::api,
                current = { currentExtras().keywords },
                edit = { change -> editExtras { it.copy(keywords = change(it.keywords)) } },
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
            when (form.type.dataKind) {
                SliderDataKind.Keywords -> keywords.name(form.data.tagIds())
                SliderDataKind.Genre -> loadGenres(form.type)
                SliderDataKind.Text -> Unit
            }
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
         * A kind that keeps its data differently drops it: ids picked for one kind mean nothing to another, and a movie
         * genre is not a TV one. Keyword ids are the same for both of their kinds, so those stay.
         */
        fun selectType(type: SliderType) {
            val previous = ready()?.draft?.type
            val keepsData = previous != null && previous.dataKind == type.dataKind && previous.genreSegment == type.genreSegment
            edit { it.copy(type = type, data = if (keepsData) it.data else "") }
            if (type.dataKind == SliderDataKind.Genre && previous?.genreSegment != type.genreSegment) loadGenres(type)
        }

        fun toggleKeyword(keywordId: Int) = edit { it.copy(data = it.data.withIdToggled(keywordId)) }

        fun selectGenre(genreId: Int) = edit { it.copy(data = genreId.toString()) }

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
