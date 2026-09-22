package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The frame [io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailScreen] and
 * [io.github.scottcooper92.binge.seerr.ui.users.UserDetailScreen] share: [ScreenScaffold]'s top bar
 * with an overflow button that opens a manage sheet, and the `outerPadding()`/`innerPadding()` split
 * every scrolling body under it uses.
 *
 * Deliberately does not own the `Loading`/`Error`/`Ready` dispatch or the overflow sheet itself: the
 * two screens' state types carry genuinely different `Ready` payloads (and even different `Loading`
 * arms — a generic [LoadingScreen] here, a bespoke skeleton there), so each keeps its own exhaustive
 * `when` and its own `if (managing) { ...ManageSheet(...) }` block, wiring [onOverflowClick] to open
 * it. This composable only ever owns the icon button.
 */
@Composable
internal fun OverflowDetailScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState,
    showOverflow: Boolean,
    overflowContentDescription: String,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    ScreenScaffold(
        title = title,
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (showOverflow) {
                IconButton(onClick = onOverflowClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = overflowContentDescription)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.outerPadding())) {
            content(padding.innerPadding())
        }
    }
}
