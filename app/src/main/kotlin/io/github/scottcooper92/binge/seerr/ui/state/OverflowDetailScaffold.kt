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
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding

/**
 * The frame [io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailScreen] and
 * [io.github.scottcooper92.binge.seerr.ui.users.UserDetailScreen] share: [BingeScreenScaffold]'s top bar
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
    overflowIcon: ImageVector = Icons.Filled.MoreVert,
    leadingActions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    BingeScreenScaffold(
        bar = ScreenBar.Small,
        title = title,
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (showOverflow) {
                leadingActions()
                IconButton(onClick = onOverflowClick) {
                    Icon(overflowIcon, contentDescription = overflowContentDescription)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            content(padding.screenInnerPadding())
        }
    }
}
