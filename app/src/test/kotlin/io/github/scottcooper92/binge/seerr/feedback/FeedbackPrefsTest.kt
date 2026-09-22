package io.github.scottcooper92.binge.seerr.feedback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeedbackPrefsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `shake to report is on until turned off`() =
        runTest {
            val prefs = FeedbackPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("f.preferences_pb") })
            assertTrue(prefs.shakeToReport.first())

            prefs.setShakeToReport(false)
            assertFalse(prefs.shakeToReport.first())
        }
}
