package com.cooper.binge.companion.seerr.ui.state

import com.binge.designsystem.SaveStatus
import com.binge.designsystem.template.FormSaveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormSaveStatusMappingTest {
    @Test
    fun `a failure wins over whatever the status was`() {
        var retries = 0
        val mapped = formSaveStatus(SaveStatus.Saved, failed = true, onRetry = { retries++ })

        assertTrue(mapped is FormSaveStatus.Failed)
        (mapped as FormSaveStatus.Failed).onRetry()
        assertEquals(1, retries)
    }

    @Test
    fun `the engine's statuses map one to one`() {
        assertEquals(FormSaveStatus.Idle, formSaveStatus(SaveStatus.Idle, failed = false, onRetry = {}))
        assertEquals(FormSaveStatus.Saving, formSaveStatus(SaveStatus.Saving, failed = false, onRetry = {}))
        assertEquals(FormSaveStatus.Saved, formSaveStatus(SaveStatus.Saved, failed = false, onRetry = {}))
    }
}
