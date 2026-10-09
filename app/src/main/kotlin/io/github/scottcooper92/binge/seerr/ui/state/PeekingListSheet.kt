package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeSheetDockingHeader
import com.binge.designsystem.component.BingeSheetTopBar
import com.binge.designsystem.R as DesR

/**
 * A sheet for a long list to pick from: it opens part-way, so the page behind stays in view, and drags up to the full
 * height, where its header docks into a top bar with a close button. [actions] (Done, Clear) sit in the header at
 * either height.
 */
@Composable
fun PeekingListSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // Edge to edge: the list runs under the navigation bar and pads its own end, as a long list does.
    BingeBottomSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = false, dockable = true, edgeToEdge = true) {
        BingeSheetDockingHeader(
            header = {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(
                            horizontal = dimensionResource(DesR.dimen.padding_m),
                            vertical = dimensionResource(DesR.dimen.padding_s),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    actions()
                }
            },
            dockedTopBar = { onClose -> BingeSheetTopBar(title = title, onClose = onClose, actions = actions) },
        )
        // Its own scroll, so a list of hundreds is reachable at either height without a lazy list inside the sheet.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
