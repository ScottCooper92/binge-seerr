package io.github.scottcooper92.binge.seerr.ui.settings.server

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
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEventSnackbarEffect
import kotlinx.coroutines.flow.Flow

/**
 * The frame of a server page whose actions are immediate rather than a draft: a title, and a snackbar for each outcome.
 *
 * [content] is handed the space above and below it, the transparent top bar and the navigation bar: a body that scrolls
 * folds it into its scroll, and one that does not pads by it.
 */
@Composable
internal fun ServerActionPage(
    title: String,
    events: Flow<EditorEvent>,
    onBack: () -> Unit,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorEventSnackbarEffect(events, snackbarHostState)
    BingeScreenScaffold(bar = ScreenBar.Small, title = title, onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) { content(padding.screenInnerPadding()) }
    }
}
