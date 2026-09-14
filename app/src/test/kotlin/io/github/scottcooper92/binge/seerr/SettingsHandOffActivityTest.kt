package io.github.scottcooper92.binge.seerr

import android.content.Intent
import com.binge.integration.sdk.CompanionManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The host's "manage" affordance. The action string is the whole contract here — a typo reads to
 * Binge as "declares nothing" and the row simply never appears — so it is asserted against the
 * SDK's own constant rather than written out a second time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsHandOffActivityTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the manifest exports the hand-off on the action the contract names`() {
        val intent = Intent(CompanionManifest.ACTION_SETTINGS).setPackage(context.packageName)

        val resolved =
            context.packageManager
                .queryIntentActivities(intent, 0)
                .single()
                .activityInfo

        assertEquals(SettingsHandOffActivity::class.java.name, resolved.name)
        assertTrue(resolved.exported)
    }

    @Test
    fun `a host that started it for a result is handed on to the hub`() {
        val controller = Robolectric.buildActivity(SettingsHandOffActivity::class.java)
        shadowOf(controller.get()).setCallingPackage("com.binge")

        controller.create()

        val next = shadowOf(controller.get()).nextStartedActivity
        assertEquals(MainActivity::class.java.name, next.component?.className)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `a start with no calling package opens nothing`() {
        val controller = Robolectric.buildActivity(SettingsHandOffActivity::class.java)

        controller.create()

        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertTrue(controller.get().isFinishing)
    }
}
