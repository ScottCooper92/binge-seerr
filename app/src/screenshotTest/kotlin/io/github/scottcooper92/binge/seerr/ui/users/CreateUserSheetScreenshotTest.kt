package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

private val SHEET_WIDTH = 411.dp

/**
 * What the create-account sheet says before it is submitted. Every state here is one the sheet knew
 * and did not show, so a frame is the only thing that can tell whether it does now.
 *
 * The modal window does not capture, which is why the body was lifted out to render on its own.
 */
class CreateUserSheetScreenshotTest {
    /** The generate row live, and a password long enough to pass. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun ready() =
        Frame(
            CreateUserDraft(
                email = "ada@example.com",
                username = "Ada Lovelace",
                password = "hunter2hunter2",
                canGeneratePassword = true,
            ),
        )

    /**
     * A password under the minimum, which had only a greyed-out Create to say so, and a generate row
     * the server cannot offer whose label read as live.
     *
     * The username's new placeholder is not here and cannot be: M3 shows a placeholder only while
     * the field is focused, and a preview renders nothing focused. Its email sibling is unframed for
     * the same reason.
     */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun notReady() =
        Frame(
            CreateUserDraft(
                email = "ada@example.com",
                username = "",
                password = "hunter",
                canGeneratePassword = false,
            ),
        )
}

@Composable
private fun Frame(draft: CreateUserDraft) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        CreateUserSheetContent(draft = draft, saving = false, onEditDraft = {}, onCreate = {})
    }
}
