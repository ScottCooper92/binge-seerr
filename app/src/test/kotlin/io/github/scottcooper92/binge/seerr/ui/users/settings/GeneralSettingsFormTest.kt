package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val MANAGED =
    GeneralSettings(
        accountType = UserOrigin.Local,
        role = UserRole.Owner,
        displayName = "Ann",
        email = "ann@home.lan",
        loadedEmail = "ann@home.lan",
        emailRequired = true,
        region = "",
        defaultMovieQuota = QuotaDefault(limit = 10, days = 7),
        canEditQuotas = true,
        canEditEmail = true,
    )

private val EXTRAS = UserGeneralExtras(serverDefaults = ServerDiscoverDefaults(region = "FR"))

/** The page over a draft it edits itself, as the ViewModel would, counting saves. */
@Composable
private fun General(
    initial: GeneralSettings,
    onSave: () -> Unit = {},
) = BingeExpressiveTheme(dynamicColor = false) {
    var draft by remember { mutableStateOf(initial) }
    GeneralSettingsScreen(
        state = ExtrasEditorUiState.Ready(draft = draft, saved = MANAGED, extras = EXTRAS),
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = { draft = it(draft) }, onSave = onSave),
    )
}

/** The general page's rows: what it shows and does not edit, the server's defaults, and the quota overrides. */
@RunWith(RobolectricTestRunner::class)
class GeneralSettingsFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(draft: GeneralSettings) = rule.setContent { General(draft) { saves++ } }

    @Test
    fun `the account type and role read out`() {
        show(MANAGED)

        rule.onNodeWithText("Local account").assertExists()
        rule.onNodeWithText("Owner").assertExists()
    }

    @Test
    fun `a blank region reads as the server's`() {
        show(MANAGED)

        rule.onNodeWithText("Default (France)").assertExists()
    }

    @Test
    fun `a quota left to the server says what that is, and its override brings the limit out`() {
        show(MANAGED)

        rule.onNodeWithText("Server default: 10 per 7 days").performScrollTo().assertExists()
        rule.onNodeWithText("Movie requests").assertDoesNotExist()

        rule.onNodeWithText("Override the movie limit").performScrollTo().performClick()

        rule.onNodeWithText("Movie requests").performScrollTo().assertExists()
    }

    /** The page saves as it changes (#930): there is no Save, and the view model never writes a draft that fails its check. */
    @Test
    fun `there is no Save to press`() {
        show(MANAGED.copy(displayName = "Annie"))

        rule.onNodeWithText("Save").assertDoesNotExist()
    }
}
