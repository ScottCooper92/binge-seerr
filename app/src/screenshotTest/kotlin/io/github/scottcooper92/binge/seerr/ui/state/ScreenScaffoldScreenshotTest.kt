package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

private const val ROWS = 20
private const val SCROLLED_ROW = 3
private const val HALF = 0.5f

/**
 * The transparent top bar every phone screen wears, through [ScreenScaffold]. The screens' own frames
 * all show it at rest, where it looks exactly like the opaque bar it replaced, so these two pin what
 * changed: the glass back button, and the scrim and title colour once rows are under the bar.
 */
class ScreenScaffoldScreenshotTest {
    /** At rest: nothing under the bar, so no scrim, and the first row starts below it. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun atRest() = Frame(collapsed = 0f, firstRow = 0)

    /** Scrolled: rows pass under a half-collapsed bar, and the scrim and the title's colour are halfway in. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun scrolledUnder() = Frame(collapsed = HALF, firstRow = SCROLLED_ROW)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Frame(
    collapsed: Float,
    firstRow: Int,
) {
    val limit = with(LocalDensity.current) { TopAppBarDefaults.TopAppBarExpandedHeight.toPx() }
    val barState = rememberTopAppBarState(initialHeightOffsetLimit = -limit, initialHeightOffset = -limit * collapsed)
    Box(Modifier.height(420.dp)) {
        ScreenScaffold(
            title = "Requests",
            onBack = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(barState),
            actions = {
                IconButton(onClick = {}) { Icon(Icons.Filled.SwapVert, contentDescription = null) }
            },
        ) { padding ->
            LazyColumn(
                state = rememberLazyListState(initialFirstVisibleItemIndex = firstRow),
                modifier = Modifier.padding(padding.outerPadding()),
                contentPadding = padding.innerPadding(),
            ) {
                items(ROWS) { row ->
                    Text(
                        "Row ${row + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}
