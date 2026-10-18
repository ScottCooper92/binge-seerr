package com.cooper.binge.companion.seerr.ui.handoff

import android.content.ComponentName
import android.content.Intent
import com.cooper.binge.companion.seerr.ReportTitleActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The report a scanned title opens is for this app's own scan alone: no link or other app can start it. */
@RunWith(RobolectricTestRunner::class)
class ReportTitleActivityTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the report is not exported`() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, ReportTitleActivity::class.java), 0)

        assertFalse(info.exported)
    }

    @Test
    fun `nothing answers a view of a link by the report's name or by any filter`() {
        val byName = Intent(Intent.ACTION_VIEW).setClassName(context, ReportTitleActivity::class.java.name)
        val byFilter = Intent(Intent.ACTION_VIEW).setPackage(context.packageName).addCategory(Intent.CATEGORY_BROWSABLE)

        val resolved = context.packageManager.queryIntentActivities(byFilter, 0).map { it.activityInfo.name }

        assertFalse(ReportTitleActivity::class.java.name in resolved)
        assertEquals(ReportTitleActivity::class.java.name, byName.component?.className)
    }
}
