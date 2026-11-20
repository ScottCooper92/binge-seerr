package com.cooper.binge.companion.seerr.feedback

import android.os.Build
import com.cooper.homelab.core.feedback.BugReport
import com.cooper.homelab.core.feedback.bugReportUrl
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BugReportTest {
    /** The three facts as this app wrote them by hand before it used the shared install fields, in the order it wrote them. */
    private fun writtenByHand(
        versionName: String,
        versionCode: Int,
    ) = mapOf(
        "app-version" to "$versionName ($versionCode)",
        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "android-version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )

    @Test
    fun `the report carries the same three facts, under the same field ids and in the same order`() {
        val report = seerrBugReport(versionName = "0.1.0", versionCode = 1)

        assertEquals("seerr-bug.yml", report.template)
        assertEquals(writtenByHand("0.1.0", 1).toList(), report.fields.toList())
    }

    @Test
    fun `the link opens the Seerr bug form on the app-feedback repository, each field prefilled by its id and encoded`() {
        val link = bugReportUrl(BUG_REPORT_REPOSITORY, seerrBugReport(versionName = "0.1.0", versionCode = 1))

        assertEquals(bugReportUrl(BUG_REPORT_REPOSITORY, BugReport("seerr-bug.yml", writtenByHand("0.1.0", 1))), link)
        assertEquals(
            true,
            link.startsWith(
                "https://github.com/ScottCooper92/app-feedback/issues/new?template=seerr-bug.yml&app-version=0.1.0+%281%29&device=",
            ),
        )
    }
}
