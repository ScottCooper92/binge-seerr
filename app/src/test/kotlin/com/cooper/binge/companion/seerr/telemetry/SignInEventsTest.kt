package com.cooper.binge.companion.seerr.telemetry

import com.cooper.homelab.core.telemetry.SharedAnalyticsEvents
import com.cooper.homelab.core.testing.RecordingAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test

/** A sign-in is reported in the shared library's events: a success under `sign_in`, a failure under `sign_in_failed`. */
class SignInEventsTest {
    private val analytics = RecordingAnalytics()

    @Test
    fun `a success is sign_in with its method and nothing else`() {
        analytics.reportSignIn("ApiKey", success = true)

        assertEquals(listOf(SharedAnalyticsEvents.SIGN_IN to mapOf(SharedAnalyticsEvents.PARAM_METHOD to "ApiKey")), analytics.events)
    }

    @Test
    fun `a failure is sign_in_failed with its method and one fixed reason`() {
        analytics.reportSignIn("Local", success = false)

        val params = mapOf(SharedAnalyticsEvents.PARAM_METHOD to "Local", SharedAnalyticsEvents.PARAM_REASON to "failed")
        assertEquals(listOf(SharedAnalyticsEvents.SIGN_IN_FAILED to params), analytics.events)
    }

    @Test
    fun `the demo is the shared demo method`() {
        analytics.reportSignIn("demo", success = true)

        assertEquals(mapOf(SharedAnalyticsEvents.PARAM_METHOD to SharedAnalyticsEvents.METHOD_DEMO), analytics.events.single().second)
    }

    @Test
    fun `a hand-off session sign-in reports under its own mode name`() {
        analytics.reportSignIn("Session", success = true)

        assertEquals("Session", analytics.events.single().second[SharedAnalyticsEvents.PARAM_METHOD])
    }
}
