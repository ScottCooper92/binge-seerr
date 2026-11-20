package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The issues list's skeleton holds the first row where the loaded list puts it, in position and in size, at font scales of 1 and 2.
 * Not 1.3: an issue row's chips and text wrap in steps as the font grows, so no one skeleton height follows it between the two.
 * The anchor is the first row: the list fills the screen, so its own top never moves, and a plate that starts at the same corner
 * but is shorter than the row agrees on every edge a position check measures while every row below it moves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IssueListSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `on a phone the skeleton holds the first row where the list puts it`() = check()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2f)
    fun `on a phone at a font scale of 2 the skeleton holds the first row where the list puts it`() = check()

    private fun check() {
        val anchor = LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)
        rule.assertSkeletonReservesGeometry(anchors = listOf(anchor), checkSize = true) { resolved ->
            val rows = remember { MutableSharedFlow<PagingData<IssueItem>>(replay = 1) }
            LaunchedEffect(resolved) { if (resolved) rows.emit(PagingData.from(listOf(issue(1), issue(2)))) }
            SeerrTheme {
                IssuesBody(
                    filter = IssueFilter.Open,
                    lazyItems = rows.collectAsLazyPagingItems(),
                    lastRefresh = null,
                    now = NOW_MILLIS,
                    onOpen = {},
                    onPull = {},
                )
            }
        }
    }

    private fun issue(id: Int) =
        IssueItem(
            id = id,
            tmdbId = id,
            mediaType = RequestMediaType.Tv,
            title = "Severance",
            posterUrl = null,
            year = "2022",
            type = IssueType.Subtitles,
            status = IssueStatus.Open,
            reportedBy = "ana",
            reportedById = 3,
            commentCount = 2,
            createdAtMillis = null,
            updatedAtMillis = null,
            problem = "The subtitles drift out of sync",
            problemSeason = 1,
            problemEpisode = 4,
        )

    private companion object {
        const val NOW_MILLIS = 1_700_000_000_000L
    }
}
