package com.cooper.binge.companion.seerr.ui.users.settings

import com.cooper.binge.companion.seerr.R
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorIssuesTest {
    private val wrong = invalid("a", "port", R.string.editor_error_port)
    private val absent = missing("a", "name")

    @Test
    fun `an invalid value shows at once and a missing one waits for a failed save`() {
        val issues = listOf(absent, wrong)

        assertEquals(listOf(wrong), issues.visible(submitted = false))
        assertEquals(issues, issues.visible(submitted = true))
    }
}
