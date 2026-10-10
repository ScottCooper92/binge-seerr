package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * What the slider list has read to name what its custom sliders hold: keywords, studios and networks by id, the genres
 * of each kind of media, and the providers TMDB lists in a region. An id it could not name stays a number in the caption.
 */
data class SliderNames(
    val keywords: Map<Int, String> = emptyMap(),
    val studios: Map<Int, String> = emptyMap(),
    val networks: Map<Int, String> = emptyMap(),
    /** By the genre path segment (`movie` or `tv`). */
    val genres: Map<String, Map<Int, String>> = emptyMap(),
    /** By the provider path segment and the region, as `movies:US`. */
    val providers: Map<String, Map<Int, String>> = emptyMap(),
)

/** The key a region's providers are held under for a kind of media. */
internal fun providerKey(
    segment: String,
    region: String,
): String = "$segment:$region"

/**
 * What a custom slider holds, in words: its keywords, genre, studio or network by name, or its region and providers; an
 * id [names] lacks stays as a number, and a search is the text itself.
 */
internal fun DiscoverSlider.dataLabel(
    names: SliderNames,
    regionName: (String) -> String,
): String? {
    val kind = type ?: return data?.takeIf { it.isNotBlank() }
    val raw = data?.takeIf { it.isNotBlank() } ?: return null
    return when (kind.dataKind) {
        SliderDataKind.Keywords -> raw.tagIds().joinToString(", ") { names.keywords[it] ?: it.toString() }
        SliderDataKind.Genre -> raw.named(kind.genreSegment?.let { names.genres[it] })
        SliderDataKind.Studio -> raw.named(names.studios)
        SliderDataKind.Network -> raw.named(names.networks)
        SliderDataKind.Streaming -> streamingLabel(raw, kind, names, regionName)
        SliderDataKind.Text -> raw
    }
}

/** A stored id by its name in [byId], as the number where it has none; text that is not an id stays as it is. */
private fun String.named(byId: Map<Int, String>?): String = trim().toIntOrNull()?.let { byId?.get(it) ?: it.toString() } ?: this

private fun streamingLabel(
    raw: String,
    kind: SliderType,
    names: SliderNames,
    regionName: (String) -> String,
): String {
    val pick = raw.toStreamingPick()
    val named = kind.providerSegment?.let { names.providers[providerKey(it, pick.region)] }.orEmpty()
    return listOfNotNull(
        pick.region.takeIf { it.isNotBlank() }?.let(regionName),
        pick.providerIds.joinToString(", ") { named[it] ?: it.toString() }.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

/**
 * Names what the slider list's custom sliders hold, once each: a keyword, studio or network by its id, a kind's genres
 * and a region's providers by one read each. It runs after the list loads, in the background; the captions show ids until
 * a name arrives, and a failed read leaves them as ids. Nothing here saves.
 */
internal class SliderNameLoader(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val api: suspend () -> SeerrApi,
    private val current: () -> SliderNames,
    private val edit: ((SliderNames) -> SliderNames) -> Unit,
) {
    fun name(sliders: List<DiscoverSlider>) {
        val custom = sliders.filter { !it.builtIn && it.type != null && !it.data.isNullOrBlank() }
        val held = current()
        val keywords = custom.idsOf(SliderDataKind.Keywords) { it.tagIds() } - held.keywords.keys
        val studios = custom.idsOf(SliderDataKind.Studio) { listOfNotNull(it.trim().toIntOrNull()) } - held.studios.keys
        val networks = custom.idsOf(SliderDataKind.Network) { listOfNotNull(it.trim().toIntOrNull()) } - held.networks.keys
        val segments = custom.mapNotNull { it.type?.genreSegment }.distinct() - held.genres.keys
        val streaming = custom.regionsToRead().filter { (segment, region) -> providerKey(segment, region) !in held.providers }
        val nothing = listOf(keywords, studios, networks, segments, streaming).all { it.isEmpty() }
        if (nothing) return
        scope.launch(dispatcher) {
            val api = api()
            val language = Locale.getDefault().toLanguageTag()
            keywords.readAll { api.keyword(it).name }.then { edit { names -> names.copy(keywords = names.keywords + it) } }
            studios.readAll { api.studio(it).name }.then { edit { names -> names.copy(studios = names.studios + it) } }
            networks.readAll { api.network(it).name }.then { edit { names -> names.copy(networks = names.networks + it) } }
            segments
                .readAllBy { segment ->
                    attempt { api.genres(segment, language) }.getOrNull()?.let { list -> segment to list.idNames({ it.id }, { it.name }) }
                }.then { edit { names -> names.copy(genres = names.genres + it) } }
            streaming
                .readAllBy { (segment, region) ->
                    attempt { api.watchProviders(segment, region) }.getOrNull()?.let { list ->
                        providerKey(segment, region) to list.idNames({ it.id }, { it.name })
                    }
                }.then { edit { names -> names.copy(providers = names.providers + it) } }
        }
    }

    /** The ids the sliders of [kind] hold, read from their data by [ids]. */
    private fun List<DiscoverSlider>.idsOf(
        kind: SliderDataKind,
        ids: (String) -> List<Int>,
    ): List<Int> = filter { it.type?.dataKind == kind }.flatMap { ids(it.data.orEmpty()) }.distinct()

    /** The provider segment and region of each streaming slider that has picked a region. */
    private fun List<DiscoverSlider>.regionsToRead(): List<Pair<String, String>> =
        mapNotNull { slider ->
            val segment = slider.type?.providerSegment
            val region =
                slider.data
                    .orEmpty()
                    .toStreamingPick()
                    .region
            if (segment != null && region.isNotBlank()) segment to region else null
        }.distinct()

    private suspend fun List<Int>.readAll(read: suspend (Int) -> String?): Map<Int, String> =
        coroutineScope {
            map { id -> async { attempt { read(id) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { id to it } } }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }

    private suspend fun <T, R> List<T>.readAllBy(read: suspend (T) -> Pair<R, Map<Int, String>>?): Map<R, Map<Int, String>> =
        coroutineScope {
            map { item -> async { read(item) } }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }

    private fun <K, V> Map<K, V>.then(apply: (Map<K, V>) -> Unit) {
        if (isNotEmpty()) apply(this)
    }
}

/** The ids a list of TMDB entries carries, by their names; an entry without one is left out. */
private fun <T> List<T>.idNames(
    id: (T) -> Int,
    name: (T) -> String?,
): Map<Int, String> = mapNotNull { item -> name(item)?.let { id(item) to it } }.toMap()
