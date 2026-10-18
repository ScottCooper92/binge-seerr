package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.binge.designsystem.testing.assertNoTextClipped
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.theme.SeerrTvTheme
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistItem
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestDownload
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import org.junit.Assert.assertTrue

/*
 * The app's own rows at their worst: every optional line filled and long text, for the no-clipping test to render at
 * font scales 1.3 and 2.0. A row that is fine on a short title at the default size is the usual way a clip ships.
 */

internal const val LONG_TITLE = "The Lord of the Rings: The Rings of Power, Extended Edition with Every Commentary Track"
internal const val LONG_NAME = "Alexandria Fitzgerald-Montgomery"

/** [LONG_NAME] with a suffix, for a title, a dialog or the signed-in shell's top bar, where a name sits in a longer line. */
internal const val LONG_ROW_NAME = "$LONG_NAME of the Upstairs Server"
private const val LONG_TEXT =
    "The subtitles drift out of sync starting around the midpoint of the episode and get progressively worse until the end"
private const val LONG_AGO_MILLIS = 1_759_000_000_000L

/** A request with a wrapping title, a long status, 4K, a year, seasons, a requester and a running download. */
internal fun worstCaseRequest(): RequestItem =
    RequestItem(
        id = 1,
        tmdbId = 95_396,
        mediaType = RequestMediaType.Tv,
        title = LONG_TITLE,
        posterUrl = null,
        year = "2022",
        requestedBy = LONG_NAME,
        requestedById = 4,
        requestedAtMillis = LONG_AGO_MILLIS,
        status = SeerrRequestStatusCode.Approved,
        mediaStatus = SeerrMediaStatusCode.PartiallyAvailable,
        download = RequestDownload(fraction = 0.6f, etaMinutes = 95, downloading = true),
        seasonNumbers = listOf(1, 2, 4, 5, 6),
        is4k = true,
    )

/** An issue with a wrapping title, a long problem line, a season and episode, a long reporter and a comment count. */
internal fun worstCaseIssue(): IssueItem =
    IssueItem(
        id = 1,
        tmdbId = 95_396,
        mediaType = RequestMediaType.Tv,
        title = LONG_TITLE,
        posterUrl = null,
        year = "2022",
        type = IssueType.Subtitles,
        status = IssueStatus.Open,
        reportedBy = LONG_NAME,
        reportedById = 4,
        commentCount = 128,
        createdAtMillis = LONG_AGO_MILLIS,
        updatedAtMillis = LONG_AGO_MILLIS,
        problem = LONG_TEXT,
        problemSeason = 12,
        problemEpisode = 24,
    )

/** A blocklist entry with a wrapping title, a long blocker and enough tags to wrap onto several lines. */
internal fun worstCaseBlocklistItem(): BlocklistItem =
    BlocklistItem(
        id = 1,
        tmdbId = 95_396,
        mediaType = RequestMediaType.Tv,
        title = LONG_TITLE,
        posterUrl = null,
        year = "2017",
        addedBy = LONG_NAME,
        addedAtMillis = LONG_AGO_MILLIS,
        tags = listOf("Not available", "Adult content", "Anime", "Sports", "4K discovery", "Documentary"),
    )

/** A user with a long name, a long email and handle, an admin's permissions and a large request count. */
internal fun worstCaseUser(): UserItem =
    UserItem(
        id = 7,
        name = LONG_NAME,
        email = "alexandria.fitzgerald-montgomery@a-rather-long-domain-name.example.com",
        handle = "alexandria.fitzgerald-montgomery",
        avatarUrl = null,
        origin = UserOrigin.Plex,
        permissions = 2,
        requestCount = 12_840,
        createdAtMillis = LONG_AGO_MILLIS,
    )

/** The phone width the guard tests render at. */
internal val CLIPPING_PHONE_WIDTH = 412.dp

/** The two font scales the guard tests render at: a common large setting, and the largest. */
internal const val FONT_SCALE_13 = 1.3f
internal const val FONT_SCALE_20 = 2.0f

/** The fewest text nodes a guard test must have examined, so a test that checked nothing cannot pass. */
private const val MIN_TEXT_NODES_CHECKED = 3

/**
 * Renders [content] in the app's theme at [fontScale], in a phone-width box, and fails if any of its text is clipped by a
 * parent. Native graphics, which the test class asks for, gives the real text metrics this needs.
 */
internal fun ComposeContentTestRule.assertNoClippingAt(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
            SeerrTheme { Box(Modifier.width(CLIPPING_PHONE_WIDTH)) { content() } }
        }
    }
    waitForIdle()
    val checked = assertNoTextClipped()
    assertTrue("only $checked text nodes were checked", checked >= MIN_TEXT_NODES_CHECKED)
}

/**
 * The largest font scale a TV screen is held to: a television is read from across the room, so it stops at 1.3
 * (binge-seerr#1492).
 */
internal const val TV_MAX_FONT_SCALE = FONT_SCALE_13

/**
 * [assertNoClippingAt] for a television screen: the TV theme and the whole of its viewport (a test sets 960 by 540dp with
 * its `qualifiers`), so a page that scrolls is checked on its first screen. TV stops at 1.3 because it is read from
 * across the room (Scott, #1492).
 */
internal fun ComposeContentTestRule.assertNoTvClippingAt(
    fontScale: Float,
    /** Brings the screen to the state worth checking, such as a control focused, once it has first settled. */
    settle: ComposeContentTestRule.() -> Unit = {},
    /** The fewest text nodes the check must reach, for a screen that holds less text than most, such as a code to scan. */
    minTextNodes: Int = MIN_TEXT_NODES_CHECKED,
    content: @Composable () -> Unit,
) {
    require(fontScale <= TV_MAX_FONT_SCALE) { "a TV screen is checked up to $TV_MAX_FONT_SCALE, not $fontScale" }
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
            SeerrTvTheme { Box(Modifier.fillMaxSize()) { content() } }
        }
    }
    waitForIdle()
    settle()
    waitForIdle()
    val checked = assertNoTextClipped()
    assertTrue("only $checked text nodes were checked", checked >= minTextNodes)
}
