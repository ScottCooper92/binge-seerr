package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SEERR_NETWORK =
    """{"csrfProtection":false,"trustProxy":true,"forceIpv4First":false,
        "proxy":{"enabled":false,"hostname":"","port":8080,"useSsl":false,"user":"","password":"","bypassFilter":"","bypassLocalAddresses":true},
        "dnsCache":{"enabled":true,"forceMinTtl":0,"forceMaxTtl":-1}}"""

private const val JELLYSEERR_NETWORK = """{"csrfProtection":true,"trustProxy":false}"""

class NetworkViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): NetworkViewModel {
        val vm = NetworkViewModel(seerr.connection(this))
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun NetworkViewModel.awaitReady(): EditorUiState.Ready<NetworkForm> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<NetworkForm>

    @Test
    fun `a seerr's proxy and dns cache are read, and a proxy that is on needs an address`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK)
            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK)
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertTrue(draft.trustProxy)
            assertEquals(false, draft.forceIpv4First)
            assertEquals("8080", draft.proxy?.port)
            assertEquals(true, draft.dnsCache?.enabled)
            assertEquals("", draft.dnsCache?.maxTtl)

            vm.edit { it.copy(proxy = it.proxy?.copy(enabled = true)) }
            assertFalse(vm.awaitReady().draft.valid)
            vm.edit {
                it.copy(
                    proxy = it.proxy?.copy(host = "proxy.local", port = "3128", user = "me"),
                    dnsCache = it.dnsCache?.copy(maxTtl = "600"),
                )
            }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/network")).jsonObject
            val proxy = sent.getValue("proxy").jsonObject
            assertEquals("true", proxy.getValue("enabled").jsonPrimitive.content)
            assertEquals("proxy.local", proxy.getValue("hostname").jsonPrimitive.content)
            assertEquals("3128", proxy.getValue("port").jsonPrimitive.content)
            assertFalse(proxy.getValue("port").jsonPrimitive.isString)
            assertEquals("false", proxy.getValue("useSsl").jsonPrimitive.content)
            val cache = sent.getValue("dnsCache").jsonObject
            assertEquals("600", cache.getValue("forceMaxTtl").jsonPrimitive.content)
            assertEquals("0", cache.getValue("forceMinTtl").jsonPrimitive.content)
        }

    @Test
    fun `a jellyseerr has the two switches and is sent nothing else`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "2.7.3")
            seerr.serve("GET /api/v1/settings/network", JELLYSEERR_NETWORK)
            seerr.serve("POST /api/v1/settings/network", JELLYSEERR_NETWORK)
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertTrue(draft.csrfProtection)
            assertNull(draft.forceIpv4First)
            assertNull(draft.proxy)
            assertNull(draft.dnsCache)

            vm.edit { it.copy(trustProxy = true) }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/network")).jsonObject
            assertEquals(setOf("csrfProtection", "trustProxy"), sent.keys)
            assertEquals("true", sent.getValue("trustProxy").jsonPrimitive.content)
        }
}
