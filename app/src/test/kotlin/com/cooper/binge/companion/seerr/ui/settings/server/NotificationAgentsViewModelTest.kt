package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationAgentsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)

    @Before
    fun setUp() {
        seerr.start()
        ServerAgent.entries.forEach { agent ->
            seerr.serve(
                "GET /api/v1/settings/notifications/${agent.segment}",
                """{"enabled":${agent == ServerAgent.Email},"types":0,"options":{}}""",
            )
        }
        seerr.remove("GET /api/v1/settings/notifications/slack")
    }

    @After
    fun tearDown() = seerr.close()

    private suspend fun TestScope.ready(): AgentsUiState.Ready {
        val vm = NotificationAgentsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
        vm.reload()
        return vm.uiState.first { it is AgentsUiState.Ready } as AgentsUiState.Ready
    }

    @Test
    fun `a seerr lists ntfy and not lunasea, each agent with whether it is on`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val agents = ready().agents
            assertTrue(agents.any { it.agent == ServerAgent.Ntfy })
            assertFalse(agents.any { it.agent == ServerAgent.LunaSea })
            assertEquals(true, agents.first { it.agent == ServerAgent.Email }.enabled)
            assertEquals(false, agents.first { it.agent == ServerAgent.Discord }.enabled)
            assertNull(agents.first { it.agent == ServerAgent.Slack }.enabled)
        }

    @Test
    fun `an overseerr lists lunasea and not ntfy`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            val agents = ready().agents.map { it.agent }
            assertTrue(ServerAgent.LunaSea in agents)
            assertTrue(ServerAgent.Gotify in agents)
            assertFalse(ServerAgent.Ntfy in agents)
            assertEquals(0, seerr.count("GET", "/api/v1/settings/notifications/ntfy"))
        }

    @Test
    fun `a refresh reads the agents again without going back to loading`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = NotificationAgentsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            vm.reload()
            vm.uiState.first { it is AgentsUiState.Ready }
            seerr.serve("GET /api/v1/settings/notifications/discord", """{"enabled":true,"types":0,"options":{}}""")

            vm.refresh()

            val after =
                vm.uiState.first { state ->
                    state is AgentsUiState.Ready &&
                        state.agents.first { it.agent == ServerAgent.Discord }.enabled == true
                }
            assertTrue(after is AgentsUiState.Ready)
        }

    @Test
    fun `an agent saved on the server reads the list again`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val changes = NotificationAgentChanges()
            val vm = NotificationAgentsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, changes)
            vm.reload()
            vm.uiState.first { it is AgentsUiState.Ready }
            seerr.serve("GET /api/v1/settings/notifications/discord", """{"enabled":true,"types":0,"options":{}}""")

            changes.changed(ServerAgent.Discord)

            vm.uiState.first { state ->
                state is AgentsUiState.Ready && state.agents.first { it.agent == ServerAgent.Discord }.enabled == true
            }
        }

    @Test
    fun `a slow read started earlier does not land over the read that replaced it`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val held = seerr.serveHeld("GET /api/v1/settings/notifications/discord")
            val vm = NotificationAgentsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            vm.reload()
            seerr.awaitCount("GET", "/api/v1/settings/notifications/discord", moreThan = 0, timeoutMillis = seerr.expectedCallWaitMillis())
            seerr.serve("GET /api/v1/settings/notifications/discord", """{"enabled":true,"types":0,"options":{}}""")

            vm.refresh()
            vm.uiState.first { state ->
                state is AgentsUiState.Ready && state.agents.first { it.agent == ServerAgent.Discord }.enabled == true
            }
            held.release(body = """{"enabled":false,"types":0,"options":{}}""")
            seerr.awaitCallbacks()
            testScheduler.runCurrent()

            val after = vm.uiState.value as AgentsUiState.Ready
            assertEquals(true, after.agents.first { it.agent == ServerAgent.Discord }.enabled)
        }
}
