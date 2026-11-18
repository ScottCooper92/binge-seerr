package com.cooper.binge.companion.seerr.ui.blocklist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The blocked title's open action: its own page with discovery on, even before the lookup gave a web address; as it was with discovery off. */
@RunWith(RobolectricTestRunner::class)
class BlocklistDetailOpensTitleTest {
    @get:Rule
    val compose = createSeerrComposeRule()

    private val opened = mutableListOf<Triple<TitleKind, Int, String>>()

    private fun show(
        discovery: Boolean,
        webUrl: String,
    ) {
        compose.setContent {
            BingeExpressiveTheme {
                CompositionLocalProvider(
                    LocalDiscoveryEnabled provides discovery,
                    LocalOpenTitle provides { kind, id, url -> opened += Triple(kind, id, url) },
                ) {
                    BlocklistDetailScreen(
                        state = state(webUrl),
                        events = MutableSharedFlow(),
                        actions = BlocklistDetailActions(onBack = {}, onUnblock = {}),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun state(webUrl: String) =
        BlocklistDetailUiState(
            item =
                BlocklistItem(
                    id = 1,
                    tmdbId = 603,
                    mediaType = RequestMediaType.Movie,
                    title = "The Matrix",
                    posterUrl = null,
                    year = "1999",
                    addedBy = "Admin",
                    addedAtMillis = null,
                    tags = emptyList(),
                ),
            canManage = true,
            webUrl = webUrl,
        )

    @Test
    fun `with discovery on the action opens the title's page before the lookup has a web address`() {
        show(discovery = true, webUrl = "")
        compose.onNodeWithContentDescription(string(R.string.request_open_title)).performClick()
        assertEquals(listOf(Triple(TitleKind.Movie, 603, "")), opened)
    }

    @Test
    fun `with discovery off the action waits for the web address and is labelled as it was`() {
        show(discovery = false, webUrl = "")
        assertEquals(0, compose.onAllNodesWithContentDescription(string(R.string.request_open_elsewhere)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription(string(R.string.request_open_title)).fetchSemanticsNodes().size)
    }

    @Test
    fun `with discovery off and a web address the action is the elsewhere one`() {
        show(discovery = false, webUrl = "https://seerr.example.com/movie/603")
        compose.onNodeWithContentDescription(string(R.string.request_open_elsewhere)).performClick()
        assertEquals(listOf(Triple(TitleKind.Movie, 603, "https://seerr.example.com/movie/603")), opened)
    }
}
