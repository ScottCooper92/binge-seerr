package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The primary is never dead: in every state it is enabled, and a tap reaches the page. */
@RunWith(RobolectricTestRunner::class)
class TitleActionsEnabledTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun check(
        standard: VersionState,
        fourK: VersionState? = null,
        label: Int,
    ) {
        var taps = 0
        val model = titleActionsModel(standard, fourK, emptyList(), mayReport = false)
        rule.setContent { SeerrTheme { PrimaryAction(model.primary, { taps++ }, Modifier) } }

        rule.onNodeWithText(resources.getString(label)).assertIsEnabled().performClick()

        assertEquals(1, taps)
    }

    private fun held(availability: TitleAvailability) = VersionState(availability, mayRequest = false)

    @Test
    fun `a title the viewer may request is enabled`() =
        check(VersionState(TitleAvailability.None, mayRequest = true), label = R.string.title_page_request)

    @Test
    fun `a title the viewer may not request is enabled`() = check(held(TitleAvailability.None), label = R.string.title_page_request)

    @Test
    fun `a requested title is enabled`() = check(held(TitleAvailability.Pending), label = R.string.hubs_card_requested)

    @Test
    fun `a processing title is enabled`() = check(held(TitleAvailability.Processing), label = R.string.hubs_card_processing)

    @Test
    fun `an available title with no request to manage is enabled`() =
        check(held(TitleAvailability.Available), label = R.string.hubs_card_available)

    @Test
    fun `a partly available title with no request to manage is enabled`() =
        check(held(TitleAvailability.PartiallyAvailable), label = R.string.hubs_card_partial)

    @Test
    fun `a blocklisted title is enabled`() = check(held(TitleAvailability.Blocklisted), label = R.string.hubs_card_blocklisted)
}
