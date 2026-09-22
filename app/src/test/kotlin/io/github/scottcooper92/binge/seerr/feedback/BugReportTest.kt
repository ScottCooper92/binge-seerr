package io.github.scottcooper92.binge.seerr.feedback

import org.junit.Assert.assertEquals
import org.junit.Test

class BugReportTest {
    private val report = DeviceReport(appVersion = "0.1.0 (1)", device = "Google Pixel 8", androidVersion = "15 (API 35)")

    @Test
    fun `the link opens the bug form on this repository, each field prefilled by its id and encoded`() {
        assertEquals(
            "https://github.com/ScottCooper92/binge-seerr/issues/new?template=bug.yml" +
                "&app-version=0.1.0+%281%29&device=Google+Pixel+8&android-version=15+%28API+35%29",
            bugReportUrl(report),
        )
    }

    @Test
    fun `a fork's repository is used as given, trailing slash or not`() {
        assertEquals(
            bugReportUrl(report, repository = "https://github.com/someone/fork"),
            bugReportUrl(report, repository = "https://github.com/someone/fork/"),
        )
    }
}
