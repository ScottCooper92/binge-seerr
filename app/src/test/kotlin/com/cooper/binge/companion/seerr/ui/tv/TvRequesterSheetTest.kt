package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.tv.requests.TvQuotaOverride
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequesterActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequesterState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequesterStep
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequesterToggle
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The requester sheet on a remote (#1516): the toggles, then the override's limit and window, each reported as made. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequesterSheetTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private var autoApproveToggles = 0
    private val overrides = mutableListOf<TvQuotaOverride>()

    @Test
    fun theSheetOpensOnAutoApproveAndOkTogglesIt() {
        setContent(state())

        row(R.string.permission_auto_approve).assertIsFocused()
        pressKey(Key.DirectionCenter)

        assertEquals(1, autoApproveToggles)
    }

    @Test
    fun turningTheOverrideOnRevealsTheLimitAndTheWindow() {
        setContent(state(override = TvQuotaOverride(on = false, limit = 0, days = 7, default = null)))
        composeTestRule.onNodeWithText(string(R.string.tv_requester_limit_title)).assertDoesNotExist()

        row(R.string.user_settings_quota_override_movie).requestFocus()
        composeTestRule.waitForIdle()
        pressKey(Key.DirectionCenter)

        assertEquals(listOf(TvQuotaOverride(on = true, limit = 0, days = 7, default = null)), overrides)
        composeTestRule.onNodeWithText(string(R.string.tv_requester_limit_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.tv_requester_window_title)).assertIsDisplayed()
    }

    @Test
    fun rightRaisesTheLimitFromUnlimited() {
        setContent(state(override = TvQuotaOverride(on = true, limit = 0, days = 7, default = null)))

        row(R.string.user_settings_quota_override_movie).requestFocus()
        composeTestRule.waitForIdle()
        pressKey(Key.DirectionDown)
        pressKey(Key.DirectionRight)

        assertEquals(TvQuotaOverride(on = true, limit = 1, days = 7, default = null), overrides.last())
    }

    @Test
    fun aWindowOffTheListIsOfferedAndAnotherCanBePicked() {
        setContent(state(override = TvQuotaOverride(on = true, limit = 5, days = 10, default = null)))

        composeTestRule.onNodeWithText(days(10)).assertIsDisplayed()
        composeTestRule.onNode(hasText(days(30)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        pressKey(Key.DirectionCenter)

        assertEquals(TvQuotaOverride(on = true, limit = 5, days = 30, default = null), overrides.last())
    }

    @Test
    fun aLimitPastTheWebClientsTopIsSteppedFromRatherThanClamped() {
        setContent(state(override = TvQuotaOverride(on = true, limit = 150, days = 7, default = null)))

        row(R.string.user_settings_quota_override_movie).requestFocus()
        composeTestRule.waitForIdle()
        pressKey(Key.DirectionDown)
        pressKey(Key.DirectionLeft)

        assertEquals(149, overrides.last().limit)
    }

    @Test
    fun aRefusedSaveIsShownAsANote() {
        setContent(state(), note = "Refused")

        composeTestRule.onNodeWithText("Refused").assertIsDisplayed()
    }

    @Test
    fun theQuotaLineIsLeftOutUntilTheServerAnswers() {
        setContent(state(quotaKnown = false))

        composeTestRule
            .onNodeWithText(string(R.string.hub_quota_movies), substring = true)
            .assertDoesNotExist()
    }

    private fun state(
        quotaKnown: Boolean = true,
        override: TvQuotaOverride? = null,
    ) = TvRequesterState(
        name = "ana",
        quota = HubQuotaBucket(5, 3, 7),
        quotaKnown = quotaKnown,
        autoApprove = TvRequesterToggle(checked = false, enabled = true),
        request4k = TvRequesterToggle(checked = false, enabled = true),
        override = override,
        saveStatus = SaveStatus.Idle,
    )

    private fun setContent(
        initial: TvRequesterState,
        note: String? = null,
    ) {
        composeTestRule.setContent {
            var state by remember { mutableStateOf(initial) }
            BingeTvTheme {
                Column {
                    TvRequesterStep(
                        state = state,
                        isMovie = true,
                        actions =
                            TvRequesterActions(
                                onToggleAutoApprove = { autoApproveToggles++ },
                                onToggleRequest4k = {},
                                onOverrideChange = {
                                    overrides += it
                                    state = state.copy(override = it)
                                },
                                onRetrySave = {},
                            ),
                        entryFocus = remember { FocusRequester() },
                        note = note,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun row(label: Int) = composeTestRule.onNode(hasText(string(label), substring = true) and isFocusable())

    private fun days(count: Int) = RuntimeEnvironment.getApplication().resources.getQuantityString(R.plurals.hub_quota_period, count, count)

    private fun pressKey(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
