package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.core.ui.R as UiR

/** A settings page that is loading shows placeholder groups, and says once to a screen reader that it is loading. */
@RunWith(RobolectricTestRunner::class)
class SettingsRowsSkeletonTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the skeleton is announced as loading settings, once`() {
        rule.setContent { BingeExpressiveTheme { SettingsRowsSkeleton() } }
        rule.onNodeWithContentDescription(string(UiR.string.settings_loading)).assertIsDisplayed()
    }
}
