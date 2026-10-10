package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        SliderDataKind.Genre -> raw.trim().toIntOrNull()?.let { id -> names.genres[kind.genreSegment]?.get(id) ?: id.toString() } ?: raw
        SliderDataKind.Studio -> raw.trim().toIntOrNull()?.let { names.studios[it] ?: it.toString() } ?: raw
        SliderDataKind.Network -> raw.trim().toIntOrNull()?.let { names.networks[it] ?: it.toString() } ?: raw
        SliderDataKind.Streaming -> {
            val pick = raw.toStreamingPick()
            val named = kind.providerSegment?.let { names.providers[providerKey(it, pick.region)] }.orEmpty()
            listOfNotNull(
                pick.region.takeIf { it.isNotBlank() }?.let(regionName),
                pick.providerIds.joinToString(", ") { named[it] ?: it.toString() }.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
        SliderDataKind.Text -> raw
    }
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
        val keywords =
            custom.filter { it.type?.dataKind == SliderDataKind.Keywords }.flatMap { it.data.orEmpty().tagIds() }.distinct() -
                held.keywords.keys
        val studios =
            custom.filter { it.type?.dataKind == SliderDataKind.Studio }.mapNotNull { it.data?.trim()?.toIntOrNull() }.distinct() -
                held.studios.keys
        val networks =
            custom.filter { it.type?.dataKind == SliderDataKind.Network }.mapNotNull { it.data?.trim()?.toIntOrNull() }.distinct() -
                held.networks.keys
        val segments = custom.mapNotNull { it.type?.genreSegment }.distinct() - held.genres.keys
        val streaming =
            custom
                .mapNotNull { slider ->
                    val segment = slider.type?.providerSegment ?: return@mapNotNull null
                    val region =
                        slider.data
                            .orEmpty()
                            .toStreamingPick()
                            .region
                            .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    segment to region
                }.distinct()
                .filter { (segment, region) -> providerKey(segment, region) !in held.providers }
        if (keywords.isEmpty() && studios.isEmpty() && networks.isEmpty() && segments.isEmpty() && streaming.isEmpty()) return
        scope.launch(dispatcher) {
            val api = api()
            val language = Locale.getDefault().toLanguageTag()
            keywords.map { id -> async { named(id) { api.keyword(id).name } } }.awaitAll().filterNotNull().toMap().let { found ->
                if (found.isNotEmpty()) edit { it.copy(keywords = it.keywords + found) }
            }
            studios.map { id -> async { named(id) { api.studio(id).name } } }.awaitAll().filterNotNull().toMap().let { found ->
                if (found.isNotEmpty()) edit { it.copy(studios = it.studios + found) }
            }
            networks.map { id -> async { named(id) { api.network(id).name } } }.awaitAll().filterNotNull().toMap().let { found ->
                if (found.isNotEmpty()) edit { it.copy(networks = it.networks + found) }
            }
            segments
                .map { segment ->
                    async {
                        attempt { api.genres(segment, language) }.getOrNull()?.let { list ->
                            segment to list.mapNotNull { dto -> dto.name?.let { dto.id to it } }.toMap()
                        }
                    }
                }.awaitAll()
                .filterNotNull()
                .toMap()
                .let { found ->
                    if (found.isNotEmpty()) edit { it.copy(genres = it.genres + found) }
                }
            streaming
                .map { (segment, region) ->
                    async {
                        attempt { api.watchProviders(segment, region) }.getOrNull()?.let { list ->
                            providerKey(segment, region) to list.mapNotNull { dto -> dto.name?.let { dto.id to it } }.toMap()
                        }
                    }
                }.awaitAll()
                .filterNotNull()
                .toMap()
                .let { found ->
                    if (found.isNotEmpty()) edit { it.copy(providers = it.providers + found) }
                }
        }
    }

    private suspend fun named(
        id: Int,
        read: suspend () -> String?,
    ): Pair<Int, String>? = attempt { read() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { id to it }
}
