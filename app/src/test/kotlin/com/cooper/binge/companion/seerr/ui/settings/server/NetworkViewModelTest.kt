package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.SAVE_AS_MADE_DELAY_MILLIS
import com.cooper.binge.companion.seerr.ui.users.settings.SaveStatus
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
    """{"csrfProtection":false,"trustProxy":true,"forceIpv4First":false,"apiRequestTimeout":30000,
        "proxy":{"enabled":false,"hostname":"","port":8080,"useSsl":false,"user":"","password":"","bypassFilter":"","bypassLocalAddresses":true},
        "dnsCache":{"enabled":true,"forceMinTtl":0,"forceMaxTtl":-1}}"""

private const val JELLYSEERR_NETWORK = """{"csrfProtection":true,"trustProxy":false}"""

class NetworkViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        seerr.start()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): NetworkViewModel {
        val vm = NetworkViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, backgroundScope)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun NetworkViewModel.awaitReady(
        where: (EditorUiState.Ready<NetworkForm>) -> Boolean = { true },
    ): EditorUiState.Ready<NetworkForm> = uiState.first { it is EditorUiState.Ready && where(it) } as EditorUiState.Ready<NetworkForm>

    /** The page saves as it changes: the change goes out once the delay has passed, and the answer is adopted. */
    private suspend fun TestScope.awaitWritten(vm: NetworkViewModel) {
        advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
        vm.awaitReady { !it.dirty && it.saveStatus == SaveStatus.Saved }
    }

    @Test
    fun `a change is written as made, as the saved record with that one change`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK)
            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(csrfProtection = true) }
            runCurrent()
            assertEquals("nothing is written before the delay", 0, seerr.count("POST", "/api/v1/settings/network"))
            awaitWritten(vm)

            assertEquals(1, seerr.count("POST", "/api/v1/settings/network"))
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/network")).jsonObject
            assertEquals("true", sent.getValue("csrfProtection").jsonPrimitive.content)
            assertEquals("true", sent.getValue("trustProxy").jsonPrimitive.content)
            assertEquals("30000", sent.getValue("apiRequestTimeout").jsonPrimitive.content)
            assertEquals(
                "8080",
                sent
                    .getValue("proxy")
                    .jsonObject
                    .getValue("port")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `a write that fails keeps the change, says so, and a retry sends it`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK)
            seerr.serve("POST /api/v1/settings/network", """{"message":"nope"}""", code = 500)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(trustProxy = false) }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            val failed = vm.awaitReady { it.saveFailed }
            assertFalse("the change stays, unsent", failed.draft.trustProxy)
            assertTrue(failed.dirty)

            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK.replace("\"trustProxy\":true", "\"trustProxy\":false"))
            vm.save()
            val saved = vm.awaitReady { !it.saveFailed && !it.dirty }
            assertFalse(saved.saved.trustProxy)
            assertEquals(2, seerr.count("POST", "/api/v1/settings/network"))
        }

    @Test
    fun `a proxy switched on with no address is never written`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK)
            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(proxy = it.proxy?.copy(enabled = true)) }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()

            assertEquals(0, seerr.count("POST", "/api/v1/settings/network"))
            assertEquals(SaveStatus.Idle, vm.awaitReady().saveStatus)
        }

    @Test
    fun `a ttl the dns cache is not using does not block saving the rest of the page`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK)
            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK)
            val vm = viewModel()
            vm.awaitReady()

            // Off, with a TTL the server would refuse: nothing reads it, so nothing holds the rest of the page.
            vm.edit { it.copy(dnsCache = it.dnsCache?.copy(enabled = false, minTtl = "-1")) }
            assertTrue(vm.awaitReady().draft.valid)

            // On, and the same value is now the page's problem.
            vm.edit { it.copy(dnsCache = it.dnsCache?.copy(enabled = true)) }
            assertFalse(vm.awaitReady().draft.valid)
        }

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
                    proxy = it.proxy?.copy(host = "proxy.local", port = "3128", user = "me", password = "secret"),
                    dnsCache = it.dnsCache?.copy(maxTtl = "600"),
                )
            }
            awaitWritten(vm)

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
            awaitWritten(vm)
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/network")).jsonObject
            assertEquals(setOf("csrfProtection", "trustProxy"), sent.keys)
            assertEquals("true", sent.getValue("trustProxy").jsonPrimitive.content)
        }

    @Test
    fun `the api request timeout is read and sent in milliseconds, shown in seconds`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK)
            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK)
            val vm = viewModel()
            assertEquals("30", vm.awaitReady().draft.apiRequestTimeout)

            vm.edit { it.copy(apiRequestTimeout = "-1") }
            assertFalse(vm.awaitReady().draft.valid)
            vm.edit { it.copy(apiRequestTimeout = "45") }
            awaitWritten(vm)

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/network")).jsonObject
            assertEquals("45000", sent.getValue("apiRequestTimeout").jsonPrimitive.content)
        }

    @Test
    fun `an api request timeout past the int range loads and is refused or sent without wrapping`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK.replace("30000", "3000000000"))
            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK)
            val vm = viewModel()
            assertEquals("3000000", vm.awaitReady().draft.apiRequestTimeout)

            vm.edit { it.copy(apiRequestTimeout = Long.MAX_VALUE.toString()) }
            assertFalse(vm.awaitReady().draft.valid)
            vm.edit { it.copy(apiRequestTimeout = "2147484") }
            assertTrue(vm.awaitReady().draft.valid)
            awaitWritten(vm)

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/network")).jsonObject
            assertEquals("2147484000", sent.getValue("apiRequestTimeout").jsonPrimitive.content)
        }

    @Test
    fun `a fractional api request timeout from the web client loads and is rounded to whole milliseconds`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK.replace("30000", "1100.0000000000002"))
            val vm = viewModel()
            assertEquals("1.1", vm.awaitReady().draft.apiRequestTimeout)
        }

    @Test
    fun `an untouched api request timeout is sent back as the server had it`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK.replace("30000", "1500"))
            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK)
            val vm = viewModel()
            assertEquals("1.5", vm.awaitReady().draft.apiRequestTimeout)

            vm.edit { it.copy(trustProxy = false) }
            awaitWritten(vm)

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/network")).jsonObject
            assertEquals("1500", sent.getValue("apiRequestTimeout").jsonPrimitive.content)
        }

    @Test
    fun `a fractional api request timeout entered in the app is sent as whole milliseconds`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", SEERR_NETWORK)
            seerr.serve("POST /api/v1/settings/network", SEERR_NETWORK)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(apiRequestTimeout = "1.5") }
            assertTrue(vm.awaitReady().draft.valid)
            awaitWritten(vm)

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/network")).jsonObject
            assertEquals("1500", sent.getValue("apiRequestTimeout").jsonPrimitive.content)
        }
}
