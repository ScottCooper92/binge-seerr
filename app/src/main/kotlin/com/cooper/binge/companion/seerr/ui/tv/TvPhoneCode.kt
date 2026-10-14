package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.tv.component.TvQrCode
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.cooper.binge.companion.seerr.R

/**
 * A page on the server as a code the phone scans, for what a television has no keyboard to do: [body] says what to do
 * there, over the code, centred in the space the sheet has left. [modifier] goes on the code, which is what takes focus.
 */
@Composable
internal fun ColumnScope.TvPhoneCode(
    url: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    TvSideSheetBody(body)
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        TvQrCode(
            content = url,
            contentDescription = body,
            modifier = modifier.focusable().size(dimensionResource(R.dimen.tv_bug_report_code_size)),
        )
    }
}
