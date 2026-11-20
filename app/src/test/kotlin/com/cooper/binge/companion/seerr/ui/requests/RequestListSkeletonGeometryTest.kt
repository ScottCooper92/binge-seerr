package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestItem
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The requests list's skeleton holds the first row where the loaded list puts it, in position and in size. The anchor is the
 * first row: the list fills the screen, so its own top never moves, and a plate that starts at the same corner but is shorter
 * than the row agrees on every edge a position check measures while every row below it lands somewhere else.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RequestListSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `on a phone the skeleton holds the first row where the list puts it`() = check()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2f)
    fun `on a phone at a font scale of 2 the skeleton holds the first row where the list puts it`() = check()

    private fun check() {
        val anchor = LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)
        rule.assertSkeletonReservesGeometry(anchors = listOf(anchor), checkSize = true) { resolved ->
            val rows = remember { MutableSharedFlow<PagingData<RequestItem>>(replay = 1) }
            LaunchedEffect(resolved) { if (resolved) rows.emit(PagingData.from(listOf(requestItem(id = 1), requestItem(id = 2)))) }
            SeerrTheme {
                RequestsBody(
                    filter = RequestFilter.All,
                    lazyItems = rows.collectAsLazyPagingItems(),
                    lastRefresh = null,
                    scope = ModerationScope(),
                    now = NOW_MILLIS,
                    onOpen = {},
                    onManage = {},
                    onPull = {},
                )
            }
        }
    }

    private companion object {
        const val NOW_MILLIS = 1_700_000_000_000L
    }
}
