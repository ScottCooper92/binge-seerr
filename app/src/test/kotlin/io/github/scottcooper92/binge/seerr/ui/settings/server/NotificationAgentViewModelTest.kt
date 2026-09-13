package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationType
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

private const val PUSHOVER = """{"enabled":false,"types":0,"options":{"accessToken":"","userToken":"","sound":""}}"""

private const val SOUNDS = """[{"name":"pushover","description":"Pushover (default)"},{"name":"bike","description":"Bike"}]"""

class NotificationAgentViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/notifications/email", EMAIL)
        seerr.serve("POST /api/v1/settings/notifications/email", EMAIL)
        seerr.serve("POST /api/v1/settings/notifications/email/test", "", code = 204)
        seerr.serve("GET /api/v1/settings/notifications/pushover", PUSHOVER)
        seerr.serve("GET /api/v1/settings/notifications/pushover/sounds", SOUNDS)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(agent: ServerAgent): NotificationAgentViewModel {
        val vm = NotificationAgentViewModel(seerr.connection(this), agent)
        vm.soundsDebounceMillis = 10
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun NotificationAgentViewModel.awaitReady(): EditorUiState.Ready<AgentForm> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<AgentForm>

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
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

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
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())
        }

    @Test
    fun `a test sends the draft as typed and reports either way`() =
        runTest {
            val vm = viewModel(ServerAgent.Email)
            vm.awaitReady()
            vm.setOption(AgentOption.EmailSenderName, "Test sender")
            vm.test()
            assertEquals(EditorEvent.Notice(R.string.server_settings_agent_tested), vm.events.first())
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
            vm.test()
            assertTrue(vm.events.first() is EditorEvent.Failed)
            assertFalse(vm.extras.first().testing)
        }

    @Test
    fun `pushover asks for the sounds of the application token once it is typed`() =
        runTest {
            val vm = viewModel(ServerAgent.Pushover)
            vm.awaitReady()
            assertTrue(
                vm.extras
                    .first()
                    .sounds
                    .isEmpty(),
            )
            vm.setOption(AgentOption.PushoverAccessToken, "app-token")
            val sounds = vm.extras.first { it.sounds.isNotEmpty() }.sounds
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
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())
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
