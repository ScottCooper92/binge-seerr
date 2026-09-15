package io.github.scottcooper92.binge.seerr.ui.tv

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The one runtime check that selects the TV shell, off the mode bits alone: the night bit must not confuse it. */
@RunWith(RobolectricTestRunner::class)
class TvDeviceTest {
    @Test
    fun aTelevisionModeIsATelevisionWhateverTheNightBit() {
        val configuration = Configuration().apply { uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES }

        assertTrue(configuration.isTelevision())
    }

    @Test
    fun aHandsetIsNot() {
        val configuration = Configuration().apply { uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES }

        assertFalse(configuration.isTelevision())
    }
}
