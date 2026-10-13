package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.tv.component.TvConfirmDialogContent
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_DIALOG_NAME = "$LONG_NAME of the Upstairs Server"

/** The width an alert dialog gets on a 412dp phone: the screen less a 24dp margin each side. */
private val DIALOG_WIDTH = 364.dp

/**
 * The confirm dialogs' cards with the app's longest titles, messages and labels, at font scales 1.3 and 2.0, through the
 * design system's `assertNoTextClipped`. A dialog is a modal window, which is not in the test's tree, so its card is
 * rendered inline at the width the window gives it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the confirm dialogs at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { ConfirmDialogs() }

    @Test fun `the confirm dialogs at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { ConfirmDialogs() }
}

/**
 * The television's confirm dialog, at font scale 1.3 only: the TV is held to 1.3, the largest the platform offers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvConfirmDialogClippingTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test fun `the disconnect dialog at 1_3`() =
        rule.assertNoTvClippingAt(FONT_SCALE_13) {
            TvConfirmDialogContent(
                title = stringResource(R.string.hub_disconnect_confirm_title),
                message = stringResource(R.string.hub_disconnect_confirm_message),
                confirmLabel = stringResource(R.string.tv_settings_disconnect_confirm),
                dismissLabel = stringResource(R.string.editor_discard),
                destructive = true,
                onConfirm = {},
                onDismissRequest = {},
            )
        }

    @Test fun `the block dialog at 1_3`() =
        rule.assertNoTvClippingAt(FONT_SCALE_13) {
            TvConfirmDialogContent(
                title = stringResource(R.string.request_block_confirm_title),
                message = stringResource(R.string.request_block_confirm_message),
                confirmLabel = stringResource(R.string.tv_detail_block),
                dismissLabel = stringResource(R.string.editor_discard),
                destructive = true,
                onConfirm = {},
                onDismissRequest = {},
            )
        }
}

@Composable
private fun ConfirmDialogs() {
    Column {
        Confirm(
            stringResource(R.string.user_delete_confirm_title, LONG_DIALOG_NAME),
            stringResource(R.string.user_delete_confirm_message),
            stringResource(R.string.user_delete),
        )
        Confirm(
            stringResource(R.string.hub_disconnect_confirm_title),
            stringResource(R.string.hub_disconnect_confirm_message),
            stringResource(R.string.hub_disconnect),
        )
        Confirm(
            stringResource(R.string.server_settings_api_key_regenerate_title),
            stringResource(R.string.server_settings_api_key_regenerate_message),
            stringResource(R.string.server_settings_api_key_regenerate),
        )
        Confirm(
            stringResource(R.string.server_settings_job_schedule_title, LONG_DIALOG_NAME),
            stringResource(R.string.server_settings_job_schedule_message),
            stringResource(R.string.editor_done),
        )
    }
}

@Composable
private fun Confirm(
    title: String,
    message: String,
    confirmLabel: String,
) {
    BingeConfirmDialogContent(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = {},
        onDismissRequest = {},
        destructive = true,
        modifier = Modifier.width(DIALOG_WIDTH),
    )
}
