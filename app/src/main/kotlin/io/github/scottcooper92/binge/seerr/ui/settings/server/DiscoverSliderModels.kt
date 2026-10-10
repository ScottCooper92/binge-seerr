package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrDiscoverSliderBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrDiscoverSliderDto
import io.github.scottcooper92.binge.seerr.ui.Choice

/**
 * The server's `DiscoverSliderType`, by its number. The first twelve are the built-in rows; the
 * rest are the kinds an admin adds, each querying what its [data] holds. A number this app does not
 * know is kept as it is and shown by its number.
 */
enum class SliderType(
    val code: Int,
    val custom: Boolean = false,
) {
    RecentlyAdded(1),
    RecentRequests(2),
    Watchlist(3),
    Trending(4),
    PopularMovies(5),
    MovieGenres(6),
    UpcomingMovies(7),
    Studios(8),
    PopularTv(9),
    TvGenres(10),
    UpcomingTv(11),
    Networks(12),
    MovieKeyword(13, custom = true),
    TvKeyword(14, custom = true),
    MovieGenre(15, custom = true),
    TvGenre(16, custom = true),
    Studio(17, custom = true),
    Network(18, custom = true),
    Search(19, custom = true),
    MovieStreamingServices(20, custom = true),
    TvStreamingServices(21, custom = true),
    ;

    /** What this kind's [DiscoverSlider.data] holds, which decides how the editor asks for it. */
    val dataKind: SliderDataKind
        get() =
            when (this) {
                MovieKeyword, TvKeyword -> SliderDataKind.Keywords
                MovieGenre, TvGenre -> SliderDataKind.Genre
                Studio -> SliderDataKind.Studio
                Network -> SliderDataKind.Network
                MovieStreamingServices, TvStreamingServices -> SliderDataKind.Streaming
                else -> SliderDataKind.Text
            }

    /** The path segment of `GET genres/{type}` for a genre slider; null for any other kind. */
    val genreSegment: String?
        get() =
            when (this) {
                MovieGenre -> "movie"
                TvGenre -> "tv"
                else -> null
            }

    /** The path segment of `GET watchproviders/{type}` for a streaming slider; null for any other kind. */
    val providerSegment: String?
        get() =
            when (this) {
                MovieStreamingServices -> "movies"
                TvStreamingServices -> "tv"
                else -> null
            }

    /**
     * Whether data picked for this kind still means the same for [other]: the same sort of thing, and where the list it
     * came from depends on movies or TV (genres, providers), the same one. Keyword ids are the same for both kinds.
     */
    fun keepsDataFor(other: SliderType): Boolean =
        dataKind == other.dataKind && genreSegment == other.genreSegment && providerSegment == other.providerSegment

    companion object {
        fun fromCode(code: Int): SliderType? = entries.firstOrNull { it.code == code }

        val customTypes: List<SliderType> get() = entries.filter { it.custom }
    }
}

/**
 * How a custom slider's data is chosen: TMDB keyword ids picked by search, one genre by name, one studio by search, a
 * network by its id (TMDB has no network search, so the id is typed and then named), a region and its streaming
 * providers, or typed as the web client takes it (a search).
 */
enum class SliderDataKind { Keywords, Genre, Studio, Network, Streaming, Text }

/** One TMDB company, a studio a slider can hold. */
data class Company(
    val id: Int,
    val name: String,
)

/** The studio search: [names] for the ids a slider holds, and [results] for the last search, null before one. */
data class CompanySearch(
    val names: Map<Int, String> = emptyMap(),
    val results: List<Company>? = null,
    val searching: Boolean = false,
    val failed: Boolean = false,
)

/** The streaming providers TMDB lists in the slider's region, for its kind: being read, read, or failed. */
sealed interface ProviderChoices {
    /** No region yet, so nothing to ask for. */
    data object Idle : ProviderChoices

    data object Loading : ProviderChoices

    data class Ready(
        val providers: List<Choice>,
    ) : ProviderChoices

    data object Failed : ProviderChoices
}

/**
 * What the slider editor holds beside the form: the keyword and studio searches and names, the network names read for
 * typed ids, the genres and the streaming providers of the kind, and the streaming regions.
 */
data class SliderExtras(
    val keywords: KeywordSearch = KeywordSearch(),
    val genres: GenreChoices = GenreChoices.Loading,
    val studios: CompanySearch = CompanySearch(),
    val networkNames: Map<Int, String> = emptyMap(),
    val regions: ListChoices? = null,
    val providers: ProviderChoices = ProviderChoices.Idle,
)

/** A streaming slider's data: the region, then the provider ids, as the web client stores it (`US,8|337`). */
data class StreamingPick(
    val region: String = "",
    val providerIds: List<Int> = emptyList(),
) {
    /** The stored text; nothing picked is nothing stored. */
    fun encode(): String = if (region.isBlank() && providerIds.isEmpty()) "" else "$region,${providerIds.joinToString("|")}"

    fun withProviderToggled(id: Int): StreamingPick = copy(providerIds = if (id in providerIds) providerIds - id else providerIds + id)
}

/** Reads `US,8|337`: a region, then ids; anything missing is empty and anything that is not an id is skipped. */
internal fun String.toStreamingPick(): StreamingPick =
    StreamingPick(
        region = substringBefore(',').trim(),
        providerIds = substringAfter(',', "").split('|').mapNotNull { it.trim().toIntOrNull() },
    )

/** One slider as the list shows it; a built-in one has no [title] of its own. */
data class DiscoverSlider(
    val id: Int,
    val typeCode: Int,
    val title: String?,
    val builtIn: Boolean,
    val enabled: Boolean,
    val data: String?,
) {
    val type: SliderType? get() = SliderType.fromCode(typeCode)
}

/** A custom slider's form: what it is called, which kind, and the ids, code or search it queries. */
data class SliderForm(
    val id: Int? = null,
    val type: SliderType = SliderType.MovieKeyword,
    val title: String = "",
    val data: String = "",
) {
    /** A streaming slider needs a region and at least one provider to query; every other kind needs its data. */
    val valid: Boolean
        get() =
            title.isNotBlank() &&
                when (type.dataKind) {
                    SliderDataKind.Streaming -> data.toStreamingPick().let { it.region.isNotBlank() && it.providerIds.isNotEmpty() }
                    else -> data.isNotBlank()
                }
}

internal fun SeerrDiscoverSliderDto.toSlider(): DiscoverSlider? {
    val id = id ?: return null
    return DiscoverSlider(
        id = id,
        typeCode = type,
        title =
            title?.takeIf {
                it.isNotBlank()
            },
        builtIn = isBuiltIn,
        enabled = enabled,
        data = data,
    )
}

/** The record as the batch update takes it: every field back, with the order and the enabled state as edited. */
internal fun DiscoverSlider.toDto(): SeerrDiscoverSliderDto =
    SeerrDiscoverSliderDto(id = id, type = typeCode, title = title, isBuiltIn = builtIn, enabled = enabled, data = data)

/**
 * Fails for a slider whose type this app does not recognize: the editor has no way to show it, and
 * falling back to a known kind would, on the next unrelated edit, save that wrong kind over the
 * server's real one.
 */
internal fun SeerrDiscoverSliderDto.toForm(): SliderForm {
    val sliderType = SliderType.fromCode(type) ?: throw NoSuchElementException("slider type $type")
    return SliderForm(id = id, type = sliderType, title = title.orEmpty(), data = data.orEmpty())
}

internal fun SliderForm.toBody(): SeerrDiscoverSliderBody =
    SeerrDiscoverSliderBody(title = title.trim(), type = type.code, data = data.trim())
