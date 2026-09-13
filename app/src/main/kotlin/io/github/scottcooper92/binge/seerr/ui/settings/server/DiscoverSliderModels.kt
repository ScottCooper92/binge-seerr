package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrDiscoverSliderBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrDiscoverSliderDto

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

    companion object {
        fun fromCode(code: Int): SliderType? = entries.firstOrNull { it.code == code }

        val customTypes: List<SliderType> get() = entries.filter { it.custom }
    }
}

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
    val valid: Boolean get() = title.isNotBlank() && data.isNotBlank()
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
