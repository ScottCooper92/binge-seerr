package io.github.scottcooper92.binge.seerr.util

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Deliberately carries no `@Config`: it is the assertion that `robolectric.properties` supplies one.
 *
 * Without that file a test falls back to `targetSdk` 36, whose sandbox requires Java 21 — so it
 * passes on a JDK 21 machine and dies in CI on JDK 17, before its body runs. That is the inverse of
 * the failure a contributor expects, and it defeats running the gate locally before pushing.
 */
@RunWith(RobolectricTestRunner::class)
class RobolectricDefaultsTest {
    @Test
    fun `a test without @Config runs on the SDK this module's JDK can sandbox`() {
        assertEquals(34, Build.VERSION.SDK_INT)
    }

    @Test
    fun `a test without @Config gets a plain Application, not the app's Hilt one`() {
        assertEquals(Application::class.java, ApplicationProvider.getApplicationContext<Application>().javaClass)
    }
}
