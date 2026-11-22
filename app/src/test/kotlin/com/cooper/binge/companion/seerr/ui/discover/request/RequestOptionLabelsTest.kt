package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onNodeWithText
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What a picker row and the Tags row say: the default profile marked, a folder's room left, and the tags chosen. */
@RunWith(RobolectricTestRunner::class)
class RequestOptionLabelsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val choices =
        RequestChoices(
            profiles = listOf(RequestChoice(4, "HD"), RequestChoice(5, "Any")),
            rootFolders = listOf("/movies", "/more"),
            tags = listOf(RequestChoice(1, "family"), RequestChoice(2, "anime")),
            defaultProfileId = 4,
            freeSpace = mapOf("/movies" to FREE_BYTES),
        )

    private fun show(text: @Composable () -> String) = rule.setContent { Text(text()) }

    @Test
    fun `the server's default profile is marked and the others are not`() {
        show { choices.profileLabel(choices.profiles[0], anime = false) }
        rule.onNodeWithText("HD (default)").assertExists()
    }

    @Test
    fun `a profile that is not the default reads as its name`() {
        show { choices.profileLabel(choices.profiles[1], anime = false) }
        rule.onNodeWithText("Any").assertExists()
    }

    @Test
    fun `a folder with a reported room left says how much, in the size formatter's terms`() {
        show { choices.folderLabel("/movies") }
        rule.onNodeWithText("/movies · 1.2 TB free").assertExists()
    }

    @Test
    fun `a folder the server did not measure is just its path`() {
        show { choices.folderLabel("/more") }
        rule.onNodeWithText("/more").assertExists()
    }

    @Test
    fun `the tags row names the chosen tags, or None`() {
        show { choices.tagsSummary(setOf(1, 2)) }
        rule.onNodeWithText("family, anime").assertExists()
    }

    @Test
    fun `no tags chosen reads None`() {
        show { choices.tagsSummary(emptySet()) }
        rule.onNodeWithText("None").assertExists()
    }

    private companion object {
        /** 1.2 TB as the size formatter counts it, in 1024s. */
        const val FREE_BYTES = 1_319_413_953_331L
    }
}
