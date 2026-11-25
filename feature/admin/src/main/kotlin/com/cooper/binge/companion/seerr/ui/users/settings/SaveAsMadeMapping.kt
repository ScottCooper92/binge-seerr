package com.cooper.binge.companion.seerr.ui.users.settings

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.homelab.core.foundation.ServerFailure
import com.binge.designsystem.SaveStatus as UiSaveStatus
import com.cooper.homelab.core.presentation.editor.WriteStatus as EngineWriteStatus

/**
 * What a failed call means to the shared library's editor engine. A write the local-network permission blocked is kept unsent
 * with a retry, as it always was here, so it is reported as unreachable: the library counts it a refusal.
 */
suspend fun Throwable.toEngineFailure(): ServerFailure =
    if (toSeerrError() == SeerrError.LocalNetworkDenied) ServerFailure.Unreachable() else toServerFailure()

/** The engine's save status as the screens' own. */
fun EngineWriteStatus.toUi(): UiSaveStatus =
    when (this) {
        EngineWriteStatus.Idle -> UiSaveStatus.Idle
        EngineWriteStatus.Saving -> UiSaveStatus.Saving
        EngineWriteStatus.Saved -> UiSaveStatus.Saved
    }
