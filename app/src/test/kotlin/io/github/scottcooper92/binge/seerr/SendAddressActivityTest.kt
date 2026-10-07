package io.github.scottcooper92.binge.seerr

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The sheet's ViewModel reads its target once, from the intent that created it. A launch mode that
 * reuses the instance would deliver a second TV's link to `onNewIntent`, which nothing reads, and
 * leave the sheet sending to the first TV's port and token.
 */
@RunWith(RobolectricTestRunner::class)
class SendAddressActivityTest {
    @Test
    fun `every link gets a new instance`() {
        val context = RuntimeEnvironment.getApplication()

        val info = context.packageManager.getActivityInfo(ComponentName(context, SendAddressActivity::class.java), 0)

        assertEquals(ActivityInfo.LAUNCH_MULTIPLE, info.launchMode)
    }

    @Test
    fun `only the link sheet is exported, so only the scan's copy may offer the session`() {
        val context = RuntimeEnvironment.getApplication()

        val link = context.packageManager.getActivityInfo(ComponentName(context, SendAddressActivity::class.java), 0)
        val scanned = context.packageManager.getActivityInfo(ComponentName(context, ScannedSendAddressActivity::class.java), 0)

        assertTrue(link.exported)
        assertFalse(scanned.exported)
        assertEquals(ActivityInfo.LAUNCH_MULTIPLE, scanned.launchMode)
    }
}
