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
 * The issues list's skeleton holds the first row where the loaded list puts it. At a font scale of 1 its size is held too. At 1.3
 * and 2 only the position is: an issue row's chips and text wrap in steps as the font grows, which one skeleton height cannot
 * follow, so the size check at those scales arrives with the design system's text-hint option for skeleton rows.
 * The anchor is the first row: the list fills the screen, so its own top never moves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IssueListSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `on a phone the skeleton holds the first row where the list puts it`() = check(checkSize = true)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 1.3f)
    fun `on a phone at a font scale of 1 point 3 the skeleton holds the first row's position`() = check(checkSize = false)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2f)
    fun `on a phone at a font scale of 2 the skeleton holds the first row's position`() = check(checkSize = false)

    private fun check(checkSize: Boolean) {
        val anchor = LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)
        rule.assertSkeletonReservesGeometry(anchors = listOf(anchor), checkSize = checkSize) { resolved ->
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
