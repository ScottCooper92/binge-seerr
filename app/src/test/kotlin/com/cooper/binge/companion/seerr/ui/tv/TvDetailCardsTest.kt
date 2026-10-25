package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvInfoGroup
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.requests.DetailDownload
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.RequestDestination
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.SeasonState
import com.cooper.binge.companion.seerr.ui.state.DownloadActivity
import com.cooper.binge.companion.seerr.ui.tv.hub.quotaLine
import com.cooper.binge.companion.seerr.ui.tv.requests.TvDownloadCard
import com.cooper.binge.companion.seerr.ui.tv.requests.TvSeasonCard
import com.cooper.binge.companion.seerr.ui.tv.requests.requestInfoGroups
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TITLE = "Heat.1995.2160p"

/**
 * The TV detail and account cards' own rules (#1054): which facts a request page lists, a season and a download
 * with less to say, and the account sheet's quota line in each of its states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvDetailCardsTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    /** A destination the server named nothing for has no server, profile or folder groups, but its tags still get theirs. */
    @Test
    fun aDestinationWithNoNamesIsDroppedAndItsTagsKept() {
        val destination = RequestDestination(serverName = null, profileName = null, rootFolder = null, tags = listOf("4k", "kids"))
        val groups = infoGroups(detail(destination))

        assertNull(groups.labelled(R.string.request_server))
        assertNull(groups.labelled(R.string.request_profile))
        assertNull(groups.labelled(R.string.request_root_folder))
        assertEquals("4k, kids", groups.labelled(R.string.request_tags)?.value)
    }

    @Test
    fun aNamedDestinationGetsAGroupPerName() {
        val destination = RequestDestination(serverName = "Radarr", profileName = "Ultra-HD", rootFolder = null, tags = emptyList())
        val groups = infoGroups(detail(destination))

        assertEquals("Radarr", groups.labelled(R.string.request_server)?.value)
        assertEquals("Ultra-HD", groups.labelled(R.string.request_profile)?.value)
        assertNull(groups.labelled(R.string.request_root_folder))
        assertNull(groups.labelled(R.string.request_tags))
    }

    /** Each version the server keeps reads as its own group, and a version it has no status for is left out. */
    @Test
    fun eachVersionTheServerKeepsGetsItsStatus() {
        val media =
            MediaRecord(
                mediaId = 9,
                isTv = false,
                instances =
                    listOf(
                        instance(is4k = false, status = SeerrMediaStatusCode.Available),
                        instance(is4k = true, status = null),
                    ),
                canSetStatus = false,
                canClearData = false,
                canDeleteFiles = false,
            )
        val groups = infoGroups(requestDetail(media = media))

        assertEquals(string(R.string.media_state_available), groups.labelled(R.string.media_instance_standard)?.value)
        assertNull(groups.labelled(R.string.settings_service_4k))
    }

    /** A processing version reads Downloading only while its queue transfers (#1719). */
    @Test
    fun aProcessingVersionWithNothingQueuedIsNotDownloading() {
        val media =
            MediaRecord(
                mediaId = 9,
                isTv = false,
                instances =
                    listOf(
                        instance(is4k = false, status = SeerrMediaStatusCode.Processing),
                        instance(is4k = true, status = SeerrMediaStatusCode.Processing).copy(download = DownloadActivity.Downloading),
                    ),
                canSetStatus = false,
                canClearData = false,
                canDeleteFiles = false,
            )
        val groups = infoGroups(requestDetail(media = media))

        assertEquals(string(R.string.media_state_not_downloading), groups.labelled(R.string.media_instance_standard)?.value)
        assertEquals(string(R.string.media_state_downloading), groups.labelled(R.string.settings_service_4k)?.value)
    }

    @Test
    fun aSeasonTheServerDidNotNameIsNumbered() {
        composeTestRule.setContent {
            BingeTvTheme {
                TvSeasonCard(
                    SeasonState(number = 2, name = null, episodeCount = null, status = null),
                    isFocused = false,
                    onFocusChanged = {},
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.request_season_number, 2)).assertExists()
    }

    /** With neither a size nor an ETA, the card is the title and its bar, with no empty line under them. */
    @Test
    fun aDownloadWithNoSizeOrEtaHasNoDetailLine() {
        composeTestRule.setContent {
            BingeTvTheme {
                TvDownloadCard(
                    DetailDownload(title = TITLE, fraction = 0.4f, totalBytes = null, etaMinutes = null),
                    isFocused = false,
                    onFocusChanged = {},
                )
            }
        }

        val texts =
            composeTestRule
                .onNode(hasText(TITLE))
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Text)
        assertEquals(listOf(TITLE), texts?.map { it.text })
    }

    @Test
    fun aSpentQuotaSaysNoneLeft() {
        setQuota(HubQuotaBucket(limit = 5, remaining = 0, days = 7), loaded = true)

        composeTestRule.onNodeWithText(string(R.string.tv_hub_quota_none_left), substring = true).assertExists()
        composeTestRule.onNodeWithText("0 of 5", substring = true).assertDoesNotExist()
    }

    @Test
    fun aPartUsedQuotaSaysWhatItIsOutOf() {
        setQuota(HubQuotaBucket(limit = 5, remaining = 3, days = 7), loaded = true)

        composeTestRule.onNodeWithText("3 of 5 left", substring = true).assertExists()
        composeTestRule.onNodeWithText("Every 7 days", substring = true).assertExists()
    }

    @Test
    fun anUnlimitedQuotaSaysSo() {
        setQuota(bucket = null, loaded = true)

        composeTestRule.onNodeWithText(string(R.string.hub_quota_unlimited)).assertExists()
    }

    /** Before the quota loads the tile holds its place, so the row does not jump when the numbers land. */
    @Test
    fun aQuotaThatHasNotLoadedShowsAPlaceholder() {
        setQuota(HubQuotaBucket(limit = 5, remaining = 3, days = 7), loaded = false)

        composeTestRule.onNodeWithText(string(R.string.hub_stat_placeholder)).assertExists()
        composeTestRule.onNodeWithText("3 of 5", substring = true).assertDoesNotExist()
    }

    private fun setQuota(
        bucket: HubQuotaBucket?,
        loaded: Boolean,
    ) {
        composeTestRule.setContent { BingeTvTheme { Text(quotaLine(bucket, loaded)) } }
        composeTestRule.waitForIdle()
    }

    private fun infoGroups(detail: RequestDetail): List<TvInfoGroup> {
        var groups = emptyList<TvInfoGroup>()
        composeTestRule.setContent { BingeTvTheme { groups = requestInfoGroups(detail, now = 0L) } }
        composeTestRule.waitForIdle()
        return groups
    }

    private fun instance(
        is4k: Boolean,
        status: SeerrMediaStatusCode?,
    ) = MediaInstance(is4k = is4k, status = status, serviceUrl = null, mediaServerUrl = null, watch = null)

    private fun List<TvInfoGroup>.labelled(id: Int): TvInfoGroup? = firstOrNull { it.label == string(id) }

    private fun detail(destination: RequestDestination) = requestDetail(destination = destination, mediaId = null)
}
