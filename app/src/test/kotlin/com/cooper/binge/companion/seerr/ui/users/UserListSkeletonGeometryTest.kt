package com.cooper.binge.companion.seerr.ui.users

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The users list's skeleton holds the first row where the loaded list puts it, in position and in size, at font scales of 1,
 * 1.3 and 2. The anchor is the first row: the list fills the screen, so its own top never moves, and a plate that starts at the
 * same corner but is shorter than the row agrees on every edge a position check measures while every row below it moves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserListSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `on a phone the skeleton holds the first row where the list puts it`() = check()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 1.3f)
    fun `on a phone at a font scale of 1 point 3 the skeleton holds the first row where the list puts it`() = check()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2f)
    fun `on a phone at a font scale of 2 the skeleton holds the first row where the list puts it`() = check()

    private fun check() {
        val anchor = LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)
        rule.assertSkeletonReservesGeometry(anchors = listOf(anchor), checkSize = true) { resolved ->
            val rows = remember { MutableSharedFlow<PagingData<UserItem>>(replay = 1) }
            LaunchedEffect(resolved) { if (resolved) rows.emit(PagingData.from(listOf(user(1), user(2)))) }
            SeerrTheme {
                UsersBody(
                    lazyItems = rows.collectAsLazyPagingItems(),
                    lastRefresh = null,
                    selection = emptySet(),
                    onOpen = {},
                    onToggleSelected = {},
                )
            }
        }
    }

    private fun user(id: Int) =
        UserItem(
            id = id,
            name = "Ana Lopez",
            email = "ana@example.com",
            handle = null,
            avatarUrl = null,
            origin = UserOrigin.Plex,
            permissions = 2,
            requestCount = 12,
            createdAtMillis = 1_700_000_000_000L,
        )
}
