package com.cooper.binge.companion.seerr.ui.consent

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews

/** The question asked before setup and the hub: what is shared, what never is, and the two answers. */
class ConsentScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun consent() = ConsentScreen(onChoice = {})
}
