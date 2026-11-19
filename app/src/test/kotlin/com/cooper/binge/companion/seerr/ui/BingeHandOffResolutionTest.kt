package com.cooper.binge.companion.seerr.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The gating of `bingeTitleHandOff`: the promotion flag, and a resolving intent. Robolectric's package manager does no package
 * visibility filtering, so this does not cover the manifest's `<queries>`; only a device shows that.
 */
@RunWith(RobolectricTestRunner::class)
class BingeHandOffResolutionTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun bingeAnswers(tmdbId: Int) {
        val link = Intent(Intent.ACTION_VIEW, "binge://title/movie/$tmdbId".toUri())
        val binge =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "com.cooper.binge.app"
                        name = "com.cooper.binge.app.MainActivity"
                        applicationInfo = ApplicationInfo().apply { packageName = "com.cooper.binge.app" }
                    }
            }
        shadowOf(context.packageManager).addResolveInfoForIntent(link, binge)
    }

    @Test
    fun `with promotion on and Binge answering the link, the hand-off is offered`() {
        bingeAnswers(155)

        assertNotNull(context.bingeTitleHandOff(promoted = true, RequestMediaType.Movie, 155))
    }

    @Test
    fun `with promotion off the hand-off is not offered even where Binge answers`() {
        bingeAnswers(155)

        assertNull(context.bingeTitleHandOff(promoted = false, RequestMediaType.Movie, 155))
    }

    @Test
    fun `with promotion on and nothing answering the link there is no hand-off`() {
        assertNull(context.bingeTitleHandOff(promoted = true, RequestMediaType.Movie, 155))
    }
}
