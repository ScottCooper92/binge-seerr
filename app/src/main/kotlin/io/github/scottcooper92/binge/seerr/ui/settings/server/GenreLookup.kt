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
 * Reads the genres TMDB lists for movies or TV, named in the device's language. The override rule editor and the Discover
 * slider editor both pick from them; each keeps the answer in its own state, through [set], and nothing here saves.
 */
internal class GenreLookup(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val api: suspend () -> SeerrApi,
    private val current: () -> GenreChoices,
    private val set: (GenreChoices) -> Unit,
) {
    private var job: Job? = null
    private var segment: String? = null

    /**
     * Makes the list for [segment] (`movie` or `tv`) available: left alone where it has been read, and read again where it
     * has not. A read that failed is therefore tried again the next time the list is asked for.
     */
    fun ensure(segment: String) {
        if (segment == this.segment && current() is GenreChoices.Ready) return
        load(segment)
    }

    /** Reads the list for [segment]; a newer read supersedes an older one. */
    fun load(segment: String) {
        this.segment = segment
        job?.cancel()
        set(GenreChoices.Loading)
        job =
            scope.launch(dispatcher) {
                set(
                    attempt { api().genres(segment, Locale.getDefault().toLanguageTag()) }
                        .fold(
                            { list -> GenreChoices.Ready(list.mapNotNull { dto -> dto.name?.let { Choice(dto.id, it) } }) },
                            { GenreChoices.Failed },
                        ),
                )
            }
    }
}
