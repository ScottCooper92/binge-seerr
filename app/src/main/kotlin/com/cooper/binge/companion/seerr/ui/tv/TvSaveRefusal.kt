package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.state.messageRes

/**
 * The status a television save-as-made row shows: [refusal] takes over from [status] while the server's refusal of the last
 * change is on the page, with the words the phone's snackbar uses for the same error. The row's switch has already been put back.
 */
@Composable
internal fun tvSaveStatus(
    status: SaveStatus,
    refusal: SeerrError?,
): SaveStatus = if (refusal != null) SaveStatus.Refused(stringResource(refusal.messageRes())) else status
