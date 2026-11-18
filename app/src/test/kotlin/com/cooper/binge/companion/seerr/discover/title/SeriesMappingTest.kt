package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.seerr.SeerrCastDto
import com.cooper.binge.companion.seerr.seerr.SeerrCreditsDto
import com.cooper.binge.companion.seerr.seerr.SeerrEpisodeDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestSummaryDto
import com.cooper.binge.companion.seerr.seerr.SeerrSeasonStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrTvDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrTvSeasonDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesMappingTest {
    private val seasons =
        listOf(
            SeerrTvSeasonDto(id = 10, seasonNumber = 0, name = "Specials", episodeCount = 3, airDate = "2010-01-01"),
            SeerrTvSeasonDto(id = 11, seasonNumber = 1, name = "Season 1", episodeCount = 10, airDate = "2011-04-17"),
            SeerrTvSeasonDto(id = 12, seasonNumber = 2, name = "Season 2", episodeCount = 10, airDate = "2012-04-01"),
            SeerrTvSeasonDto(id = 13, seasonNumber = 3, name = "Season 3", episodeCount = 0),
            SeerrTvSeasonDto(id = 14, seasonNumber = 4, name = "Season 4", episodeCount = 10),
        )

    private fun show(media: SeerrMediaInfoDto? = null) =
        SeerrTvDetailsDto(
            id = 1399,
            name = "A Show",
            firstAirDate = "2011-04-17",
            episodeRunTime = listOf(0, 55),
            seasons = seasons,
            mediaInfo = media,
            nextEpisodeToAir = SeerrEpisodeDto(name = "The Next One", airDate = "2026-12-01", episodeNumber = 3, seasonNumber = 4),
        )

    private fun map(
        dto: SeerrTvDetailsDto,
        settings: SeerrPublicSettings = SeerrPublicSettings(),
        mayRequest4k: Boolean = false,
    ) = dto.toSeriesDetails(
        region = "US",
        mayRequest = true,
        mayRequest4k = mayRequest4k,
        settings = settings,
        webUrl = "https://seerr.example/tv/1399",
    )

    @Test
    fun `seasons with no episode and the specials are left out unless the server lists specials`() {
        assertEquals(listOf(1, 2, 4), map(show()).seasons.map { it.number })
        assertEquals(listOf(0, 1, 2, 4), map(show(), SeerrPublicSettings(enableSpecialEpisodes = true)).seasons.map { it.number })
    }

    @Test
    fun `a partly available show reports each season and offers the rest`() {
        val media =
            SeerrMediaInfoDto(
                status = SeerrMediaStatusCode.PartiallyAvailable,
                seasons =
                    listOf(
                        SeerrSeasonStatusDto(1, SeerrMediaStatusCode.Available),
                        SeerrSeasonStatusDto(2, SeerrMediaStatusCode.Processing),
                    ),
            )

        val series = map(show(media))

        assertEquals(
            listOf(TitleAvailability.Available, TitleAvailability.Processing, TitleAvailability.None),
            series.seasons.map { it.standard },
        )
        assertTrue(series.standard.mayRequest)
        assertEquals(
            listOf(4),
            series.seasonChoices.standard
                .filter { it.isOpen }
                .map { it.number },
        )
    }

    @Test
    fun `a show whose every season is covered offers no request`() {
        val media =
            SeerrMediaInfoDto(
                seasons = listOf(1, 2, 4).map { SeerrSeasonStatusDto(it, SeerrMediaStatusCode.Available) },
            )

        assertFalse(map(show(media)).standard.mayRequest)
    }

    @Test
    fun `an open request covers its seasons but a declined or completed one does not`() {
        val open = SeerrRequestSummaryDto(1, SeerrRequestStatusCode.Pending, seasons = listOf(SeerrSeasonStatusDto(2)))
        val declined = SeerrRequestSummaryDto(2, SeerrRequestStatusCode.Declined, seasons = listOf(SeerrSeasonStatusDto(4)))
        val completed = SeerrRequestSummaryDto(3, SeerrRequestStatusCode.Completed, seasons = listOf(SeerrSeasonStatusDto(1)))

        val series = map(show(SeerrMediaInfoDto(requests = listOf(open, declined, completed))))

        assertEquals(listOf(TitleAvailability.None, TitleAvailability.Pending, TitleAvailability.None), series.seasons.map { it.standard })
    }

    @Test
    fun `4K is judged apart from standard`() {
        val media =
            SeerrMediaInfoDto(
                seasons = listOf(SeerrSeasonStatusDto(1, SeerrMediaStatusCode.Available, status4k = SeerrMediaStatusCode.Unknown)),
                requests =
                    listOf(
                        SeerrRequestSummaryDto(5, SeerrRequestStatusCode.Approved, is4k = true, seasons = listOf(SeerrSeasonStatusDto(2))),
                    ),
            )

        val series = map(show(media), mayRequest4k = true)

        assertEquals(listOf(TitleAvailability.None, TitleAvailability.Pending, TitleAvailability.None), series.seasons.map { it.fourK })
        assertEquals(
            listOf(TitleAvailability.Available, TitleAvailability.None, TitleAvailability.None),
            series.seasons.map { it.standard },
        )
    }

    @Test
    fun `without 4K there is no 4K version and no 4K season state`() {
        val series = map(show())

        assertNull(series.fourK)
        assertTrue(series.seasons.all { it.fourK == null })
    }

    @Test
    fun `the facts the server gave come through and an empty runtime is skipped`() {
        val series = map(show())

        assertEquals(55, series.episodeRuntimeMinutes)
        assertEquals("2011", series.firstAirYear)
        assertEquals("The Next One", series.nextEpisode?.name)
        assertTrue(series.partialRequests)
    }

    @Test
    fun `the partial requests setting is carried`() {
        assertFalse(map(show(), SeerrPublicSettings(partialRequestsEnabled = false)).partialRequests)
    }

    @Test
    fun `a person listed twice in the cast reads once`() {
        val dto =
            show().copy(
                credits = SeerrCreditsDto(cast = listOf(SeerrCastDto(1, "A", "One", null, 0), SeerrCastDto(1, "A", "Two", null, 1))),
            )

        assertEquals(listOf("One, Two"), map(dto).cast.map { it.role })
    }
}
