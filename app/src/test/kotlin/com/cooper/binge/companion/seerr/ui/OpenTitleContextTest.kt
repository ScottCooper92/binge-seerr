package com.cooper.binge.companion.seerr.ui

import android.app.Activity
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class OpenTitleContextTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `a title whose address cannot be a real site opens nothing`() {
        activity.openTitle(promoted = true, RequestMediaType.Movie, 603, "https://demo.seerr.invalid/movie/603")
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `a title on a real server still opens its page`() {
        activity.openTitle(promoted = false, RequestMediaType.Movie, 603, "https://seerr.example.com/movie/603")
        assertNotNull(shadowOf(activity).nextStartedActivity)
    }
}
