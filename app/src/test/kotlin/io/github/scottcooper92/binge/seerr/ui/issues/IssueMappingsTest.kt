package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.data.IssueEntity
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IssueMappingsTest {
    @Test
    fun `a cached row reads back as the item it was stored from`() {
        val entity =
            IssueEntity(
                listKey = "open:added",
                id = 31,
                tmdbId = 100,
                mediaType = "Tv",
                title = "Severance",
                posterUrl = null,
                year = "2022",
                issueType = "Audio",
                status = "Resolved",
                reportedBy = "scott",
                reportedById = 7,
                commentCount = 2,
                createdAtMillis = 1L,
                updatedAtMillis = 2L,
                problem = "Desync",
                problemSeason = 2,
                problemEpisode = 5,
                orderIndex = 0,
                backdropUrl = "https://image.tmdb.org/t/p/w1280/sev.jpg",
                overview = "Mark leads a team.",
                certification = "TV-MA",
            )

        val item = entity.toIssueItem()

        assertEquals(RequestMediaType.Tv, item.mediaType)
        assertEquals(IssueType.Audio, item.type)
        assertEquals(IssueStatus.Resolved, item.status)
        assertEquals(7, item.reportedById)
        assertEquals(5, item.problemEpisode)
        assertEquals("https://image.tmdb.org/t/p/w1280/sev.jpg", item.backdropUrl)
        assertEquals("Mark leads a team.", item.overview)
        assertEquals("TV-MA", item.certification)
        assertEquals(entity, item.toEntity(listKey = "open:added", orderIndex = 0))
    }

    @Test
    fun `the open and resolved filters select on the stored status, and the whole list does not`() {
        assertEquals("Open", IssueFilter.Open.statusValue())
        assertEquals("Resolved", IssueFilter.Resolved.statusValue())
        assertNull(IssueFilter.All.statusValue())
    }

    @Test
    fun `only the resolved code reads as resolved, so an unknown state is never hidden from the open list`() {
        assertEquals(IssueStatus.Resolved, SeerrIssueStatusCode.Resolved.toIssueStatus())
        assertEquals(IssueStatus.Open, SeerrIssueStatusCode.Open.toIssueStatus())
        assertEquals(IssueStatus.Open, SeerrIssueStatusCode(9).toIssueStatus())
        assertEquals(IssueStatus.Open, null.toIssueStatus())
    }
}
