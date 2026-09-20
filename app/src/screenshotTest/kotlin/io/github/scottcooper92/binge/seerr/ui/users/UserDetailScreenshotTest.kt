package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview

/** The Loading arm (#373): the profile, the two unconditional stats, and the Requests section header and rows. */
class UserDetailScreenshotTest {
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = UserDetailSkeleton()
}
