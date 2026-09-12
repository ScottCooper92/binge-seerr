package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
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

class NotificationsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$REQUEST,"userType":3}""")
        seerr.serve(
            "GET /api/v1/user/8/settings/notifications",
            """{"emailEnabled":true,"pgpKey":null,"discordEnabled":false,"discordId":"1234","telegramEnabled":true,
               "telegramBotUsername":"seerr_bot","telegramChatId":"99","telegramSendSilently":true,
               "pushbulletAccessToken":"pb-token","notificationTypes":{"email":12,"telegram":4}}""",
        )
        seerr.serve("POST /api/v1/user/8/settings/notifications")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): NotificationsViewModel {
        val vm = NotificationsViewModel(seerr.connection(this), 8)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun NotificationsViewModel.awaitReady(): EditorUiState.Ready<NotificationSettings> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<NotificationSettings>

    @Test
    fun `each agent reads with its fields and its bitmask, and a keyed agent without a toggle reads as on`() =
        runTest {
            val draft = viewModel().awaitReady().draft

            assertEquals(
                AgentSettings(enabled = true, fields = mapOf(AgentField.PgpKey to ""), types = 12),
                draft.agent(NotificationAgent.Email),
            )
            assertEquals(
                AgentSettings(enabled = true, fields = mapOf(AgentField.TelegramChatId to "99"), sendSilently = true, types = 4),
                draft.agent(NotificationAgent.Telegram),
            )
            assertTrue(draft.isOn(NotificationAgent.Pushbullet))
            assertFalse(draft.isOn(NotificationAgent.Pushover))
            assertFalse(draft.isOn(NotificationAgent.Discord))
            assertEquals("seerr_bot", draft.telegramBotUsername)
            assertFalse(draft.isModerator)
        }

    @Test
    fun `a user who manages requests is offered the moderation events`() =
        runTest {
            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$MANAGE_REQUESTS,"userType":3}""")
            assertTrue(viewModel().awaitReady().draft.isModerator)
        }

    @Test
    fun `saving posts every agent's fields and the whole bitmask table`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { settings ->
                settings
                    .update(NotificationAgent.Discord) { it.copy(enabled = true, types = NotificationType.MediaApproved.bit) }
                    .update(NotificationAgent.Email) { it.copy(types = it.types or NotificationType.IssueComment.bit) }
            }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/notifications")).jsonObject
            assertEquals("true", sent.getValue("discordEnabled").jsonPrimitive.content)
            assertEquals("1234", sent.getValue("discordId").jsonPrimitive.content)
            assertNull(sent["pgpKey"])
            assertEquals("true", sent.getValue("telegramSendSilently").jsonPrimitive.content)
            val types = sent.getValue("notificationTypes").jsonObject
            assertEquals("4", types.getValue("discord").jsonPrimitive.content)
            assertEquals("${12 or (1 shl 9)}", types.getValue("email").jsonPrimitive.content)
            assertEquals("0", types.getValue("webpush").jsonPrimitive.content)
        }
}
