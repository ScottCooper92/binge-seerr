package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationType
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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

private const val SOUNDS = """[{"name":"pushover","description":"Pushover (default)"},{"name":"bike","description":"Bike"}]"""

class NotificationAgentViewModelTest {
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
        seerr.serve("GET /api/v1/settings/notifications/email", EMAIL)
        seerr.serve("POST /api/v1/settings/notifications/email", EMAIL)
        seerr.serve("POST /api/v1/settings/notifications/email/test", "", code = 204)
        seerr.serve("GET /api/v1/settings/notifications/ntfy", NTFY)
        seerr.serve("POST /api/v1/settings/notifications/ntfy", NTFY)
        seerr.serve("GET /api/v1/settings/notifications/pushover", PUSHOVER)
        seerr.serve("GET /api/v1/settings/notifications/pushover/sounds", SOUNDS)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(agent: ServerAgent): NotificationAgentViewModel {
        val vm = NotificationAgentViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, agent)
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

            seerr.serve("POST /api/v1/settings/notifications/email/test", """{"message":"boom"}""", code = 500)
            val failed = awaitEvent(vm.events)
            vm.test()
            assertTrue(failed.await() is EditorEvent.Failed)
            // `test()` reports the outcome before it clears `testing`, so await the flag rather
            // than sampling it the moment the event lands.
            assertFalse(vm.awaitReady { !it.extras.testing }.extras.testing)
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
}
