package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.requests.DetailDownload
import com.cooper.binge.companion.seerr.ui.requests.RequestDestination
import com.cooper.binge.companion.seerr.ui.requests.SeasonState
import com.cooper.binge.companion.seerr.ui.tv.hub.quotaLine
import com.cooper.binge.companion.seerr.ui.tv.requests.TvDownloadCard
import com.cooper.binge.companion.seerr.ui.tv.requests.TvInfoCardItem
import com.cooper.binge.companion.seerr.ui.tv.requests.TvSeasonCard
import com.cooper.binge.companion.seerr.ui.tv.requests.requestInfoCards
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

    /** A destination the server named nothing for has no "Sent to" card, but its tags still get theirs. */
    @Test
    fun aDestinationWithNoNamesIsDroppedAndItsTagsKept() {
        val cards = infoCards(RequestDestination(serverName = null, profileName = null, rootFolder = null, tags = listOf("4k", "kids")))

        assertNull(cards.labelled(R.string.tv_detail_info_sent_to))
        assertEquals("4k, kids", cards.labelled(R.string.request_tags)?.value)
    }

    @Test
    fun aNamedDestinationReadsAsOneLine() {
        val cards = infoCards(RequestDestination(serverName = "Radarr", profileName = "Ultra-HD", rootFolder = null, tags = emptyList()))

        assertEquals("Radarr · Ultra-HD", cards.labelled(R.string.tv_detail_info_sent_to)?.value)
        assertNull(cards.labelled(R.string.request_tags))
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

    private fun infoCards(destination: RequestDestination): List<TvInfoCardItem> {
        var cards = emptyList<TvInfoCardItem>()
        composeTestRule.setContent { BingeTvTheme { cards = requestInfoCards(detail(destination), now = 0L) } }
        composeTestRule.waitForIdle()
        return cards
    }

    private fun List<TvInfoCardItem>.labelled(id: Int): TvInfoCardItem? = firstOrNull { it.label == string(id) }

    private fun detail(destination: RequestDestination) = requestDetail(destination = destination, mediaId = null)
}
