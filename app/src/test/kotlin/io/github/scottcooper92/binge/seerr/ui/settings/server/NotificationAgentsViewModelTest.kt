package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
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
        val vm = NotificationAgentsViewModel(seerr.connection(this))
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
}
