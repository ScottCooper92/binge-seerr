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
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.FormSaveFailureSnackbar
import com.binge.designsystem.template.FormSaveStatus
import com.binge.designsystem.template.FormSaveStatusText
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEventSnackbarEffect
import kotlinx.coroutines.flow.Flow

/**
 * The frame of a server page whose actions are immediate rather than a draft: a title, and a snackbar for each outcome.
 *
 * [content] is handed the space above and below it, the transparent top bar and the navigation bar: a body that scrolls
 * folds it into its scroll, and one that does not pads by it.
 *
 * [saveStatus], where the page has something that saves as it changes (#930), puts its Saving and Saved in the top bar
 * and keeps the Retry snackbar up after a failed save until one succeeds.
 */
@Composable
internal fun ServerActionPage(
    title: String,
    events: Flow<EditorEvent>,
    onBack: () -> Unit,
    saveStatus: FormSaveStatus? = null,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorEventSnackbarEffect(events, snackbarHostState)
    saveStatus?.let { FormSaveFailureSnackbar(it, snackbarHostState) }
    BingeScreenScaffold(
        bar = ScreenBar.Small,
        title = title,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = { saveStatus?.let { FormSaveStatusText(it) } },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) { content(padding.screenInnerPadding()) }
    }
}
