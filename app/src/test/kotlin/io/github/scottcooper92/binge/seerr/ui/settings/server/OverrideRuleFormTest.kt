package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val RULE_EXTRAS =
    OverrideRuleExtras(
        instances = listOf(DvrSummary(1, ServiceType.Radarr, "Radarr", "radarr.lan:7878", is4k = false, isDefault = true)),
        users = listOf(Choice(1, "Ann")),
        choices = TESTED,
    )

private val ON_RADARR = OverrideRuleForm(serviceType = ServiceType.Radarr, serviceId = 1)

/** The override rule form: each group says what it still needs, Save waits on all three, and the pickers are rows. */
@RunWith(RobolectricTestRunner::class)
class OverrideRuleFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0
    private val users = mutableListOf<Int>()

    private fun show(
        draft: OverrideRuleForm,
        extras: OverrideRuleExtras = RULE_EXTRAS,
    ) = rule.setContent {
        BingeExpressiveTheme(dynamicColor = false) {
            OverrideRuleScreen(
                state = ExtrasEditorUiState.Ready(draft = draft, saved = OverrideRuleForm(), extras = extras),
                events = emptyFlow(),
                actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = { saves++ }),
                ruleActions = ruleActions(onToggleUser = { users += it }),
            )
        }
    }

    @Test
    fun `a rule without a condition or an override says what each group needs and can't be saved`() {
        show(ON_RADARR)

        rule.onNodeWithText("Add at least one condition: a user, genre, language or keyword.").assertExists()
        rule.onNodeWithText("Pick at least one override: a quality profile, root folder or tag.").performScrollTo().assertExists()
        rule.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `without an instance the overrides wait on one`() {
        show(OverrideRuleForm(genres = "16"), RULE_EXTRAS.copy(choices = null))

        assertEquals(3, rule.onAllNodesWithText("Pick an instance to see what it offers.").fetchSemanticsNodes().size)
        rule.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `a user is picked from the checklist`() {
        show(ON_RADARR)

        rule.onNode(hasText("Requested by") and hasClickAction()).performClick()
        rule.onNodeWithText("Ann").performClick()

        assertEquals(listOf(1), users)
    }

    @Test
    fun `a rule with an instance, a condition and an override saves`() {
        show(ON_RADARR.copy(genres = "16", profileId = 4))

        rule.onNodeWithText("Create").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, saves)
    }
}

private fun ruleActions(onToggleUser: (Int) -> Unit) =
    OverrideRuleActions(
        onSelectInstance = {},
        onToggleUser = onToggleUser,
        onToggleTag = {},
        onToggleGenre = {},
        onLoadLanguages = {},
        onSelectLanguages = {},
        onToggleKeyword = {},
        onSearchKeywords = {},
        onLoadKeywordNames = {},
        onDelete = {},
    )
