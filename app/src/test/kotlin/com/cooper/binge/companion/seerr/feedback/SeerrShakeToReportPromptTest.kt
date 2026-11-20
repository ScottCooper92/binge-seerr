package com.cooper.binge.companion.seerr.feedback

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.ui.openInBrowser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor

private const val URL = "https://example.test/report"

/**
 * A confirmed shake report opens through this app's own opener, a Custom Tab, like the Settings row and setup's Report a problem,
 * and not through the shared prompt's plain view intent. The Custom Tab is recognised by the extras its intent carries.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SeerrShakeToReportPromptTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private fun newSensorEvent(): SensorEvent =
        SensorEvent::class.java
            .getDeclaredConstructor(Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .newInstance(3)

    /** Three hard jolts, 50 ms apart: one shake. */
    private fun shake() {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        repeat(6) { i ->
            val event = newSensorEvent()
            event.sensor = sensor
            event.timestamp = i * 50_000_000L
            event.values[0] = if (i % 2 == 0) 60f else -60f
            event.values[1] = 0f
            event.values[2] = 9.81f
            shadowOf(manager).sendSensorEventToListeners(event)
        }
    }

    @Test
    fun `the confirmed report opens the way the other report links do`() {
        shadowOf(manager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        rule.setContent { SeerrShakeToReportPrompt(enabled = true, bugReportUrl = { URL }) }
        rule.waitForIdle()
        // What this app's own opener starts for this link: the shape to match.
        rule.activity.openInBrowser(URL)
        val expected: Intent = shadowOf(application).nextStartedActivity
        assertNotNull(expected.extras)
        assertTrue("the app's opener is a Custom Tab, which carries extras", expected.extras!!.keySet().isNotEmpty())

        shake()
        rule.waitForIdle()
        rule.onNodeWithText("Report a bug?").assertIsDisplayed()
        assertNull(shadowOf(application).nextStartedActivity)
        rule.onNodeWithText("Report a bug").performClick()
        rule.waitForIdle()

        val opened: Intent = shadowOf(application).nextStartedActivity
        assertEquals(URL, opened.data?.toString())
        assertEquals(expected.action, opened.action)
        assertEquals(expected.extras!!.keySet(), opened.extras?.keySet())
    }
}
