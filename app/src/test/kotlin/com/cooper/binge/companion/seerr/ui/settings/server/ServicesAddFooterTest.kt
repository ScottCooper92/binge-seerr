package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/** Adding on the services page is one footer button that asks what to add, not a row at the end of each group. */
@RunWith(RobolectricTestRunner::class)
class ServicesAddFooterTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val opened = mutableListOf<Pair<ServiceType, Int?>>()
    private val rules = mutableListOf<Int?>()

    private fun show(withRules: Boolean) {
        rule.setContent {
            BingeExpressiveTheme {
                ServicesScreen(
                    state = ServicesUiState.Ready(instances = emptyList(), rules = if (withRules) emptyList() else null),
                    actions =
                        ServicesActions(onBack = {}, onRetry = {}, onOpenInstance = { t, id -> opened += t to id }, onOpenRule = {
                            rules +=
                                it
                        }),
                )
            }
        }
    }

    @Test
    fun `the groups carry no add rows`() {
        show(withRules = true)
        rule.onAllNodesWithText(string(AdminR.string.server_settings_dvr_add, "Radarr")).assertCountEquals(0)
        rule.onAllNodesWithText(string(AdminR.string.server_settings_rule_add)).assertCountEquals(0)
    }

    @Test
    fun `the footer offers each service and the rule and a pick opens that form`() {
        show(withRules = true)
        rule.onNodeWithText(string(AdminR.string.server_settings_services_add)).performClick()
        rule.onNodeWithText(string(AdminR.string.server_settings_rule_add)).assertExists()
        rule.onNodeWithText(string(AdminR.string.server_settings_dvr_add, "Sonarr")).performClick()
        assertEquals(listOf(ServiceType.Sonarr to null), opened)
    }

    @Test
    fun `a rule is offered only where the server has rules`() {
        show(withRules = false)
        rule.onNodeWithText(string(AdminR.string.server_settings_services_add)).performClick()
        rule.onNodeWithText(string(AdminR.string.server_settings_dvr_add, "Radarr")).assertExists()
        rule.onAllNodesWithText(string(AdminR.string.server_settings_rule_add)).assertCountEquals(0)
    }

    @Test
    fun `picking the rule opens a new rule`() {
        show(withRules = true)
        rule.onNodeWithText(string(AdminR.string.server_settings_services_add)).performClick()
        rule.onNodeWithText(string(AdminR.string.server_settings_rule_add)).performClick()
        assertEquals(listOf<Int?>(null), rules)
    }

    @Test
    fun `an empty group says so instead of showing a bare header`() {
        show(withRules = true)
        rule.onNodeWithText(string(AdminR.string.server_settings_dvr_none, "Radarr")).assertExists()
        rule.onNodeWithText(string(AdminR.string.server_settings_dvr_none, "Sonarr")).assertExists()
        rule.onNodeWithText(string(AdminR.string.server_settings_rules_none)).assertExists()
    }

    @Test
    fun `no rules group at all where the server has no rules`() {
        show(withRules = false)
        rule.onAllNodesWithText(string(AdminR.string.server_settings_rules_none)).assertCountEquals(0)
    }
}
