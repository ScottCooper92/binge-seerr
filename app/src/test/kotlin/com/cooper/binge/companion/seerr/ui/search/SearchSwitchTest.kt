package com.cooper.binge.companion.seerr.ui.search

import androidx.lifecycle.ViewModelStore
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.settings.server.NetworkForm
import com.cooper.binge.companion.seerr.ui.settings.server.NetworkViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralSettings
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.settings.server.ServerUsersSettings
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import com.cooper.homelab.core.presentation.editor.SAVE_AS_MADE_DELAY_MILLIS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A value a test needs to hold, where the assertion's own check returns nothing. */
private fun <T : Any> nonNull(value: T?): T = checkNotNull(value)

private const val NETWORK = """{"csrfProtection":false,"trustProxy":true}"""

/**
 * A result's switch is the page's own editor under it: the draft's value, the page's write, and the page's failure. A switch for a
 * setting the server lacks is not there, and a viewer who may not change it sees it dimmed.
 */
class SearchSwitchTest {
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

    private suspend fun TestScope.network(): NetworkViewModel {
        val vm = NetworkViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, backgroundScope)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun NetworkViewModel.ready(where: (EditorUiState.Ready<NetworkForm>) -> Boolean = { true }) =
        uiState.first { it is EditorUiState.Ready && where(it) } as EditorUiState.Ready<NetworkForm>

    private fun NetworkViewModel.csrf(
        ready: EditorUiState.Ready<NetworkForm>,
        mayChange: Boolean = true,
    ) = networkSwitches.getValue(SettingRows.NETWORK_CSRF).toggle(ready.draft, ready.saveStatus == SaveStatus.Saving, mayChange, ::edit)

    @Test
    fun `a switch shows the draft's value and writes through the page's editor`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", NETWORK)
            seerr.serve("POST /api/v1/settings/network", """{"csrfProtection":true,"trustProxy":true}""")
            val vm = network()
            val toggle = nonNull(vm.csrf(vm.ready()))
            assertFalse(toggle.checked)

            toggle.onCheckedChange(true)
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)

            val written = vm.ready { !it.dirty && it.saveStatus == SaveStatus.Saved }
            assertTrue("the page agrees", written.draft.csrfProtection)
            assertTrue(nonNull(vm.csrf(written)).checked)
            assertEquals(1, seerr.count("POST", "/api/v1/settings/network"))
        }

    @Test
    fun `a refused write shows the page's failure and the switch goes back`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/network", NETWORK)
            seerr.serve("POST /api/v1/settings/network", """{"message":"bad value"}""", code = 400)
            val vm = network()
            val toggle = nonNull(vm.csrf(vm.ready()))
            val refused = awaitEvent(vm.events)

            toggle.onCheckedChange(true)
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)

            assertEquals(EditorEvent.Failed(SeerrError.Rejected, "bad value"), refused.await())
            val back = vm.ready { !it.dirty && it.saveStatus == SaveStatus.Idle }
            assertFalse("back to the stored value", nonNull(vm.csrf(back)).checked)
        }

    @Test
    fun `a viewer who may not change it sees the switch dimmed at its stored value`() =
        runTest {
            seerr.viewer(id = 2, permissions = 32)
            seerr.serve("GET /api/v1/settings/network", NETWORK)
            val vm = network()

            val toggle = nonNull(vm.csrf(vm.ready(), mayChange = false))

            assertFalse(toggle.enabled)
            assertFalse(toggle.checked)
        }

    @Test
    fun `a switch is a spinner while its write is in flight`() {
        val toggle = networkSwitches.getValue(SettingRows.NETWORK_CSRF).toggle(NetworkForm(), saving = true, mayChange = true) {}
        assertTrue(nonNull(toggle).loading)
    }

    @Test
    fun `local login cannot be switched off while media server sign in is off`() {
        val switch = usersSwitches.getValue(SettingRows.USERS_LOCAL_LOGIN)
        val stuck = ServerUsersSettings(localLogin = true, mediaServerLogin = false)
        val free = ServerUsersSettings(localLogin = true, mediaServerLogin = true)

        assertFalse("the page would refuse to save it", nonNull(switch.toggle(stuck, saving = false, mayChange = true) {}).enabled)
        assertTrue(nonNull(switch.toggle(free, saving = false, mayChange = true) {}).enabled)
        val off = stuck.copy(localLogin = false)
        assertTrue("turning it back on is always allowed", nonNull(switch.toggle(off, saving = false, mayChange = true) {}).enabled)
    }

    @Test
    fun `a setting the server does not have gets no switch`() {
        val without = ServerGeneralSettings(hideRequested = null, trustProxy = null)
        assertNull(generalSwitches.getValue(SettingRows.GENERAL_HIDE_REQUESTED).toggle(without, false, true) {})
        assertNull(generalSwitches.getValue(SettingRows.GENERAL_TRUST_PROXY).toggle(without, false, true) {})
        assertNotNull(generalSwitches.getValue(SettingRows.GENERAL_CACHE_IMAGES).toggle(without, false, true) {})
    }

    @Test
    fun `every switch writes its own field and nothing else`() {
        val general = ServerGeneralSettings(hideRequested = false, specialEpisodes = false, trustProxy = false, csrfProtection = false)
        generalSwitches.forEach { (row, switch) ->
            val flipped = switch.write(general, true)
            assertEquals(row, true, switch.read(flipped))
            assertEquals(row, general, switch.write(flipped, switch.read(general) ?: false))
        }
        networkSwitches.forEach { (row, switch) ->
            assertEquals(row, true, switch.read(switch.write(NetworkForm(trustProxy = false, csrfProtection = false), true)))
        }
        assertEquals(false, usersSwitches.getValue(SettingRows.USERS_LOCAL_LOGIN).read(ServerUsersSettings(localLogin = false)))
    }

    @Test
    fun `every switch row is an indexed setting on the page that edits it`() {
        val rows =
            settingsIndex
                .filter { it.row != null }
                .mapNotNull { entry -> (entry.target as? SearchTarget.Server)?.let { entry.row to it.page } }
                .toMap()
        generalSwitches.keys.forEach { assertEquals(it, ServerSettingsPage.General, rows[it]) }
        networkSwitches.keys.forEach { assertEquals(it, ServerSettingsPage.Network, rows[it]) }
        usersSwitches.keys.forEach { assertEquals(it, ServerSettingsPage.Users, rows[it]) }
    }
}
