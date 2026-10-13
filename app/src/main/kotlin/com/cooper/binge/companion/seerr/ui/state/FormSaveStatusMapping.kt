package com.cooper.binge.companion.seerr.ui.state

import com.binge.designsystem.SaveStatus
import com.binge.designsystem.template.FormSaveStatus

/**
 * The engine reports a failure through its `failed` flag, never through [SaveStatus], so [failed] is checked first and
 * the status is only Idle, Saving or Saved here.
 */
internal fun formSaveStatus(
    status: SaveStatus,
    failed: Boolean,
    onRetry: () -> Unit,
): FormSaveStatus =
    when {
        failed -> FormSaveStatus.Failed(onRetry)
        status == SaveStatus.Saving -> FormSaveStatus.Saving
        status == SaveStatus.Saved -> FormSaveStatus.Saved
        else -> FormSaveStatus.Idle
    }
