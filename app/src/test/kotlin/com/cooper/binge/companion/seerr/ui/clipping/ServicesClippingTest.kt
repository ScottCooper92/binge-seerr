package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.server.DvrChoices
import com.cooper.binge.companion.seerr.ui.settings.server.DvrExtras
import com.cooper.binge.companion.seerr.ui.settings.server.DvrForm
import com.cooper.binge.companion.seerr.ui.settings.server.DvrInstanceScreen
import com.cooper.binge.companion.seerr.ui.settings.server.DvrSummary
import com.cooper.binge.companion.seerr.ui.settings.server.GenreChoices
import com.cooper.binge.companion.seerr.ui.settings.server.KeywordSearch
import com.cooper.binge.companion.seerr.ui.settings.server.OverrideRuleActions
import com.cooper.binge.companion.seerr.ui.settings.server.OverrideRuleExtras
import com.cooper.binge.companion.seerr.ui.settings.server.OverrideRuleForm
import com.cooper.binge.companion.seerr.ui.settings.server.OverrideRuleScreen
import com.cooper.binge.companion.seerr.ui.settings.server.OverrideRuleSummary
import com.cooper.binge.companion.seerr.ui.settings.server.ServicesActions
import com.cooper.binge.companion.seerr.ui.settings.server.ServicesScreen
import com.cooper.binge.companion.seerr.ui.settings.server.ServicesUiState
import com.cooper.binge.companion.seerr.ui.users.settings.EditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_INSTANCE = "Radarr 4K Remux Library on the Upstairs Server"
private const val LONG_PROFILE = "Ultra-HD Remux with Lossless Audio and Subtitles"
private const val LONG_FOLDER = "/mnt/storage/media/movies/4k-remux-collection"

/**
 * The Radarr and Sonarr pages with long names, addresses, profiles and folders, at font scales 1.3 and 2.0, through the
 * design system's `assertNoTextClipped`: the instance list, an instance's form and a rule's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServicesClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the services page at 1_3`() = rule.assertClipFree(FONT_13) { ServicesPage() }

    @Test fun `the services page at 2_0`() = rule.assertClipFree(FONT_20) { ServicesPage() }

    @Test fun `a radarr instance at 1_3`() = rule.assertClipFree(FONT_13) { InstancePage(ServiceType.Radarr) }

    @Test fun `a radarr instance at 2_0`() = rule.assertClipFree(FONT_20) { InstancePage(ServiceType.Radarr) }

    @Test fun `a sonarr instance at 1_3`() = rule.assertClipFree(FONT_13) { InstancePage(ServiceType.Sonarr) }

    @Test fun `a sonarr instance at 2_0`() = rule.assertClipFree(FONT_20) { InstancePage(ServiceType.Sonarr) }

    @Test fun `an override rule at 1_3`() = rule.assertClipFree(FONT_13) { RulePage() }

    @Test fun `an override rule at 2_0`() = rule.assertClipFree(FONT_20) { RulePage() }

    private fun <T> actions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

    private fun choices() =
        DvrChoices(
            profiles = listOf(Choice(1, "Any"), Choice(4, LONG_PROFILE)),
            rootFolders = listOf("/movies", LONG_FOLDER),
            tags = listOf(Choice(1, "kids and family friendly"), Choice(2, "4k remux")),
            languageProfiles = listOf(Choice(1, "English and Original Language with Subtitles")),
        )

    private fun instances() =
        listOf(
            DvrSummary(
                1,
                ServiceType.Radarr,
                LONG_INSTANCE,
                "a-rather-long-hostname.upstairs.home.example.org:7878",
                is4k = true,
                isDefault = true,
            ),
            DvrSummary(
                2,
                ServiceType.Sonarr,
                "Sonarr Anime and Documentaries",
                "sonarr.upstairs.home.example.org:8989",
                is4k = false,
                isDefault = false,
            ),
        )

    @Composable
    private fun ServicesPage() =
        ServicesScreen(
            state =
                ServicesUiState.Ready(
                    instances = instances(),
                    rules =
                        listOf(
                            OverrideRuleSummary(id = 1, instanceName = LONG_INSTANCE, conditions = 12),
                            OverrideRuleSummary(id = 2, instanceName = "Sonarr Anime and Documentaries", conditions = 3),
                        ),
                ),
            actions = ServicesActions(onBack = {}, onRetry = {}, onOpenInstance = { _, _ -> }, onOpenRule = {}),
        )

    @Composable
    private fun InstancePage(type: ServiceType) {
        val form =
            DvrForm.blank(type).copy(
                id = 1,
                name = LONG_INSTANCE,
                host = "a-rather-long-hostname.upstairs.home.example.org",
                apiKey = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
                isDefault = true,
                is4k = true,
                profileId = 4,
                profileName = LONG_PROFILE,
                rootFolder = LONG_FOLDER,
                tagIds = setOf(1, 2),
                animeProfileId = 4,
                animeRootFolder = LONG_FOLDER,
                languageProfileId = 1,
                animeLanguageProfileId = 1,
            )
        DvrInstanceScreen(
            state =
                ExtrasEditorUiState.Ready(
                    draft = form,
                    saved = form,
                    extras =
                        DvrExtras(
                            choices =
                                choices().let {
                                    if (type ==
                                        ServiceType.Radarr
                                    ) {
                                        it.copy(languageProfiles = null)
                                    } else {
                                        it
                                    }
                                },
                        ),
                ),
            events = emptyFlow(),
            actions = actions(),
            onTest = {},
            onDelete = {},
        )
    }

    @Composable
    private fun RulePage() {
        val form =
            OverrideRuleForm(
                id = 1,
                serviceType = ServiceType.Radarr,
                serviceId = 1,
                userIds = setOf(1, 2),
                genres = "16,28,12",
                languages = "ja",
                keywords = "9951,210024",
                profileId = 4,
                rootFolder = LONG_FOLDER,
                tagIds = setOf(1, 2),
            )
        OverrideRuleScreen(
            state =
                ExtrasEditorUiState.Ready(
                    draft = form,
                    saved = form,
                    extras =
                        OverrideRuleExtras(
                            instances = instances(),
                            users = listOf(Choice(1, LONG_NAME), Choice(2, "Alex")),
                            choices = choices().copy(languageProfiles = null),
                            genres =
                                GenreChoices.Ready(
                                    listOf(
                                        Choice(16, "Animation and Cartoons"),
                                        Choice(28, "Action"),
                                        Choice(12, "Adventure and Exploration"),
                                    ),
                                ),
                            keywords =
                                KeywordSearch(
                                    names =
                                        mapOf(
                                            9951 to "kaiju monsters attacking coastal cities in the near future",
                                            210024 to "anime adaptation of a long-running manga",
                                        ),
                                ),
                        ),
                ),
            events = emptyFlow(),
            actions = actions(),
            ruleActions =
                OverrideRuleActions(
                    onSelectInstance = {},
                    onToggleUser = {},
                    onToggleTag = {},
                    onToggleGenre = {},
                    onLoadLanguages = {},
                    onSelectLanguages = {},
                    onToggleKeyword = {},
                    onSearchKeywords = {},
                    onLoadKeywordNames = {},
                    onDelete = {},
                ),
            saveAsMade = true,
        )
    }
}
