package com.cooper.binge.companion.seerr.feedback

import com.cooper.homelab.core.feedback.bugReportUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class BugReportTest {
    @Test
    fun `the link opens the Seerr bug form on the app-feedback repository, each field prefilled by its id and encoded`() {
        val report = seerrBugReport(appVersion = "0.1.0 (1)", device = "Google Pixel 8", androidVersion = "15 (API 35)")
        assertEquals(
            "https://github.com/ScottCooper92/app-feedback/issues/new?template=seerr-bug.yml" +
                "&app-version=0.1.0+%281%29&device=Google+Pixel+8&android-version=15+%28API+35%29",
            bugReportUrl(BUG_REPORT_REPOSITORY, report),
        )
    }
}
