package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The issue page's header hands the title to whatever opens titles: its kind, its TMDB id and the issue's web address. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class IssueDetailOpensTitleTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `tapping the title in the header opens it as a series with its TMDB id`() {
        val detail = commonIssueDetail()
        val opened = mutableListOf<Triple<TitleKind, Int, String>>()
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalOpenTitle provides { kind, id, url -> opened += Triple(kind, id, url) }) {
                    IssueDetailScreen(
                        state = IssueDetailUiState.Ready(detail = detail),
                        events = emptyFlow(),
                        actions = noIssueDetailActions(),
                    )
                }
            }
        }

        // The title is also the page heading, which does nothing: the header row is the one that opens it.
        rule.onNode(hasText("Breaking Bad") and hasClickAction()).performClick()

        assertEquals(listOf(Triple(TitleKind.Series, 1396, detail.webUrl)), opened)
    }
}
