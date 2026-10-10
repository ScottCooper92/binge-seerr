package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetConfirm
import com.binge.designsystem.tv.component.TvSideSheetPanel
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetTitle
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews

/**
 * The end-edge action sheet's panel and the rows on it. Focus is a seed rather than a runtime state, so
 * each frame names the row that holds it: the first action, a destructive one (whose resting tone is the
 * error colour and whose focused one is the fill), and Cancel on a confirm, where focus lands on purpose
 * so a stray OK does nothing. The scrim and the focus trap are the modal half and are not framed.
 */
class TvActionSheetScreenshotTest {
    /** The first action focused, as the sheet opens. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun FirstActionFocused() =
        SheetFrame {
            TvSideSheetTitle("Heat")
            TvSideSheetBody("Scott · Pending")
            TvSideSheetRow(label = "Approve", onClick = {}, initiallyFocused = true)
            TvSideSheetRow(label = "Decline", onClick = {})
            TvSideSheetRow(label = "Remove", onClick = {}, destructive = true)
        }

    /** A destructive row reached: it fills like the rest rather than keeping its error tone on the fill. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun DestructiveFocused() =
        SheetFrame {
            TvSideSheetTitle("Heat")
            TvSideSheetBody("Scott · Pending")
            TvSideSheetRow(label = "Approve", onClick = {})
            TvSideSheetRow(label = "Decline", onClick = {})
            TvSideSheetRow(label = "Remove", onClick = {}, destructive = true, initiallyFocused = true)
        }

    /** A row that is only a read-out: dimmed, and out of the focus order. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun DisabledRow() =
        SheetFrame {
            TvSideSheetTitle("Heat")
            TvSideSheetRow(label = "Approve", onClick = {}, initiallyFocused = true)
            TvSideSheetRow(label = "Already approved", onClick = {}, enabled = false)
        }

    /** The confirm step with nothing focused yet: the destructive confirm above Cancel. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ConfirmAtRest() = ConfirmFrame(cancelFocused = false)

    /** The confirm step as it lands, with focus on Cancel. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ConfirmCancelFocused() = ConfirmFrame(cancelFocused = true)
}

@Composable
private fun ConfirmFrame(cancelFocused: Boolean) =
    SheetFrame {
        TvSideSheetConfirm(
            title = "Remove this request?",
            message = "The request is deleted. The title stays available to request again.",
            confirmLabel = "Remove",
            onConfirm = {},
            onCancel = {},
            entryFocus = FocusRequester(),
            cancelInitiallyFocused = cancelFocused,
        )
    }

@Composable
private fun SheetFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        TvSideSheetPanel(modifier = Modifier.align(Alignment.CenterEnd), content = content)
    }
}
