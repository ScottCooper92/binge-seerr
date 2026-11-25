package com.cooper.binge.companion.seerr.ui.users

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/** Adding a user is the footer's button: there while browsing, gone while rows are ticked or when the viewer may not add. */
@RunWith(RobolectricTestRunner::class)
class UsersAddFooterTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var created = 0

    private fun show(
        canAdmit: Boolean,
        selection: Set<Int> = emptySet(),
    ) {
        rule.setContent {
            BingeExpressiveTheme {
                UsersScreen(
                    state =
                        UsersUiState.Ready(
                            sort = UserSort.Created,
                            selection = selection,
                            edit = null,
                            offered = emptyList(),
                            canAdmit = canAdmit,
                        ),
                    users = flowOf(PagingData.empty()),
                    events = emptyFlow(),
                    actions =
                        UsersActions(
                            onBack = {},
                            onSortChange = {},
                            onOpen = {},
                            onToggleSelected = {},
                            onClearSelection = {},
                            onStartBulkEdit = {},
                            onTogglePermission = {},
                            onApplyBulkEdit = {},
                            onCancelBulkEdit = {},
                            onRetryLoad = {},
                            admission = UserAdmissionActions({}, {}, {}, { created++ }, {}, {}, {}, {}, {}, {}),
                        ),
                )
            }
        }
    }

    @Test
    fun `a viewer who may add sees the footer button and it starts a new user`() {
        show(canAdmit = true)
        rule.onNodeWithText(string(AdminR.string.users_add)).performClick()
        assertEquals(1, created)
    }

    @Test
    fun `a viewer who may not add has no footer`() {
        show(canAdmit = false)
        rule.onAllNodesWithText(string(AdminR.string.users_add)).assertCountEquals(0)
    }

    @Test
    fun `ticking rows takes the footer away`() {
        show(canAdmit = true, selection = setOf(7))
        rule.onAllNodesWithText(string(AdminR.string.users_add)).assertCountEquals(0)
    }
}
