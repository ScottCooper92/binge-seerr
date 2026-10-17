package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
 * The frame [com.cooper.binge.companion.seerr.ui.issues.IssueDetailScreen] and
 * [com.cooper.binge.companion.seerr.ui.users.UserDetailScreen] share: [BingeScreenScaffold]'s top bar
 * with an overflow button that opens a manage sheet, and the `screenOuterPadding()`/`screenInnerPadding()` split
 * every scrolling body under it uses.
 *
 * It owns only the icon button. The two screens' `Ready` and `Loading` arms differ, so each keeps its own `when`
 * and its own manage sheet, opened from [onOverflowClick].
 *
 * @param leadingActions draw before the overflow button.
 * @param trailingActions draw after it. A destructive action goes here, so it is the last thing on the bar (#1531).
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
    leadingActions: @Composable RowScope.() -> Unit = {},
    trailingActions: @Composable () -> Unit = {},
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
                trailingActions()
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            content(padding.screenInnerPadding())
        }
    }
}
