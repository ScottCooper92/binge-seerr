package com.cooper.binge.companion.seerr.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Setup's two controls that open the browser say so to a screen reader, and still do what they did (#2347). */
@RunWith(RobolectricTestRunner::class)
class OpensOutsideMarksTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `report a problem announces that it opens outside the app, and opens the form`() {
        var opened = 0
        rule.setContent { BingeExpressiveTheme(dynamicColor = false) { ReportProblemMenuItem(onClick = { opened++ }) } }

        rule.onNodeWithContentDescription("Report a problem, opens outside the app").assertHasClickAction().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `open plex dot tv announces that it opens outside the app, and opens the page`() {
        var opened = 0
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                SetupLinkContent(
                    link = LinkFlow.Plex("A1B2", "https://app.plex.tv/auth", launchPending = false),
                    onOpenPlex = { opened++ },
                    onCancel = {},
                )
            }
        }

        rule.onNodeWithContentDescription("Open plex.tv, opens outside the app").assertHasClickAction().performClick()

        assertEquals(1, opened)
    }
}
