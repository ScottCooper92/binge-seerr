package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.component.ItemRows
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutRowsTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `the About group lists the four rows and the licences row opens the licences`() {
        var licences = 0
        composeTestRule.setContent { BingeExpressiveTheme(dynamicColor = false) { ItemRows(rows = aboutRows { licences++ }) } }
        listOf(R.string.about_app_version, R.string.about_privacy_policy, R.string.licences_title, R.string.about_join_beta)
            .forEach { composeTestRule.onNodeWithText(string(it)).assertIsDisplayed() }
        composeTestRule.onNodeWithText(string(R.string.licences_title)).performClick()
        assertEquals(1, licences)
    }
}
