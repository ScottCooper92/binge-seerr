package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.SaveFailedSnackbar
import com.cooper.binge.companion.seerr.ui.state.SaveStatusLine
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEventSnackbarEffect
import kotlinx.coroutines.flow.Flow

/**
 * The frame of a server page whose actions are immediate rather than a draft: a title, and a snackbar for each outcome.
 *
 * [content] is handed the space above and below it, the transparent top bar and the navigation bar: a body that scrolls
 * folds it into its scroll, and one that does not pads by it.
 *
 * [saveAsMade], where the page has something that saves as it changes (#930), puts its Saving and Saved in the top bar
 * and keeps the Retry snackbar up after a failed save until one succeeds.
 */
@Composable
internal fun ServerActionPage(
    title: String,
    events: Flow<EditorEvent>,
    onBack: () -> Unit,
    saveAsMade: SaveAsMadeState? = null,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorEventSnackbarEffect(events, snackbarHostState)
    saveAsMade?.let {
        SaveFailedSnackbar(
            failed = it.failed,
            snackbarHostState = snackbarHostState,
            message = stringResource(R.string.editor_save_failed),
            retryLabel = stringResource(R.string.action_try_again),
            onRetry = it.onRetry,
        )
    }
    BingeScreenScaffold(
        bar = ScreenBar.Small,
        title = title,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = { saveAsMade?.let { save -> SaveStatusLine(status = save.status, failed = save.failed) } },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) { content(padding.screenInnerPadding()) }
    }
}

/** Where a page's save-as-made writes stand, for [ServerActionPage]'s status line and Retry snackbar. */
internal class SaveAsMadeState(
    val status: SaveStatus,
    val failed: Boolean,
    val onRetry: () -> Unit,
)
