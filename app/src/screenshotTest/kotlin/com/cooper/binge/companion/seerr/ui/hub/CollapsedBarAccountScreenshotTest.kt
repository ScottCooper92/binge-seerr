package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.CollapsedFilterAction
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.binge.designsystem.R as DesR

private val ACCOUNT = AccountBar(HubAccount(id = 1, name = "Ada Lovelace", isAdmin = true, avatarUrl = null), quota = null)

/**
 * The account in the bar once it has collapsed (#2366). A frame cannot make the scroll gesture, and the templates own their
 * scroll behaviour, so the bar here is the design system's own scaffold held collapsed, with the actions the app hands each template.
 */
class CollapsedBarAccountScreenshotTest {
    /** The Search tab's collapsed bar: the title and the account, which stays reachable after the title row has gone. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun searchCollapsed() = CollapsedBar(title = "Search") { TitleBarAccount(ACCOUNT, enabled = true, onOpen = {}) }

    /** The Discover grid's collapsed bar: the Filters action with its count, then the account. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun gridCollapsed() =
        CollapsedBar(title = "Discover") {
            CollapsedFilterAction(
                activeCount = 2,
                enabled = true,
                collapsedFraction = 1f,
                contentDescription = "Open filters, 2 applied",
                onClick = {},
            )
            TitleBarAccount(ACCOUNT, enabled = true, onOpen = {})
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsedBar(
    title: String,
    actions: @Composable RowScope.() -> Unit,
) {
    BingeScreenScaffold(
        title = title,
        bar = ScreenBar.Large,
        scrollBehavior = collapsedLargeBar(),
        actions = { actions() },
    ) { Box(Modifier.fillMaxSize()) }
}

/** A large bar's scroll behaviour held fully collapsed: its expanded height down to the single row, as a negative offset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun collapsedLargeBar(): TopAppBarScrollBehavior {
    val limit =
        -with(LocalDensity.current) {
            (
                dimensionResource(DesR.dimen.large_top_bar_expanded_height) -
                    TopAppBarDefaults.TopAppBarExpandedHeight
            ).toPx()
        }
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        TopAppBarState(initialHeightOffsetLimit = limit, initialHeightOffset = limit, initialContentOffset = 0f),
    )
}
