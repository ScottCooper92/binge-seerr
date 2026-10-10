package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
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
        val vm = NotificationsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, backgroundScope, 8)
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
                AgentSettings(
                    enabled = true,
                    fields = mapOf(AgentField.TelegramChatId to "99", AgentField.TelegramThreadId to ""),
                    sendSilently = true,
                    types = 4,
                ),
                draft.agent(NotificationAgent.Telegram),
            )
            assertTrue(draft.isOn(NotificationAgent.Pushbullet))
            assertFalse(draft.isOn(NotificationAgent.Pushover))
            assertFalse(draft.isOn(NotificationAgent.Discord))
            assertEquals("seerr_bot", draft.telegramBotUsername)
            assertEquals(listOf("1234"), draft.discordIds)
            assertTrue(draft.telegramTopics)
            assertFalse(draft.multipleDiscordIds)
            assertFalse(draft.isModerator)
        }

    @Test
    fun `a user who manages requests is offered the moderation events`() =
        runTest {
            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$MANAGE_REQUESTS,"userType":3}""")
            assertTrue(viewModel().awaitReady().draft.isModerator)
        }

    @Test
    fun `a change posts every agent's fields and the whole bitmask table`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { settings ->
                settings
                    .update(NotificationAgent.Discord) { it.copy(enabled = true, types = NotificationType.MediaApproved.bit) }
                    .update(NotificationAgent.Email) { it.copy(types = it.types or NotificationType.IssueComment.bit) }
            }
            // Notifications save as they change: the edit goes out once the delay has passed.
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.uiState.first { it is EditorUiState.Ready && !it.dirty }

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/notifications")).jsonObject
            assertEquals("true", sent.getValue("discordEnabled").jsonPrimitive.content)
            assertEquals("1234", sent.getValue("discordId").jsonPrimitive.content)
            assertEquals("", sent.getValue("pgpKey").jsonPrimitive.content)
            assertEquals("true", sent.getValue("telegramSendSilently").jsonPrimitive.content)
            val types = sent.getValue("notificationTypes").jsonObject
            assertEquals("4", types.getValue("discord").jsonPrimitive.content)
            assertEquals("${12 or (1 shl 9)}", types.getValue("email").jsonPrimitive.content)
            assertEquals("0", types.getValue("webpush").jsonPrimitive.content)
        }

    /** The server keeps a key the body leaves out, so a cleared token goes out as "", as the web client sends it (#1020). */
    @Test
    fun `clearing the Pushbullet token sends it empty, so the server clears it`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { settings ->
                settings.update(NotificationAgent.Pushbullet) { it.copy(fields = it.fields + (AgentField.PushbulletToken to "  ")) }
            }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.uiState.first { it is EditorUiState.Ready && !it.dirty }

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/notifications")).jsonObject
            assertEquals("", sent.getValue("pushbulletAccessToken").jsonPrimitive.content)
        }

    @Test
    fun `a Discord id that is not digits is never written`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(discordIds = listOf("scott#1234")) }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()

            val ready = vm.awaitReady()
            assertFalse(ready.draft.discordIdsValid)
            assertFalse(ready.draft.valid)
            assertFalse(ready.saving)
            assertEquals(0, seerr.count("POST", "/api/v1/user/8/settings/notifications"))
        }

    @Test
    fun `Seerr's list of Discord ids reads whole, and an added one and the Telegram topic go out with the save`() =
        runTest {
            seerr.serve(
                "GET /api/v1/user/8/settings/notifications",
                """{"discordEnabled":true,"discordIds":["1234","5678"],"telegramEnabled":true,"telegramChatId":"99",
                   "telegramMessageThreadId":"42","notificationTypes":{}}""",
            )
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertEquals(listOf("1234", "5678"), draft.discordIds)
            assertTrue(draft.multipleDiscordIds)
            assertEquals("42", draft.field(AgentField.TelegramThreadId))

            vm.edit { it.copy(discordIds = it.discordIds + " 9999 ").set(AgentField.TelegramThreadId, "7") }
            // Notifications save as they change: the edit goes out once the delay has passed.
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.uiState.first { it is EditorUiState.Ready && !it.dirty }

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/notifications")).jsonObject
            assertEquals("1234", sent.getValue("discordId").jsonPrimitive.content)
            assertEquals(listOf("1234", "5678", "9999"), sent.getValue("discordIds").jsonArray.map { it.jsonPrimitive.content })
            assertEquals("7", sent.getValue("telegramMessageThreadId").jsonPrimitive.content)
        }

    @Test
    fun `the saved Pushover application's sounds are read with the settings`() =
        runTest {
            seerr.serve(
                "GET /api/v1/user/8/settings/notifications",
                """{"pushoverApplicationToken":"azGDORePK8gMaC0QOYAMyEEuzJnyUi","pushoverUserKey":"uQiRzpo4DXghDmr9QzzfQu27cmVRsG",
                   "pushoverSound":"bike","notificationTypes":{}}""",
            )
            seerr.serve(
                "GET /api/v1/settings/notifications/pushover/sounds",
                """[{"name":"bike","description":"Bike"},{"name":"tugboat"}]""",
            )

            val draft = viewModel().awaitReady().draft

            assertEquals(listOf(PushoverSoundChoice("bike", "Bike"), PushoverSoundChoice("tugboat", "tugboat")), draft.pushoverSounds)
            assertEquals("bike", draft.field(AgentField.PushoverSound))
        }

    /** The server serves the list to any signed-in user, so a plain user with a token gets the picker too (#1011). */
    @Test
    fun `a user who is not an admin reads the Pushover sounds as well`() =
        runTest {
            seerr.viewer(id = 8, permissions = REQUEST)
            seerr.serve(
                "GET /api/v1/user/8/settings/notifications",
                """{"pushoverApplicationToken":"azGDORePK8gMaC0QOYAMyEEuzJnyUi","pushoverSound":"bike","notificationTypes":{}}""",
            )
            seerr.serve("GET /api/v1/settings/notifications/pushover/sounds", """[{"name":"bike","description":"Bike"}]""")

            assertEquals(listOf(PushoverSoundChoice("bike", "Bike")), viewModel().awaitReady().draft.pushoverSounds)
        }

    @Test
    fun `sounds the server will not list leave the page to load without them`() =
        runTest {
            seerr.serve(
                "GET /api/v1/user/8/settings/notifications",
                """{"pushoverApplicationToken":"azGDORePK8gMaC0QOYAMyEEuzJnyUi","notificationTypes":{}}""",
            )
            seerr.serve("GET /api/v1/settings/notifications/pushover/sounds", code = 403)

            assertTrue(
                viewModel()
                    .awaitReady()
                    .draft.pushoverSounds
                    .isEmpty(),
            )
        }

    @Test
    fun `Overseerr keeps no Telegram topic, so the page does not offer one`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            assertFalse(viewModel().awaitReady().draft.telegramTopics)
        }

    @Test
    fun `Jellyseerr before 2_2 keeps no Telegram topic, so the page does not offer one`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "2.1.0", settings = "{}")
            assertFalse(viewModel().awaitReady().draft.telegramTopics)
        }
}
