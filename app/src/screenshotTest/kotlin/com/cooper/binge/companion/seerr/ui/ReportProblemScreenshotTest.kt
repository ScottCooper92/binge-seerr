package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame

/** The setup screens' overflow item: a popup does not capture, so the frame renders the item as a row of its own. */
class ReportProblemScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun reportProblemItem() = SheetFrame { ReportProblemMenuItem(onClick = {}) }
}
