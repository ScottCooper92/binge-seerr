package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.seerr.SeerrProfileDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRootFolderDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDetailsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrTagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules the advanced picker and the request editor now share. Both decide which profile and
 * folder a request is sent against, so each case here is a destination a user could end up with.
 */
class DestinationChoicesTest {
    private val details =
        SeerrServerDetailsDto(
            profiles = listOf(SeerrProfileDto(id = 7, name = "HD"), SeerrProfileDto(id = 8, name = "SD")),
            rootFolders = listOf(SeerrRootFolderDto(id = 1, path = "/films"), SeerrRootFolderDto(id = 2, path = "/other")),
            tags = listOf(SeerrTagDto(id = 4, label = "kids"), SeerrTagDto(id = 5, label = "archive")),
        )

    @Test
    fun `a pick the instance still offers is the pick that stands`() {
        val filled =
            DestinationChoices(profileId = 8, rootFolder = "/other", tagIds = setOf(5), loadingChoices = true)
                .withChoices(details)

        assertEquals(8, filled.profileId)
        assertEquals("/other", filled.rootFolder)
        assertEquals(setOf(5), filled.tagIds)
        assertFalse(filled.loadingChoices)
    }

    @Test
    fun `a pick the instance does not offer falls to its first, and a tag it does not have is dropped`() {
        val filled =
            DestinationChoices(profileId = 99, rootFolder = "/gone", tagIds = setOf(4, 99)).withChoices(details)

        assertEquals(7, filled.profileId)
        assertEquals("/films", filled.rootFolder)
        assertEquals(setOf(4), filled.tagIds)
    }

    @Test
    fun `an instance that lists nothing leaves the pick alone rather than clearing it`() {
        val filled =
            DestinationChoices(profileId = 8, rootFolder = "/other").withChoices(SeerrServerDetailsDto())

        // An empty list is an instance that did not say, not one that said no: clearing here would
        // re-target a request the user never touched at whatever the instance defaults to.
        assertEquals(8, filled.profileId)
        assertEquals("/other", filled.rootFolder)
        assertTrue(filled.rootFolders.isEmpty())
    }

    @Test
    fun `moving to another instance takes its defaults and carries none of the first's choices`() {
        val moved =
            DestinationChoices(profileId = 8, rootFolder = "/other", tagIds = setOf(5))
                .withChoices(details)
                .onServer(SeerrServerDto(id = 2, name = "Spare", activeProfileId = 20, activeDirectory = "/spare"))

        assertEquals(2, moved.serverId)
        assertEquals(20, moved.profileId)
        assertEquals("/spare", moved.rootFolder)
        assertEquals(emptySet<Int>(), moved.tagIds)
        assertTrue(moved.profiles.isEmpty())
        assertTrue(moved.rootFolders.isEmpty())
        assertTrue(moved.tags.isEmpty())
        assertTrue(moved.loadingChoices)
    }

    @Test
    fun `an instance with no defaults of its own opens on nothing rather than on the last one's`() {
        val moved =
            DestinationChoices(profileId = 8, rootFolder = "/other").onServer(SeerrServerDto(id = 3, name = "Bare"))

        assertEquals(null, moved.profileId)
        assertEquals(null, moved.rootFolder)
    }
}
