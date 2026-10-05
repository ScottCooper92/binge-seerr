package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestEntityMappingsTest {
    private val item =
        RequestItem(
            id = 7,
            tmdbId = 100,
            mediaType = RequestMediaType.Movie,
            title = "Heat",
            posterUrl = "https://image.tmdb.org/t/p/w342/heat.jpg",
            year = "1995",
            requestedBy = "scott",
            requestedById = 3,
            requestedAtMillis = 1L,
            status = SeerrRequestStatusCode.Approved,
            mediaStatus = SeerrMediaStatusCode.Processing,
            download = RequestDownload(fraction = 0.5f, etaMinutes = 12, downloading = true),
            seasonNumbers = listOf(1, 2),
            is4k = true,
            backdropUrl = "https://image.tmdb.org/t/p/w1280/heat-wide.jpg",
            overview = "A crew, a cop.",
            certification = "R",
        )

    /** The backdrop, synopsis and age rating ride the cached row, or a list read back from the cache would lose them. */
    @Test
    fun `a request keeps its backdrop, synopsis and age rating through the cache`() {
        assertEquals(item, item.toEntity(listKey = "all:added", orderIndex = 0).toRequestItem())
    }
}
