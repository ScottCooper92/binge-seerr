package io.github.scottcooper92.binge.seerr.ui.users.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TITLE = "Requested by"
private const val ANYONE = "Anyone"

/**
 * The pick-several row on the design system's multi-choice sheet, in its apply-as-picked mode (#1120): each tick and
 * Clear reach the editor as one toggle per value, a choice's mark shows, and only a long list gets the filter.
 */
@RunWith(RobolectricTestRunner::class)
class MultiChoiceSettingItemTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val toggled = mutableListOf<Int>()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val users =
        listOf(BingeChoice(8, "Ana Lima", mark = "AL"), BingeChoice(9, "Bo Diaz", mark = "BD"), BingeChoice(10, "Cy Ng", mark = "CN"))

    private fun show(
        choices: List<BingeChoice<Int>>,
        initial: Set<Int> = setOf(8),
    ) {
        rule.setContent {
            var selected by remember { mutableStateOf(initial) }
            SeerrTheme {
                ItemGroup(
                    title = "Conditions",
                    rows =
                        listOf(
                            multiChoiceSettingItem(
                                icon = Icons.Filled.Group,
                                title = TITLE,
                                choices = choices,
                                selected = selected,
                                enabled = true,
                                emptyLabel = ANYONE,
                                onToggle = { value ->
                                    toggled += value
                                    selected = if (value in selected) selected - value else selected + value
                                },
                            ),
                        ),
                )
            }
        }
        rule.onNodeWithText(TITLE).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `a tick applies at once, as one toggle`() {
        show(users)

        rule.onNodeWithText("Bo Diaz").performClick()
        rule.waitForIdle()

        assertEquals(listOf(9), toggled)
    }

    @Test
    fun `clear toggles off every pick`() {
        show(users, initial = setOf(8, 10))

        rule.onNodeWithText(context.getString(R.string.server_settings_list_clear)).performClick()
        rule.waitForIdle()

        assertEquals(setOf(8, 10), toggled.toSet())
        assertEquals(2, toggled.size)
    }

    @Test
    fun `a user's initials show as the choice's mark`() {
        show(users)

        rule.onNodeWithText("BD").assertExists()
    }

    @Test
    fun `a short list has no filter`() {
        show(users)
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a long list gets the filter`() {
        show(List(12) { BingeChoice(it, "User $it") })

        rule.onNodeWithText(context.getString(R.string.settings_choices_filter)).assertExists()
    }
}
