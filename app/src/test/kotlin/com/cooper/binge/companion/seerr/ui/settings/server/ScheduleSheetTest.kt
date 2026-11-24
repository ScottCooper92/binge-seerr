package com.cooper.binge.companion.seerr.ui.settings.server

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/** A job's schedule is edited in a sheet: a preset applies when picked, and a typed cron applies from Done. */
@RunWith(RobolectricTestRunner::class)
class ScheduleSheetTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val applied = mutableListOf<String>()
    private val job =
        ServerJob(id = "availability-sync", name = "Availability sync", interval = JobInterval.Short, running = false, nextRunMillis = null)

    private fun show() {
        rule.setContent { BingeExpressiveTheme { ScheduleSheet(job = job, onConfirm = { applied += it }, onDismiss = {}) } }
    }

    @Test
    fun `a preset applies the moment it is picked`() {
        show()
        val preset = job.presets().first()
        rule.onNodeWithText(presetLabel(preset)).performClick()
        assertEquals(listOf(preset.cron), applied)
    }

    @Test
    fun `Done is off until a cron is typed and then applies it`() {
        show()
        rule.onNodeWithText(string(AdminR.string.editor_done)).assertIsNotEnabled()
        rule.onNodeWithText(string(AdminR.string.server_settings_job_cron)).performTextInput(" 0 */15 * * * * ")
        rule.onNodeWithText(string(AdminR.string.editor_done)).performClick()
        assertEquals(listOf("0 */15 * * * *"), applied)
    }

    private fun presetLabel(preset: SchedulePreset): String =
        ApplicationProvider.getApplicationContext<Context>().resources.getQuantityString(
            AdminR.plurals.server_settings_every_minutes,
            preset.every,
            preset.every,
        )
}
