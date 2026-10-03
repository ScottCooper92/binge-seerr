package io.github.scottcooper92.binge.seerr.ui.users.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The page stacks one section per agent, and only the last field on the whole page offers Done. */
@RunWith(RobolectricTestRunner::class)
class NotificationsImeTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private val lastLabel
        get() = ApplicationProvider.getApplicationContext<Context>().getString(R.string.user_settings_field_pushover_sound)

    @Test
    fun `only the page's last field offers Done`() {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                NotificationsSettingsScreen(
                    state = EditorUiState.Ready(draft = NotificationSettings(), saved = NotificationSettings()),
                    events = emptyFlow(),
                    actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = {}),
                )
            }
        }

        composeTestRule.onAllNodes(hasImeAction(ImeAction.Done)).assertCountEquals(1)
        composeTestRule
            .onAllNodes(hasImeAction(ImeAction.Done) and hasText(lastLabel))
            .assertCountEquals(1)
    }
}
