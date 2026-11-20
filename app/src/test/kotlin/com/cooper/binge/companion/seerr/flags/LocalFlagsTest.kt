package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.CompositionLocalProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.homelab.core.flags.DataStoreFlagOverrides
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.bingePromotion
import com.cooper.homelab.core.flags.reviewPrompt
import com.cooper.homelab.core.testing.InMemoryDataStore
import com.cooper.homelab.core.uiandroid.flags.LocalFlags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The flags a screen reads through the shared library's local and accessors. A screen shown alone advertises nothing, and a
 * developer override an earlier build wrote to disk still reaches the screens once a root provides it.
 */
@RunWith(RobolectricTestRunner::class)
class LocalFlagsTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `nothing is advertised until a root provides the flags`() {
        var promoted = true
        var review = true
        var shared = true
        composeTestRule.setContent {
            promoted = LocalFlags.current.bingePromotion
            review = LocalFlags.current.reviewPrompt
            shared = LocalFlags.current[SharedSetupEnabled]
        }
        composeTestRule.waitForIdle()

        assertFalse(promoted)
        assertFalse(review)
        assertFalse(shared)
    }

    @Test
    fun `overrides already on disk reach the screens through the local`() =
        runTest {
            val stored =
                InMemoryDataStore(
                    mutablePreferencesOf(
                        booleanPreferencesKey("override_binge_promotion_enabled") to true,
                        booleanPreferencesKey("override_shared_setup_enabled") to true,
                    ),
                )
            val onDisk = DataStoreFlagOverrides(stored).values.first()
            val provided =
                Flags.of(
                    *onDisk.entries
                        .mapNotNull { (key, value) ->
                            AllFlagKeys.find { it.key == key }?.to(value)
                        }.toTypedArray(),
                )
            var promoted = false
            var review = true
            var shared = false
            composeTestRule.setContent {
                CompositionLocalProvider(LocalFlags provides provided) {
                    promoted = LocalFlags.current.bingePromotion
                    review = LocalFlags.current.reviewPrompt
                    shared = LocalFlags.current[SharedSetupEnabled]
                }
            }
            composeTestRule.waitForIdle()

            assertTrue(promoted)
            assertTrue(shared)
            assertFalse(review)
        }
}
