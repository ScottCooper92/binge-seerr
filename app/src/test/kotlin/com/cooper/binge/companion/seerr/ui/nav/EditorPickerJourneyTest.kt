package com.cooper.binge.companion.seerr.ui.nav

import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.DiscoverSliderRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.OverrideRuleRoute
import com.cooper.binge.companion.seerr.ui.RuleKeywordPickerRoute
import com.cooper.binge.companion.seerr.ui.SliderKeywordPickerRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NARROW = "w400dp-h800dp"

/**
 * A picker is a page over its editor, not a window: it finds the editor's own view model, so it opens and stays open, and
 * Back from it is the editor beneath.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = NARROW)
class EditorPickerJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @Test
    fun `the slider's keyword page opens over the editor and Back is the editor`() {
        host.connect()
        host.launch(HomeRoute, DiscoverSliderRoute(null))
        host.awaitShowing(host.string(R.string.server_settings_slider_add))
        host.push(SliderKeywordPickerRoute(null))
        host.awaitShowing(host.string(R.string.server_settings_blocklist_tags_search))
        assertEquals(SliderKeywordPickerRoute(null), host.stack().last())

        host.back()
        host.await("the slider editor on top") { host.stack().last() == DiscoverSliderRoute(null) }
    }

    @Test
    fun `the rule's keyword page opens over the editor and Back is the editor`() {
        host.connect()
        host.launch(HomeRoute, OverrideRuleRoute(null))
        host.push(RuleKeywordPickerRoute(null))
        host.awaitShowing(host.string(R.string.server_settings_blocklist_tags_search))
        assertEquals(RuleKeywordPickerRoute(null), host.stack().last())

        host.back()
        host.await("the rule editor on top") { host.stack().last() == OverrideRuleRoute(null) }
    }
}
