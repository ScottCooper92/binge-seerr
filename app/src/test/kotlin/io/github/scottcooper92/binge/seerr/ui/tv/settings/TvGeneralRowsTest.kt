package io.github.scottcooper92.binge.seerr.ui.tv.settings

import com.binge.designsystem.component.ListItem
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.settings.GeneralSettings
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The TV settings read-out shows an application URL only when it is one a browser could open (#1054). */
@RunWith(RobolectricTestRunner::class)
class TvGeneralRowsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    /** The rows for each URL in [urls], read from one composition: a test sets its content once. */
    private fun rowsFor(vararg urls: String?): List<List<ListItem>> {
        var rows: List<List<ListItem>> = emptyList()
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                rows =
                    urls.map { url ->
                        generalRows(
                            GeneralSettings(applicationTitle = null, applicationUrl = url, displayLanguage = "en", hideAvailable = null),
                        )
                    }
            }
        }
        rule.waitForIdle()
        return rows
    }

    @Test
    fun `a web application URL is listed, a typed non-URL is dropped, and none leaves the other rows alone`() {
        val (web, typed, none) = rowsFor("https://seerr.example.com", "seerr.example.com", null)

        assertTrue(web.any { it.detail == "https://seerr.example.com" })
        assertFalse(typed.any { it.detail == "seerr.example.com" })
        assertEquals(web.size - 1, typed.size)
        assertEquals(typed.size, none.size)
    }
}
