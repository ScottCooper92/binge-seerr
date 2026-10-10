package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationType
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.RecordingAnalytics
import io.github.scottcooper92.binge.seerr.util.afterProcessDeath
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
import java.util.Base64

private const val EMAIL =
    """{"enabled":true,"types":6,"options":{"emailFrom":"seerr@example.com","senderName":"Seerr","smtpHost":"smtp.example.com",
        "smtpPort":465,"secure":true,"ignoreTls":false,"requireTls":false,"allowSelfSigned":false,"authUser":"","authPass":"",
        "pgpMode":"legacy"}}"""

private const val NTFY =
    """{"enabled":true,"types":2,"options":{"url":"https://ntfy.example.com","topic":"seerr",
        "authMethodUsernamePassword":true,"username":"me","password":"pw",
        "authMethodToken":true,"token":"tk"}}"""

private const val PUSHOVER = """{"enabled":false,"types":0,"options":{"accessToken":"","userToken":"","sound":""}}"""

private const val TELEGRAM = """{"enabled":true,"types":0,"options":{"botAPI":"t","chatId":"1","messageThreadId":""}}"""

private const val SOUNDS = """[{"name":"pushover","description":"Pushover (default)"},{"name":"bike","description":"Bike"}]"""

/** Long enough for a keystroke to land while a sounds fetch is still out. */
private const val SLOW_SOUNDS_MILLIS = 400L

class NotificationAgentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()
    private val analytics = RecordingAnalytics()

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/notifications/email", EMAIL)
        seerr.serve("POST /api/v1/settings/notifications/email", EMAIL)
        seerr.serve("POST /api/v1/settings/notifications/email/test", "", code = 204)
        seerr.serve("GET /api/v1/settings/notifications/ntfy", NTFY)
        seerr.serve("POST /api/v1/settings/notifications/ntfy", NTFY)
        seerr.serve("GET /api/v1/settings/notifications/pushover", PUSHOVER)
        seerr.serve("GET /api/v1/settings/notifications/pushover/sounds", SOUNDS)
        seerr.serve("GET /api/v1/settings/notifications/telegram", TELEGRAM)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(
        agent: ServerAgent,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): NotificationAgentViewModel {
        val vm =
            NotificationAgentViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, agent, analytics, savedState = savedState)
        vm.soundsDebounceMillis = 10
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun NotificationAgentViewModel.awaitReady(
        where: (ExtrasEditorUiState.Ready<AgentForm, AgentExtras>) -> Boolean = { true },
    ): ExtrasEditorUiState.Ready<AgentForm, AgentExtras> =
        uiState.first {
            it is ExtrasEditorUiState.Ready && !it.saving && where(it)
        } as ExtrasEditorUiState.Ready<AgentForm, AgentExtras>

    /** #1026: an agent's draft outlives the process; a webhook address carries its token, so it comes back from the record. */
    @Test
    fun `an agent's draft survives the process being killed, without its credentials`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/notifications/discord",
                """{"enabled":false,"types":0,"options":{"webhookUrl":"https://discord.example/old","botUsername":""}}""",
            )
            val savedState = SavedStateHandle()
            val vm = viewModel(ServerAgent.Discord, savedState)
            vm.awaitReady()
            vm.setOption(AgentOption.DiscordBotUsername, "Seerr bot")
            vm.setOption(AgentOption.DiscordWebhookUrl, "https://discord.example/t0ken")
            vm.awaitReady { it.draft.option(AgentOption.DiscordBotUsername) == "Seerr bot" }
            assertTrue(savedState.keys().none { savedState.get<Any?>(it).toString().contains("t0ken") })

            val back = viewModel(ServerAgent.Discord, savedState.afterProcessDeath()).awaitReady()

            assertEquals("Seerr bot", back.draft.option(AgentOption.DiscordBotUsername))
            assertEquals("https://discord.example/old", back.draft.option(AgentOption.DiscordWebhookUrl))
        }

    @Test
    fun `only the options holding a username opt out of autocorrect`() {
        assertEquals(
            setOf(AgentOption.EmailAuthUser, AgentOption.NtfyUsername),
            AgentOption.entries.filterNot { it.autoCorrect }.toSet(),
        )
    }

    @Test
    fun `an agent's options are read as typed, and written back typed with the unknown ones kept`() =
        runTest {
            val vm = viewModel(ServerAgent.Email)
            val draft = vm.awaitReady().draft
            assertTrue(draft.enabled)
            assertEquals("465", draft.option(AgentOption.EmailSmtpPort))
            assertTrue(draft.switched(AgentOption.EmailSecure))
            assertEquals("", draft.option(AgentOption.EmailAuthUser))
            assertTrue(draft.types and NotificationType.MediaApproved.bit != 0)

            vm.setOption(AgentOption.EmailSmtpPort, "587")
            vm.setOption(AgentOption.EmailSecure, "false")
            vm.setOption(AgentOption.EmailAuthUser, " mailer ")
            vm.toggleType(NotificationType.MediaAvailable.bit)
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/notifications/email")).jsonObject
            assertEquals("true", sent.getValue("enabled").jsonPrimitive.content)
            assertEquals(
                6 xor NotificationType.MediaAvailable.bit,
                sent
                    .getValue("types")
                    .jsonPrimitive.content
                    .toInt(),
            )
            val options = sent.getValue("options").jsonObject
            assertEquals("587", options.getValue("smtpPort").jsonPrimitive.content)
            assertFalse(options.getValue("smtpPort").jsonPrimitive.isString)
            assertEquals("false", options.getValue("secure").jsonPrimitive.content)
            assertFalse(options.getValue("secure").jsonPrimitive.isString)
            assertEquals("mailer", options.getValue("authUser").jsonPrimitive.content)
            assertEquals("legacy", options.getValue("pgpMode").jsonPrimitive.content)
            assertEquals(
                listOf("notification_agent_changed" to mapOf("agent" to "email", "action" to "updated")),
                analytics.events,
            )
        }

    @Test
    fun `an agent that is on cannot be saved without what it sends through, and one that is off can`() =
        runTest {
            val vm = viewModel(ServerAgent.Email)
            vm.awaitReady()
            vm.setOption(AgentOption.EmailSmtpHost, "")
            assertFalse(vm.awaitReady().draft.valid)
            vm.save()
            assertEquals(0, seerr.count("POST", "/api/v1/settings/notifications/email"))

            vm.setEnabled(false)
            assertTrue(vm.awaitReady().draft.valid)
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/notifications/email")).jsonObject
            assertEquals("false", sent.getValue("enabled").jsonPrimitive.content)
            assertEquals("6", sent.getValue("types").jsonPrimitive.content)
        }

    @Test
    fun `a required number option that does not parse blocks save the same as a blank one`() =
        runTest {
            val vm = viewModel(ServerAgent.Email)
            vm.awaitReady()
            vm.setOption(AgentOption.EmailSmtpPort, "abcd")
            assertFalse(vm.awaitReady().draft.valid)
            vm.save()
            assertEquals(0, seerr.count("POST", "/api/v1/settings/notifications/email"))
        }

    @Test
    fun `an optional number option left blank is sent as null, not an empty string`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/notifications/ntfy",
                """{"enabled":false,"types":0,"options":{"url":"","topic":"","priority":5}}""",
            )
            seerr.serve("POST /api/v1/settings/notifications/ntfy", """{"enabled":false,"types":0,"options":{}}""")
            val vm = viewModel(ServerAgent.Ntfy)
            assertEquals("5", vm.awaitReady().draft.option(AgentOption.NtfyPriority))

            vm.setOption(AgentOption.NtfyPriority, "")
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/notifications/ntfy")).jsonObject
            assertEquals(JsonNull, sent.getValue("options").jsonObject.getValue("priority"))
        }

    @Test
    fun `each ntfy priority level is saved as its number, and an unknown stored one is left as found`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/notifications/ntfy",
                """{"enabled":false,"types":0,"options":{"url":"","topic":"","priority":7}}""",
            )
            seerr.serve("POST /api/v1/settings/notifications/ntfy", """{"enabled":false,"types":0,"options":{}}""")
            val vm = viewModel(ServerAgent.Ntfy)
            assertEquals("7", vm.awaitReady().draft.option(AgentOption.NtfyPriority))
            assertEquals(listOf("1", "2", "3", "4", "5"), NtfyPriorityLevel.entries.map { it.value })

            vm.setOption(AgentOption.NtfyPriority, NtfyPriorityLevel.High.value)
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/notifications/ntfy")).jsonObject
            assertEquals(
                "4",
                sent
                    .getValue("options")
                    .jsonObject
                    .getValue("priority")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `a pushover sound can be put back to the device default`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/notifications/pushover",
                """{"enabled":false,"types":0,"options":{"accessToken":"","userToken":"","sound":"bike"}}""",
            )
            seerr.serve("POST /api/v1/settings/notifications/pushover", """{"enabled":false,"types":0,"options":{}}""")
            val vm = viewModel(ServerAgent.Pushover)
            assertEquals("bike", vm.awaitReady().draft.option(AgentOption.PushoverSound))

            vm.setOption(AgentOption.PushoverSound, "")
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/notifications/pushover")).jsonObject
            assertEquals(
                "",
                sent
                    .getValue("options")
                    .jsonObject
                    .getValue("sound")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `turning on one ntfy auth method turns the other off, and a server holding both stays fixable`() =
        runTest {
            val vm = viewModel(ServerAgent.Ntfy)
            // The server is already holding both, which this app could write before this change.
            val loaded = vm.awaitReady().draft
            assertTrue(loaded.switched(AgentOption.NtfyAuthByPassword))
            assertTrue(loaded.switched(AgentOption.NtfyAuthByToken))

            vm.setOption(AgentOption.NtfyAuthByToken, true.toString())

            val draft = vm.awaitReady().draft
            assertTrue(draft.switched(AgentOption.NtfyAuthByToken))
            assertFalse(draft.switched(AgentOption.NtfyAuthByPassword))

            vm.setOption(AgentOption.NtfyAuthByPassword, true.toString())
            val swapped = vm.awaitReady().draft
            assertTrue(swapped.switched(AgentOption.NtfyAuthByPassword))
            assertFalse(swapped.switched(AgentOption.NtfyAuthByToken))
        }

    @Test
    fun `the email TLS trio reads as one choice, and a pick writes exactly one of the three`() =
        runTest {
            // The server is holding a pair the web client cannot produce, which this app could write before this change.
            seerr.serve(
                "GET /api/v1/settings/notifications/email",
                """{"enabled":true,"types":6,"options":{"emailFrom":"seerr@example.com","smtpHost":"smtp.example.com",
                    "smtpPort":587,"secure":false,"ignoreTls":true,"requireTls":true}}""",
            )
            val vm = viewModel(ServerAgent.Email)
            vm.awaitReady()
            assertEquals(EmailEncryption.StartTlsAlways, EmailEncryption.of(vm.awaitReady().draft))

            vm.setEncryption(EmailEncryption.None)
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val options =
                Json
                    .parseToJsonElement(seerr.body("POST", "/api/v1/settings/notifications/email"))
                    .jsonObject
                    .getValue("options")
                    .jsonObject
            assertEquals("true", options.getValue("ignoreTls").jsonPrimitive.content)
            assertEquals("false", options.getValue("requireTls").jsonPrimitive.content)
            assertEquals("false", options.getValue("secure").jsonPrimitive.content)
        }

    @Test
    fun `a test sends the draft as typed and reports either way`() =
        runTest {
            val vm = viewModel(ServerAgent.Email)
            vm.awaitReady()
            vm.setOption(AgentOption.EmailSenderName, "Test sender")
            val notice = awaitEvent(vm.events)
            vm.test()
            assertEquals(EditorEvent.Notice(R.string.server_settings_agent_tested), notice.await())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/notifications/email/test")).jsonObject
            assertEquals(
                "Test sender",
                sent
                    .getValue("options")
                    .jsonObject
                    .getValue("senderName")
                    .jsonPrimitive.content,
            )
            assertEquals(0, seerr.count("POST", "/api/v1/settings/notifications/email"))
            assertEquals(
                listOf("notification_agent_changed" to mapOf("agent" to "email", "action" to "tested")),
                analytics.events,
            )

            seerr.serve("POST /api/v1/settings/notifications/email/test", """{"message":"boom"}""", code = 500)
            val failed = awaitEvent(vm.events)
            vm.test()
            assertTrue(failed.await() is EditorEvent.Failed)
            // `test()` reports the outcome before it clears `testing`, so await the flag rather
            // than sampling it the moment the event lands.
            assertFalse(vm.awaitReady { !it.extras.testing }.extras.testing)
            // The failed test above must not have added a second event.
            assertEquals(1, analytics.events.size)
        }

    @Test
    fun `pushover asks for the sounds of the application token once it is typed`() =
        runTest {
            val vm = viewModel(ServerAgent.Pushover)
            assertTrue(
                vm
                    .awaitReady()
                    .extras.sounds
                    .isEmpty(),
            )
            vm.setOption(AgentOption.PushoverAccessToken, "app-token")
            val sounds = vm.awaitReady { it.extras.sounds.isNotEmpty() }.extras.sounds
            assertEquals(listOf("pushover", "bike"), sounds.map { it.name })
            assertEquals(
                "app-token",
                seerr.received
                    .last { it.url.encodedPath.endsWith("/sounds") }
                    .url
                    .queryParameter("token"),
            )
        }

    /** A keystroke cancels the sounds fetch in flight; the cancellation must not empty the picker on its way out (#1025). */
    @Test
    fun `a token edited while its sounds load keeps the picker full until the next answer`() =
        runTest {
            val vm = viewModel(ServerAgent.Pushover)
            vm.awaitReady()
            vm.setOption(AgentOption.PushoverAccessToken, "app-token")
            vm.awaitReady { it.extras.sounds.isNotEmpty() }
            val seen = mutableListOf<Int>()
            backgroundScope.launch {
                vm.uiState.collect { state -> (state as? ExtrasEditorUiState.Ready)?.let { seen += it.extras.sounds.size } }
            }
            seerr.serveFrom("GET /api/v1/settings/notifications/pushover/sounds", delayMillis = SLOW_SOUNDS_MILLIS) { SOUNDS }

            vm.setOption(AgentOption.PushoverAccessToken, "app-token-2")
            seerr.awaitCount("GET", "/api/v1/settings/notifications/pushover/sounds", moreThan = 1)
            vm.setOption(AgentOption.PushoverAccessToken, "app-token-3")
            seerr.awaitCount("GET", "/api/v1/settings/notifications/pushover/sounds", moreThan = 2)
            withContext(Dispatchers.IO) { delay(SLOW_SOUNDS_MILLIS * 2) }
            vm.awaitReady { it.draft.option(AgentOption.PushoverAccessToken) == "app-token-3" }

            assertFalse("the picker emptied mid-fetch: $seen", 0 in seen)
        }

    @Test
    fun `the webhook template is shown decoded and stored as the web client stores it`() =
        runTest {
            val template = "{\n  \"subject\": \"{{subject}}\"\n}"
            val stored =
                Base64.getEncoder().encodeToString(
                    Json.encodeToString(template).toByteArray(),
                )
            seerr.serve(
                "GET /api/v1/settings/notifications/webhook",
                """{"enabled":false,"types":0,"options":{"webhookUrl":"","jsonPayload":"$stored"}}""",
            )
            seerr.serve("POST /api/v1/settings/notifications/webhook", """{"enabled":false,"types":0,"options":{}}""")
            val vm = viewModel(ServerAgent.Webhook)
            assertEquals(template, vm.awaitReady().draft.option(AgentOption.WebhookJsonPayload))

            vm.setOption(AgentOption.WebhookJsonPayload, "{\"event\": \"{{event}}\"}")
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/notifications/webhook")).jsonObject
            val payload =
                sent
                    .getValue("options")
                    .jsonObject
                    .getValue("jsonPayload")
                    .jsonPrimitive.content
            assertEquals("\"{\\\"event\\\": \\\"{{event}}\\\"}\"", String(Base64.getDecoder().decode(payload)))
        }

    @Test
    fun `Overseerr's Telegram agent has no topic, so the page does not offer one`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            val vm = viewModel(ServerAgent.Telegram)
            assertTrue(
                AgentOption.TelegramMessageThreadId in
                    vm.awaitReady { AgentOption.TelegramMessageThreadId in it.extras.withheld }.extras.withheld,
            )
        }

    @Test
    fun `Jellyseerr before 2_2 has no Telegram topic on the agent either`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "2.1.0", settings = "{}")
            val vm = viewModel(ServerAgent.Telegram)
            assertTrue(
                AgentOption.TelegramMessageThreadId in
                    vm.awaitReady { AgentOption.TelegramMessageThreadId in it.extras.withheld }.extras.withheld,
            )
        }

    @Test
    fun `Seerr's Telegram agent keeps its topic`() =
        runTest {
            val vm = viewModel(ServerAgent.Telegram)
            assertTrue(
                vm
                    .awaitReady()
                    .extras.withheld
                    .isEmpty(),
            )
        }
}
