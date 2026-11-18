package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvQrCode
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.tv.R as TvR

/**
 * The server's release notes on a television, which has no browser to open them in: the address, and a code the phone
 * scans, as the bug report's page shows its own. Back is the way out, so there is no Close; the code holds focus meanwhile,
 * through [codeModifier]. The address is on the page so a viewer who cannot scan it can still read where it goes.
 */
@Composable
internal fun ColumnScope.TvReleaseNotesSheetContent(
    url: String,
    codeModifier: Modifier,
) {
    TvSideSheetTitle(stringResource(R.string.tv_settings_release_notes))
    TvSideSheetBody(stringResource(R.string.tv_settings_release_notes_scan, url))
    val codeSize = dimensionResource(R.dimen.tv_bug_report_code_size)
    Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
        contentAlignment = Alignment.Center,
    ) {
        TvQrCode(
            content = url,
            contentDescription = stringResource(R.string.tv_settings_release_notes_code_description),
            modifier = codeModifier.focusable().sizeIn(maxWidth = codeSize, maxHeight = codeSize).aspectRatio(1f),
        )
    }
}
