package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val RADARR = """[{"id":1,"name":"Movies","hostname":"radarr.local","port":7878,"isDefault":true}]"""
private const val SONARR = """[{"id":3,"name":"","hostname":"sonarr.local","port":8989,"is4k":true},{"name":"unsaved"}]"""
private const val RULES = """[{"id":11,"radarrServiceId":1,"users":"3","genre":"28"},{"id":12,"sonarrServiceId":9}]"""

class ServicesViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.serve("GET /api/v1/settings/radarr", RADARR)
        seerr.serve("GET /api/v1/settings/sonarr", SONARR)
        seerr.serve("GET /api/v1/overrideRule", RULES)
    }

    @After
    fun tearDown() = seerr.close()

    @Test
    fun `lists every saved instance and the rules, naming a rule's instance or its id where gone`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = ServicesViewModel(seerr.connection(this))
            vm.reload()
            val ready = vm.uiState.first { it is ServicesUiState.Ready } as ServicesUiState.Ready

            assertEquals(listOf(1 to ServiceType.Radarr, 3 to ServiceType.Sonarr), ready.instances.map { it.id to it.type })
            assertEquals("Sonarr", ready.instances[1].name)
            assertEquals("sonarr.local:8989", ready.instances[1].address)
            assertEquals(listOf("Movies", "Sonarr 9"), ready.rules?.map { it.instanceName })
            assertEquals(listOf(2, 0), ready.rules?.map { it.conditions })
        }

    @Test
    fun `an overseerr has no rules, and is not asked for them`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            val vm = ServicesViewModel(seerr.connection(this))
            vm.reload()
            val ready = vm.uiState.first { it is ServicesUiState.Ready } as ServicesUiState.Ready
            assertNull(ready.rules)
            assertEquals(0, seerr.count("GET", "/api/v1/overrideRule"))
        }
}
