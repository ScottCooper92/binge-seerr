package com.cooper.binge.companion.seerr.ui.search.title

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.hub.HubActions
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import com.cooper.binge.companion.seerr.ui.hub.HubScreen
import com.cooper.binge.companion.seerr.ui.hub.readyHub
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Search tab's field and the Settings tab's search entry draw their icon and text at one position under the two small bars, so
 * switching tabs does not move the bar's field. The text is compared because the fields' own containers carry no node of their own; both
 * rows share the design system's inner padding, so the same text position is the same field.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class SearchFieldPositionTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private var onSearch by mutableStateOf(true)

    private fun fieldBounds(): Pair<DpRect, DpRect> {
        rule.setContent {
            SeerrTheme {
                if (onSearch) {
                    TitleSearchScreen(
                        TitleSearchUiState.Idle(text = "", recents = emptyList(), trending = Trending.Unavailable),
                        TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}),
                    )
                } else {
                    HubScreen(
                        readyHub(HubOverview(loaded = true)),
                        HubActions(
                            onOpenSection = {},
                            onOpenAccount = {},
                            onOpenRequest = {},
                            onRetry = {},
                            onReconnect = {},
                            onDisconnect = {},
                            onDismissBingeHint = {},
                            onRefresh = {},
                        ),
                    )
                }
            }
        }
        rule.waitForIdle()
        val placeholder = ApplicationProvider.getApplicationContext<Context>().getString(R.string.title_search_placeholder)
        val search = rule.onNode(hasText(placeholder), useUnmergedTree = true).getBoundsInRoot()
        onSearch = false
        rule.waitForIdle()
        val settings = rule.onNode(hasText("Search", substring = true) and !hasClickAction(), useUnmergedTree = true).getBoundsInRoot()
        return search to settings
    }

    private fun assertSame(
        search: DpRect,
        settings: DpRect,
    ) {
        val expected = "left ${search.left.value} top ${search.top.value} height ${search.height.value}"
        val actual = "left ${settings.left.value} top ${settings.top.value} height ${settings.height.value}"
        assertEquals("Search $expected, Settings $actual", expected, actual)
    }

    @Test
    fun `the two search fields draw their text at one position`() {
        val (search, settings) = fieldBounds()

        assertSame(search, settings)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 1.3f)
    fun `they still do at a font scale of 1 point 3`() {
        val (search, settings) = fieldBounds()

        assertSame(search, settings)
    }
}
