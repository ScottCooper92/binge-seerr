package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.ui.Choice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * What the slider editor reads from the server for the kind it is editing: the genres of a genre slider, the providers of a
 * region, the name of a network. A newer genre or provider read supersedes an older one; a failed read leaves its state as
 * `Failed`, or a network as the id it was typed as.
 */
internal class SliderReaders(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val api: suspend () -> SeerrApi,
    private val current: () -> SliderExtras,
    private val edit: ((SliderExtras) -> SliderExtras) -> Unit,
    private val nameKeywords: (List<Int>) -> Unit,
    private val nameStudio: (Int) -> Unit,
) {
    private var genresJob: Job? = null
    private var providersJob: Job? = null

    /** Reads what [form]'s kind needs to name or offer its data: the names it holds, or the list it picks from. */
    fun readFor(form: SliderForm) {
        val id = form.data.trim().toIntOrNull()
        when (form.type.dataKind) {
            SliderDataKind.Keywords -> nameKeywords(form.data.tagIds())
            SliderDataKind.Genre -> genres(form.type)
            SliderDataKind.Studio -> id?.let(nameStudio)
            SliderDataKind.Network -> id?.let(::network)
            SliderDataKind.Streaming -> {
                val region = form.data.toStreamingPick().region
                if (region.isBlank()) clearProviders() else providers(form.type, region)
            }
            SliderDataKind.Text -> Unit
        }
    }

    /** The genres of a genre slider's kind, named in the device's language. */
    fun genres(type: SliderType) {
        val segment = type.genreSegment ?: return
        genresJob?.cancel()
        edit { it.copy(genres = GenreChoices.Loading) }
        genresJob =
            scope.launch(dispatcher) {
                val genres =
                    attempt { api().genres(segment, Locale.getDefault().toLanguageTag()) }
                        .fold(
                            { list -> GenreChoices.Ready(list.mapNotNull { dto -> dto.name?.let { Choice(dto.id, it) } }) },
                            { GenreChoices.Failed },
                        )
                edit { it.copy(genres = genres) }
            }
    }

    /** Forgets the provider list and any read of it in flight: with no region there is no list to offer. */
    fun clearProviders() {
        providersJob?.cancel()
        edit { it.copy(providers = ProviderChoices.Idle) }
    }

    /** The providers TMDB lists in [region] for the kind's movies or TV. */
    fun providers(
        type: SliderType,
        region: String,
    ) {
        val segment = type.providerSegment ?: return
        providersJob?.cancel()
        edit { it.copy(providers = ProviderChoices.Loading) }
        providersJob =
            scope.launch(dispatcher) {
                val providers =
                    attempt { api().watchProviders(segment, region) }
                        .fold(
                            { list ->
                                ProviderChoices.Ready(
                                    list
                                        .mapNotNull { dto -> dto.name?.takeIf { it.isNotBlank() }?.let { Choice(dto.id, it) } }
                                        .sortedBy { it.label.lowercase() },
                                )
                            },
                            { ProviderChoices.Failed },
                        )
                edit { it.copy(providers = providers) }
            }
    }

    /** Names the network a typed id is, once; a failed read leaves the id as it is. */
    fun network(id: Int) {
        if (id in current().networkNames) return
        scope.launch(dispatcher) {
            val name = attempt { api().network(id) }.getOrNull()?.name?.takeIf { it.isNotBlank() }
            if (name != null) edit { it.copy(networkNames = it.networkNames + (id to name)) }
        }
    }
}
