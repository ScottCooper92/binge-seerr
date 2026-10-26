package com.cooper.binge.companion.seerr

import android.content.Intent
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.CompanionManifest
import com.cooper.binge.companion.seerr.flags.FixedFeatureFlags
import com.cooper.binge.companion.seerr.flags.Flags
import com.cooper.binge.companion.seerr.ui.DeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The host's "open this request" hand-off: the action is the SDK's, and the extra lands on the request's page. */
@RunWith(RobolectricTestRunner::class)
class ViewRequestHandOffActivityTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun handOff(requestId: String?) =
        Intent(CompanionManifest.ACTION_VIEW_REQUEST).apply {
            requestId?.let { putExtra(CompanionManifest.EXTRA_REQUEST_ID, it) }
        }

    private fun start(
        requestId: String?,
        callingPackage: String? = BingeHosts.DEBUG_PACKAGE_NAME,
        flags: Flags = Flags.AllOn,
    ): Intent? {
        val controller = Robolectric.buildActivity(ViewRequestHandOffActivity::class.java, handOff(requestId))
        controller.get().featureFlags = { FixedFeatureFlags(flags) }
        callingPackage?.let { shadowOf(controller.get()).setCallingPackage(it) }
        controller.create()
        assertTrue(controller.get().isFinishing)
        return shadowOf(controller.get()).nextStartedActivity
    }

    @Test
    fun `the manifest exports the hand-off on the action the contract names`() {
        val intent = Intent(CompanionManifest.ACTION_VIEW_REQUEST).setPackage(context.packageName)

        val resolved =
            context.packageManager
                .queryIntentActivities(intent, 0)
                .single()
                .activityInfo

        assertEquals(ViewRequestHandOffActivity::class.java.name, resolved.name)
        assertTrue(resolved.exported)
    }

    @Test
    fun `a host's request id opens that request's page`() {
        val next = start(requestId = "42")

        assertEquals(MainActivity::class.java.name, next?.component?.className)
        assertEquals(DeepLinks.request(42), next?.dataString)
    }

    @Test
    fun `an id that is not one of ours opens the requests list`() {
        assertEquals(DeepLinks.requests(), start(requestId = "not-a-number")?.dataString)
        assertEquals(DeepLinks.requests(), start(requestId = "0")?.dataString)
        assertEquals(DeepLinks.requests(), start(requestId = null)?.dataString)
    }

    @Test
    fun `a caller that is not Binge opens nothing`() {
        assertNull(start(requestId = "42", callingPackage = "com.example.other"))
    }

    @Test
    fun `a start with no calling package opens nothing`() {
        assertNull(start(requestId = "42", callingPackage = null))
    }

    @Test
    fun `with request companions off even Binge opens nothing`() {
        assertNull(start("7", flags = Flags(bingePromotion = true)))
    }
}
