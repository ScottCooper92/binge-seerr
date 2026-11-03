package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)

    @Before
    fun setUp() {
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
            val vm = ServicesViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
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
            val vm = ServicesViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            vm.reload()
            val ready = vm.uiState.first { it is ServicesUiState.Ready } as ServicesUiState.Ready
            assertNull(ready.rules)
            assertEquals(0, seerr.count("GET", "/api/v1/overrideRule"))
        }

    @Test
    fun `a pull swaps in the new list, and is refreshing only while the read runs`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = ServicesViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            vm.reload()
            vm.uiState.first { it is ServicesUiState.Ready }
            seerr.serve(
                "GET /api/v1/settings/radarr",
                """[{"id":1,"name":"Movies","hostname":"radarr.local","port":7878},{"id":2,"name":"Films 4K","hostname":"r4k.local","port":7878}]""",
            )

            vm.refresh()

            val fresh = vm.uiState.first { it is ServicesUiState.Ready && it.instances.size == 3 } as ServicesUiState.Ready
            assertFalse(fresh.isRefreshing)
        }

    @Test
    fun `a failed pull keeps the list and says so, where a reload would replace it with the error`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = ServicesViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            vm.reload()
            vm.uiState.first { it is ServicesUiState.Ready }
            seerr.serve("GET /api/v1/settings/radarr", code = 503)

            val failed = awaitEvent(vm.events)
            vm.refresh()

            assertEquals(ServicesEvent.RefreshFailed(SeerrError.Server), failed.await())
            val kept = vm.uiState.first { it is ServicesUiState.Ready && !it.isRefreshing } as ServicesUiState.Ready
            assertEquals(2, kept.instances.size)
        }
}
