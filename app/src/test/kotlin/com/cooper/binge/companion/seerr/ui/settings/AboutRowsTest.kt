package com.cooper.binge.companion.seerr.ui.settings

import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
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
    fun `the About group has the shared five rows, the beta opt-in once, and its in-app rows open the licences and diagnostics pages`() {
        var licences = 0
        var diagnostics = 0
        var rows: List<ListItem> = emptyList()
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                rows = aboutGroupRows(onOpenLicences = { licences++ }, onOpenDiagnostics = { diagnostics++ })
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(5, rows.size)
        assertEquals(rows.size, rows.map { it.label }.distinct().size)
        rows.filter { it.destination == ListItemDestination.InApp }.forEach { it.onClick() }
        assertEquals(1, licences)
        assertEquals(1, diagnostics)
    }
}
