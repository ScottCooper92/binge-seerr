package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.ui.settings.server.AgentActions
import com.cooper.binge.companion.seerr.ui.settings.server.AgentExtras
import com.cooper.binge.companion.seerr.ui.settings.server.AgentForm
import com.cooper.binge.companion.seerr.ui.settings.server.AgentOption
import com.cooper.binge.companion.seerr.ui.settings.server.AgentSummary
import com.cooper.binge.companion.seerr.ui.settings.server.AgentsActions
import com.cooper.binge.companion.seerr.ui.settings.server.AgentsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSlider
import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSliderScreen
import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSlidersScreen
import com.cooper.binge.companion.seerr.ui.settings.server.GenreChoices
import com.cooper.binge.companion.seerr.ui.settings.server.KeywordSearch
import com.cooper.binge.companion.seerr.ui.settings.server.NotificationAgentScreen
import com.cooper.binge.companion.seerr.ui.settings.server.NotificationAgentsScreen
import com.cooper.binge.companion.seerr.ui.settings.server.ProviderChoices
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.SliderEditorActions
import com.cooper.binge.companion.seerr.ui.settings.server.SliderExtras
import com.cooper.binge.companion.seerr.ui.settings.server.SliderForm
import com.cooper.binge.companion.seerr.ui.settings.server.SliderNames
import com.cooper.binge.companion.seerr.ui.settings.server.SliderType
import com.cooper.binge.companion.seerr.ui.settings.server.SlidersActions
import com.cooper.binge.companion.seerr.ui.users.settings.EditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.NotificationType
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_URL = "https://hooks.a-rather-long-hostname.living-room.home.example.org/binge/with/a/long/path"
private const val LONG_SLIDER = "Kaiju, Giant Robots and Other Large Things Fighting in Cities"

private val ALL_TYPES = NotificationType.entries.fold(0) { mask, type -> mask or type.bit }

/**
 * The notification agents and the discover sliders with long names, URLs and tokens, at font scales 1.3 and 2.0, through
 * the design system's `assertNoTextClipped`: each list, an agent's form and a slider's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentsAndSlidersClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the agents list at 1_3`() = rule.assertClipFree(FONT_13) { AgentsList() }

    @Test fun `the agents list at 2_0`() = rule.assertClipFree(FONT_20) { AgentsList() }

    @Test fun `an email agent at 1_3`() = rule.assertClipFree(FONT_13) { Agent(emailForm(), ServerAgent.Email) }

    @Test fun `an email agent at 2_0`() = rule.assertClipFree(FONT_20) { Agent(emailForm(), ServerAgent.Email) }

    @Test fun `a webhook agent at 1_3`() = rule.assertClipFree(FONT_13) { Agent(webhookForm(), ServerAgent.Webhook) }

    @Test fun `a webhook agent at 2_0`() = rule.assertClipFree(FONT_20) { Agent(webhookForm(), ServerAgent.Webhook) }

    @Test fun `an ntfy agent at 2_0`() = rule.assertClipFree(FONT_20) { Agent(ntfyForm(), ServerAgent.Ntfy) }

    @Test fun `the sliders list at 1_3`() = rule.assertClipFree(FONT_13) { SlidersList() }

    @Test fun `the sliders list at 2_0`() = rule.assertClipFree(FONT_20) { SlidersList() }

    @Test fun `a keyword slider at 1_3`() = rule.assertClipFree(FONT_13) { Slider(keywordSlider(), keywordExtras()) }

    @Test fun `a keyword slider at 2_0`() = rule.assertClipFree(FONT_20) { Slider(keywordSlider(), keywordExtras()) }

    @Test fun `a streaming slider at 2_0`() = rule.assertClipFree(FONT_20) { Slider(streamingSlider(), streamingExtras()) }

    private fun <T> actions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

    private fun agentActions() =
        AgentActions(onSetEnabled = {
        }, onSetOption = { _, _ -> }, onSetEncryption = {}, onToggleType = {}, onTest = {})

    private fun emailForm() =
        AgentForm(
            agent = ServerAgent.Email,
            enabled = true,
            types = ALL_TYPES,
            options =
                mapOf(
                    AgentOption.EmailFrom to "alexandria.fitzgerald-montgomery@a-rather-long-domain-name.example.com",
                    AgentOption.EmailSenderName to LONG_NAME,
                    AgentOption.EmailSmtpHost to "smtp.a-rather-long-hostname.living-room.home.example.org",
                    AgentOption.EmailSmtpPort to "587",
                    AgentOption.EmailRequireTls to "true",
                    AgentOption.EmailAuthUser to "alexandria.fitzgerald-montgomery",
                    AgentOption.EmailAuthPass to "hunter2hunter2hunter2",
                ),
        )

    private fun webhookForm() =
        AgentForm(
            agent = ServerAgent.Webhook,
            enabled = true,
            types = ALL_TYPES,
            options =
                mapOf(
                    AgentOption.WebhookUrl to LONG_URL,
                    AgentOption.WebhookJsonPayload to "{\n  \"subject\": \"{{subject}}\",\n  \"message\": \"{{message}}\"\n}",
                ),
        )

    private fun ntfyForm() =
        AgentForm(
            agent = ServerAgent.Ntfy,
            enabled = true,
            types = ALL_TYPES,
            options =
                mapOf(
                    AgentOption.NtfyUrl to LONG_URL,
                    AgentOption.NtfyTopic to "binge-notifications-for-the-whole-living-room",
                    AgentOption.NtfyAuthByPassword to "true",
                    AgentOption.NtfyUsername to "alexandria.fitzgerald-montgomery",
                    AgentOption.NtfyPassword to "correcthorsebatterystaple",
                ),
        )

    @Composable
    private fun AgentsList() =
        NotificationAgentsScreen(
            state = AgentsUiState.Ready(ServerAgent.entries.map { AgentSummary(it, enabled = true) }),
            actions = AgentsActions(onBack = {}, onRetry = {}, onOpenAgent = {}),
        )

    @Composable
    private fun Agent(
        form: AgentForm,
        agent: ServerAgent,
    ) = NotificationAgentScreen(
        state = ExtrasEditorUiState.Ready(draft = form, saved = form, extras = AgentExtras()),
        events = emptyFlow(),
        actions = actions(),
        agentActions = agentActions(),
        agent = agent,
    )

    private fun keywordExtras() =
        SliderExtras(
            keywords =
                KeywordSearch(
                    names =
                        mapOf(
                            210024 to "kaiju monsters attacking coastal cities in the near future",
                            4344 to "giant robots piloted from inside",
                        ),
                ),
        )

    private fun keywordSlider() = SliderForm(id = 4, type = SliderType.MovieKeyword, title = LONG_SLIDER, data = "210024,4344")

    private fun streamingSlider() = SliderForm(id = 10, type = SliderType.MovieStreamingServices, title = LONG_SLIDER, data = "GB,8|337|9")

    private fun streamingExtras() =
        SliderExtras(
            providers =
                ProviderChoices.Ready(
                    listOf(
                        Choice(8, "Netflix Standard with Ads"),
                        Choice(337, "Disney Plus Premium Bundle"),
                        Choice(9, "Amazon Prime Video"),
                    ),
                ),
        )

    @Composable
    private fun Slider(
        form: SliderForm,
        extras: SliderExtras,
    ) = DiscoverSliderScreen(
        state =
            ExtrasEditorUiState.Ready(
                draft = form,
                saved = form,
                extras = extras.copy(genres = GenreChoices.Ready(listOf(Choice(28, "Action")))),
            ),
        events = emptyFlow(),
        actions = actions(),
        sliderActions =
            SliderEditorActions(
                onSelectType = {},
                onToggleKeyword = {},
                onSelectGenre = {},
                onSearchKeywords = {},
                onLoadKeywordNames = {},
                onSearchStudios = {},
                onSelectStudio = {},
                onNameNetwork = {},
                onLoadRegions = {},
                onSelectRegion = {},
                onToggleProvider = {},
                onDelete = {},
            ),
    )

    @Composable
    private fun SlidersList() {
        val sliders =
            listOf(
                DiscoverSlider(id = 1, typeCode = 1, title = null, builtIn = true, enabled = true, data = null),
                DiscoverSlider(id = 4, typeCode = 13, title = LONG_SLIDER, builtIn = false, enabled = true, data = "210024,4344"),
                DiscoverSlider(
                    id = 5,
                    typeCode = 19,
                    title = LONG_SLIDER,
                    builtIn = false,
                    enabled = false,
                    data = "dune and other long search phrases",
                ),
                DiscoverSlider(id = 10, typeCode = 20, title = LONG_SLIDER, builtIn = false, enabled = true, data = "GB,8|337"),
            )
        val names =
            SliderNames(
                keywords =
                    mapOf(
                        210024 to "kaiju monsters attacking coastal cities in the near future",
                        4344 to "giant robots piloted from inside",
                    ),
                providers = mapOf("movies:GB" to mapOf(8 to "Netflix Standard with Ads", 337 to "Disney Plus Premium Bundle")),
            )
        DiscoverSlidersScreen(
            state = ExtrasEditorUiState.Ready(draft = sliders, saved = sliders, extras = names),
            events = emptyFlow(),
            actions = actions(),
            sliderActions = SlidersActions(onMove = { _, _ -> }, onToggle = {}, onOpenSlider = {}, onReset = {}),
        )
    }
}
